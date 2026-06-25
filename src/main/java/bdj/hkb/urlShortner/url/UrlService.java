package bdj.hkb.urlShortner.url;

import bdj.hkb.urlShortner.security.dto.JwtPrincipal;
import bdj.hkb.urlShortner.url.dto.UrlCreateRequest;
import bdj.hkb.urlShortner.url.dto.UrlResponse;
import bdj.hkb.urlShortner.util.Base62Encoder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UrlService {

    private final UrlRepository urlRepository;
    private final StringRedisTemplate redisTemplate;

    private static final String REDIS_URL_PREFIX = "url:route:";
    private static final Duration CACHE_TTL = Duration.ofDays(7); // Keep hot links in RAM for 7 days

    @Transactional
    public UrlResponse createShortLink(UrlCreateRequest request, Authentication auth) {

        String urlHash = generateHash(request.longUrl());
        UUID userId = null;

        // Soft-Auth: If the user provides a token, we grab the ID.
        // If not, we proceed with userId = null (anonymous).
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof JwtPrincipal principal) {
            userId = principal.userId();
        }
        // 1. Check for existing link
        Optional<Url> existingUrl = urlRepository.findByLongUrlHash(urlHash);

        if (existingUrl.isPresent()) {
            Url url = existingUrl.get();

            // If the existing record is anonymous (userId is null)
            // AND the current request is from an authenticated user,
            // "adopt" this URL by setting the userId!
            if (url.getUserId() == null && userId != null) {
                url.setUserId(userId);
                urlRepository.save(url);
            }

            return new UrlResponse(url.getShortCode(), url.getLongUrl(), url.getTitle(), url.getCreatedAt());
        }
        // Step 1: Create the initial entity
        Url newUrl = Url.builder()
                .longUrl(request.longUrl())
                .longUrlHash(urlHash)
                .userId(userId)
                .title(request.title())
                .shortCode("PENDING") // Placeholder to satisfy non-null constraint
                .build();

        // Step 2: Save to get the auto-generated database ID
        newUrl = urlRepository.save(newUrl);

        // Step 3: Convert the ID to a Base62 short code
        String shortCode = Base62Encoder.encode(newUrl.getId());
        newUrl.setShortCode(shortCode);

        // Step 4: Update the database with the real short code
        urlRepository.save(newUrl);

        // Step 5: Warm up the Redis cache immediately so the first click is lightning fast
        redisTemplate.opsForValue().set(
                REDIS_URL_PREFIX + shortCode,
                request.longUrl(),
                CACHE_TTL
        );

        return new UrlResponse(
                newUrl.getShortCode(),
                newUrl.getLongUrl(),
                newUrl.getTitle(),
                newUrl.getCreatedAt()
        );
    }

    /**
     * This method is called when a user actually clicks a short link.
     * It uses the "Cache-Aside" pattern.
     */
    public String getLongUrl(String shortCode) {
        String cacheKey = REDIS_URL_PREFIX + shortCode;

        // 1. Try RAM first (Sub-millisecond read)
        String cachedUrl = redisTemplate.opsForValue().get(cacheKey);
        if (cachedUrl != null) {
            return cachedUrl;
        }

        // 2. Cache Miss: Hit PostgreSQL (Slower)
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new RuntimeException("URL not found")); // We'll upgrade this to a custom exception later

        if (!url.getIsActive()) {
            throw new RuntimeException("This link has been disabled");
        }

        // 3. Hydrate the cache for the next person
        redisTemplate.opsForValue().set(cacheKey, url.getLongUrl(), CACHE_TTL);

        return url.getLongUrl();
    }

    private String generateHash(String longUrl) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(longUrl.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Error hashing URL", e);
        }
    }
}
