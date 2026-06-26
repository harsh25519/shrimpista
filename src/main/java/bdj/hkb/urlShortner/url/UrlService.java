package bdj.hkb.urlShortner.url;

import bdj.hkb.urlShortner.exceptionHandler.UrlExpiredException;
import bdj.hkb.urlShortner.exceptionHandler.UrlNotFoundException;
import bdj.hkb.urlShortner.security.dto.JwtPrincipal;
import bdj.hkb.urlShortner.url.dto.UrlCreateRequest;
import bdj.hkb.urlShortner.url.dto.UrlDashboardResponse;
import bdj.hkb.urlShortner.url.dto.UrlResponse;
import bdj.hkb.urlShortner.util.Base62Encoder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
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
    public UrlResponse createShortLink(UrlCreateRequest request, UUID userId) {

        String urlHash = generateHash(request.longUrl());

        // 1. Check for existing link
        Optional<Url> existingUrl = userId != null
                ? urlRepository.findByLongUrlHashAndUserId(urlHash, userId)
                : urlRepository.findByLongUrlHashAndUserIdIsNull(urlHash);

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

        if (url.getDeletedAt() != null) {
            throw new UrlNotFoundException("This link is no longer available");
        }
        if (url.getExpiresAt() != null && url.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new UrlExpiredException("This link has expired");
        }

        if (!url.getIsActive()) {
            throw new RuntimeException("This link has been disabled");
        }

        // 3. Hydrate the cache for the next person
        redisTemplate.opsForValue().set(cacheKey, url.getLongUrl(), CACHE_TTL);

        return url.getLongUrl();
    }

    public Page<UrlDashboardResponse> getUserUrls(JwtPrincipal principal, int page, int size) {

        if(principal == null){
            throw new RuntimeException("Unauthenticated user");
        }

        UUID userId = principal.userId();
        PageRequest pageRequest = PageRequest.of(page, size);

        Page<Url> userUrls = urlRepository.findAllByUserIdOrderByCreatedAtDesc(userId, pageRequest);

        return userUrls.map(url -> new UrlDashboardResponse(
                url.getShortCode(),
                url.getTitle(),
                url.getCreatedAt(),
                url.getIsActive()
        ));
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
