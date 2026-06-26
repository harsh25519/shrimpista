package bdj.hkb.urlShortner.url;

import bdj.hkb.urlShortner.exceptionHandler.UrlDisabledException;
import bdj.hkb.urlShortner.exceptionHandler.UrlExpiredException;
import bdj.hkb.urlShortner.exceptionHandler.UrlNotFoundException;
import bdj.hkb.urlShortner.security.dto.JwtPrincipal;
import bdj.hkb.urlShortner.stats.UrlStats;
import bdj.hkb.urlShortner.stats.UrlStatsRepository;
import bdj.hkb.urlShortner.url.dto.UrlCreateRequest;
import bdj.hkb.urlShortner.url.dto.UrlDashboardResponse;
import bdj.hkb.urlShortner.url.dto.UrlResponse;
import bdj.hkb.urlShortner.url.dto.UrlUpdateRequest;
import bdj.hkb.urlShortner.util.Base62Encoder;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UrlService {

    private final UrlRepository urlRepository;
    private final UrlStatsRepository urlStatsRepository;
    private final StringRedisTemplate redisTemplate;

    private static final String REDIS_URL_PREFIX = "url:route:";
    private static final String REDIS_ID_SUFFIX = ":id";

    @Value("${url.cache-ttl-days:7}")
    private long cacheTtlDays; // Keep hot links in RAM for 7 days

    // -------------------------------------------------------------------
    // CREATE
    // -------------------------------------------------------------------
    @Transactional
    public UrlResponse createShortLink(UrlCreateRequest request, UUID userId) {

        String urlHash = generateHash(request.longUrl());

        // Scope lookup by userId — different users get their own short codes
        Optional<Url> existingUrl = userId != null
                ? urlRepository.findByLongUrlHashAndUserId(urlHash, userId)
                : urlRepository.findByLongUrlHashAndUserIdIsNull(urlHash);

        if (existingUrl.isPresent()) {
            return toResponse(existingUrl.get());
        }

        // Save with placeholder — flush forces DB to assign ID immediately
        Url newUrl = Url.builder()
                .longUrl(request.longUrl())
                .longUrlHash(urlHash)
                .userId(userId)
                .title(request.title())
                .shortCode("PENDING")
                .isActive(true)
                .build();

        urlRepository.saveAndFlush(newUrl);

        // Derive short code from DB-assigned ID
        String shortCode = Base62Encoder.encode(newUrl.getId());
        newUrl.setShortCode(shortCode);

        // Warm both cache keys — longUrl AND urlId
        Duration ttl = Duration.ofDays(cacheTtlDays);
        redisTemplate.opsForValue().set(REDIS_URL_PREFIX + shortCode,
                request.longUrl(), ttl);
        redisTemplate.opsForValue().set(REDIS_URL_PREFIX + shortCode + REDIS_ID_SUFFIX,
                newUrl.getId().toString(), ttl);

        return toResponse(newUrl);
    }

    // -------------------------------------------------------------------
    // REDIRECT — cache-aside, returns both urlId and longUrl
    // -------------------------------------------------------------------
    public RedirectResult getLongUrl(String shortCode) {
        String cacheKey = REDIS_URL_PREFIX + shortCode;
        String idKey = cacheKey + REDIS_ID_SUFFIX;

        String cachedUrl = redisTemplate.opsForValue().get(cacheKey);
        String cachedId = redisTemplate.opsForValue().get(idKey);

        if (cachedUrl != null && cachedId != null) {
            return new RedirectResult(Long.parseLong(cachedId), cachedUrl);
        }

        // Cache miss — hit Postgres
        Url url = urlRepository.findByShortCodeAndIsActiveTrue(shortCode)
                .orElseThrow(() -> new UrlNotFoundException("URL not found"));

        if (url.getDeletedAt() != null) {
            throw new UrlDisabledException("This link is no longer available");
        }

        if (url.getExpiresAt() != null && url.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new UrlExpiredException("This link has expired");
        }

        // Hydrate both cache keys
        Duration ttl = Duration.ofDays(cacheTtlDays);
        redisTemplate.opsForValue().set(cacheKey, url.getLongUrl(), ttl);
        redisTemplate.opsForValue().set(idKey, url.getId().toString(), ttl);

        return new RedirectResult(url.getId(), url.getLongUrl());
    }

    // -------------------------------------------------------------------
    // USER DASHBOARD — paginated, no deleted URLs, includes stats
    // -------------------------------------------------------------------
    public Page<UrlDashboardResponse> getUserUrls(JwtPrincipal principal, int page, int size) {

        UUID userId = principal.userId();
        PageRequest pageRequest = PageRequest.of(page, size,
                Sort.by("createdAt").descending());

        Page<Url> userUrls = urlRepository
                .findAllByUserIdAndDeletedAtIsNull(userId, pageRequest);

        // Batch fetch stats — avoids N+1
        List<Long> urlIds = userUrls.getContent()
                .stream()
                .map(Url::getId)
                .toList();

        Map<Long, UrlStats> statsMap = urlStatsRepository
                .findAllById(urlIds)
                .stream()
                .collect(Collectors.toMap(UrlStats::getUrlId, s -> s));

        return userUrls.map(url -> {
            UrlStats stats = statsMap.get(url.getId());
            return new UrlDashboardResponse(
                    url.getId(),
                    url.getShortCode(),
                    url.getTitle(),
                    url.getCreatedAt(),
                    url.getExpiresAt(),
                    url.getIsActive()
            );
        });
    }

    // -------------------------------------------------------------------
    // SOFT DELETE
    // -------------------------------------------------------------------
    @Transactional
    public void deleteUrl(Long urlId, JwtPrincipal principal) {
        Url url = urlRepository.findById(urlId)
                .orElseThrow(() -> new UrlNotFoundException("URL not found"));

        if (url.getUserId() == null || !url.getUserId().equals(principal.userId())) {
            throw new UrlNotFoundException("URL not found");
        }

        url.setDeletedAt(OffsetDateTime.now());
        url.setIsActive(false);

        // Evict from cache
        redisTemplate.delete(REDIS_URL_PREFIX + url.getShortCode());
        redisTemplate.delete(REDIS_URL_PREFIX + url.getShortCode() + REDIS_ID_SUFFIX);
    }

    // -------------------------------------------------------------------
    // TOGGLE ACTIVE
    // -------------------------------------------------------------------
    @Transactional
    public UrlResponse toggleActive(Long urlId, JwtPrincipal principal) {
        Url url = urlRepository.findById(urlId)
                .orElseThrow(() -> new UrlNotFoundException("URL not found"));

        if (url.getUserId() == null || !url.getUserId().equals(principal.userId())) {
            throw new UrlNotFoundException("URL not found");
        }

        url.setIsActive(!url.getIsActive());

        // Evict cache so next redirect re-checks DB state
        redisTemplate.delete(REDIS_URL_PREFIX + url.getShortCode());
        redisTemplate.delete(REDIS_URL_PREFIX + url.getShortCode() + REDIS_ID_SUFFIX);

        return toResponse(url);
    }

    // -------------------------------------------------------------------
    // UPDATE URL (PATCH)
    // -------------------------------------------------------------------
    @Transactional
    public UrlResponse updateUrl(Long urlId, UrlUpdateRequest request, JwtPrincipal principal) {

        // 1. Fetch the URL
        Url url = urlRepository.findById(urlId)
                .orElseThrow(() -> new UrlNotFoundException("URL not found"));

        // 2. Ownership Lock (Anonymous URLs cannot be updated)
        if (url.getUserId() == null || !url.getUserId().equals(principal.userId())) {
            throw new UrlNotFoundException("URL not found"); // Masked to prevent enumeration
        }

        // 3. Soft-Delete Guard
        if (url.getDeletedAt() != null) {
            throw new UrlNotFoundException("URL not found");
        }

        // 4. Update the core destination and hash (ONLY if it actually changed)
        if (request.longUrl() != null && !url.getLongUrl().equals(request.longUrl())) {

            // Optional: You could check if the NEW hash already exists for this user here
            // to prevent them from creating duplicate destinations.

            url.setLongUrl(request.longUrl());
            url.setLongUrlHash(generateHash(request.longUrl()));

            // CRITICAL: Evict the old destination from the fast-path Redis cache
            redisTemplate.delete(REDIS_URL_PREFIX + url.getShortCode());
            redisTemplate.delete(REDIS_URL_PREFIX + url.getShortCode() + REDIS_ID_SUFFIX);
        }

        // 5. Update optional metadata fields if provided
        if (request.title() != null) {
            url.setTitle(request.title());
        }

        if (request.isActive() != null) {
            url.setIsActive(request.isActive());
            // If they toggle it off, evict the cache so the redirect hits the DB and fails
            if (!request.isActive()) {
                redisTemplate.delete(REDIS_URL_PREFIX + url.getShortCode());
                redisTemplate.delete(REDIS_URL_PREFIX + url.getShortCode() + REDIS_ID_SUFFIX);
            }
        }

        if (request.expiresAt() != null) {
            url.setExpiresAt(request.expiresAt());
        }

        return toResponse(url);
    }

    // -------------------------------------------------------------------
    // INTERNAL RECORD — returned by getLongUrl to controller
    // -------------------------------------------------------------------
    public record RedirectResult(
            Long urlId,
            String longUrl
    ) {}

    // -------------------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------------------
    private UrlResponse toResponse(Url url) {
        return new UrlResponse(
                url.getId(),
                url.getShortCode(),
                url.getLongUrl(),
                url.getTitle(),
                url.getCreatedAt()
        );
    }

    private String generateHash(String longUrl) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    longUrl.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new UrlNotFoundException("Error processing URL");
        }
    }
}
