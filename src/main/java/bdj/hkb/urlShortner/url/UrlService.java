package bdj.hkb.urlShortner.url;

import bdj.hkb.urlShortner.exceptionHandler.UrlDisabledException;
import bdj.hkb.urlShortner.exceptionHandler.UrlExpiredException;
import bdj.hkb.urlShortner.exceptionHandler.UrlNotFoundException;
import bdj.hkb.urlShortner.security.dto.JwtPrincipal;
import bdj.hkb.urlShortner.stats.UrlStats;
import bdj.hkb.urlShortner.stats.UrlStatsRepository;
import bdj.hkb.urlShortner.url.dto.*;
import bdj.hkb.urlShortner.util.Base62Encoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class UrlService {

    private final UrlRepository urlRepository;
    private final UrlStatsRepository urlStatsRepository;
    private final StringRedisTemplate redisTemplate;

    private static final String REDIS_URL_PREFIX = "url:route:";
    private static final String REDIS_ID_SUFFIX = ":id";

    @Value("${url.cache-ttl-days:7}")
    private long cacheTtlDays;

    @Transactional
    public UrlResponse createShortLink(UrlCreateRequest request, UUID userId) {

        String urlHash = generateHash(request.longUrl());

        Optional<Url> existingUrl = userId != null
                ? urlRepository.findByLongUrlHashAndUserId(urlHash, userId)
                : urlRepository.findByLongUrlHashAndUserIdIsNull(urlHash);

        if (existingUrl.isPresent()) {
            log.info(
                    "User {} reused existing short code {}",
                    userId,
                    existingUrl.get().getShortCode()
            );
            return toResponse(existingUrl.get());
        }

        Url newUrl = Url.builder()
                .longUrl(request.longUrl())
                .longUrlHash(urlHash)
                .userId(userId)
                .title(request.title())
                .shortCode("PENDING")
                .isActive(true)
                .build();

        urlRepository.saveAndFlush(newUrl);

        String shortCode = Base62Encoder.encode(newUrl.getId());
        newUrl.setShortCode(shortCode);
        log.info("User:{} created short URL {}", newUrl.getUserId(), shortCode);

        Duration ttl = Duration.ofDays(cacheTtlDays);
        redisTemplate.opsForValue().set(REDIS_URL_PREFIX + shortCode,
                request.longUrl(), ttl);
        redisTemplate.opsForValue().set(REDIS_URL_PREFIX + shortCode + REDIS_ID_SUFFIX,
                newUrl.getId().toString(), ttl);

        return toResponse(newUrl);
    }


    public RedirectResult getLongUrl(String shortCode) {
        String cacheKey = REDIS_URL_PREFIX + shortCode;
        String idKey = cacheKey + REDIS_ID_SUFFIX;

        String cachedUrl = redisTemplate.opsForValue().get(cacheKey);
        String cachedId = redisTemplate.opsForValue().get(idKey);

        if (cachedUrl != null && cachedId != null) {
            return new RedirectResult(Long.parseLong(cachedId), cachedUrl);
        }

        Url url = urlRepository.findByShortCodeAndIsActiveTrue(shortCode)
                .orElseThrow(() -> new UrlNotFoundException("URL not found"));

        if (url.getDeletedAt() != null) {
            log.warn("Long url for the short code:{} is disabled", shortCode);
            throw new UrlDisabledException("This link is no longer available");
        }

        if (url.getExpiresAt() != null && url.getExpiresAt().isBefore(OffsetDateTime.now())) {
            log.warn("The url for given short code:{} is expired",shortCode);
            throw new UrlExpiredException("This link has expired");
        }

        Duration ttl = Duration.ofDays(cacheTtlDays);
        redisTemplate.opsForValue().set(cacheKey, url.getLongUrl(), ttl);
        redisTemplate.opsForValue().set(idKey, url.getId().toString(), ttl);

        return new RedirectResult(url.getId(), url.getLongUrl());
    }

    public Page<UrlDashboardResponse> getUserUrls(JwtPrincipal principal, int page, int size) {

        UUID userId = principal.userId();
        PageRequest pageRequest = PageRequest.of(page, size,
                Sort.by("createdAt").descending());

        Page<Url> userUrls = urlRepository
                .findAllByUserIdAndDeletedAtIsNull(userId, pageRequest);

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


    @Transactional
    public void deleteUrl(Long urlId, JwtPrincipal principal) {
        Url url = urlRepository.findById(urlId)
                .orElseThrow(() -> new UrlNotFoundException("URL not found"));

        if (url.getUserId() == null || !url.getUserId().equals(principal.userId())) {
            log.warn(
                    "User {} attempted to delete URL {} they do not own",
                    principal.userId(),
                    urlId
            );
            throw new UrlNotFoundException("URL not found");
        }

        url.setDeletedAt(OffsetDateTime.now());
        url.setIsActive(false);

        log.info(
                "User {} deleted URL {}",
                principal.userId(),
                urlId
        );

        redisTemplate.delete(REDIS_URL_PREFIX + url.getShortCode());
        redisTemplate.delete(REDIS_URL_PREFIX + url.getShortCode() + REDIS_ID_SUFFIX);
    }


    @Transactional
    public UrlResponse toggleActive(Long urlId, JwtPrincipal principal) {
        Url url = urlRepository.findById(urlId)
                .orElseThrow(() -> new UrlNotFoundException("URL not found"));

        if (url.getUserId() == null || !url.getUserId().equals(principal.userId())) {
            log.warn(
                    "User {} attempted to toggle URL {} they do not own",
                    principal.userId(),
                    urlId
            );
            throw new UrlNotFoundException("URL not found");
        }

        url.setIsActive(!url.getIsActive());
        log.info(
                "User {} changed URL {} active status to {}",
                principal.userId(),
                urlId,
                url.getIsActive()
        );

        redisTemplate.delete(REDIS_URL_PREFIX + url.getShortCode());
        redisTemplate.delete(REDIS_URL_PREFIX + url.getShortCode() + REDIS_ID_SUFFIX);

        return toResponse(url);
    }


    @Transactional
    public UrlResponse updateUrl(Long urlId, UrlUpdateRequest request, JwtPrincipal principal) {

        // 1. Fetch the URL
        Url url = urlRepository.findById(urlId)
                .orElseThrow(() -> {
                    log.info("URL:{} cannot be found for updation", urlId);
                    return new UrlNotFoundException("URL not found");
                });

        if (url.getUserId() == null || !url.getUserId().equals(principal.userId())) {
            log.warn(
                    "User {} attempted to update URL {} they do not own",
                    principal.userId(),
                    urlId
            );
            throw new UrlNotFoundException("URL not found");
        }

        if (url.getDeletedAt() != null) {
            log.warn(
                    "User {} tried access stats for deleted URL:{}",
                    principal.userId(),
                    url.getId()
            );
            throw new UrlNotFoundException("URL not found");
        }

        if (request.longUrl() != null && !url.getLongUrl().equals(request.longUrl())) {

            url.setLongUrl(request.longUrl());
            url.setLongUrlHash(generateHash(request.longUrl()));

            redisTemplate.delete(REDIS_URL_PREFIX + url.getShortCode());
            redisTemplate.delete(REDIS_URL_PREFIX + url.getShortCode() + REDIS_ID_SUFFIX);
        }

        if (request.title() != null) {
            url.setTitle(request.title());
        }

        if (request.isActive() != null) {
            url.setIsActive(request.isActive());
            if (!request.isActive()) {
                redisTemplate.delete(REDIS_URL_PREFIX + url.getShortCode());
                redisTemplate.delete(REDIS_URL_PREFIX + url.getShortCode() + REDIS_ID_SUFFIX);
            }
        }

        if (request.expiresAt() != null) {
            url.setExpiresAt(request.expiresAt());
        }

        log.info(
                "User {} updated URL {}",
                principal.userId(),
                urlId
        );

        return toResponse(url);
    }

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
            log.error("Failed to hash URL", e);
            throw new UrlNotFoundException("Error processing URL");
        }
    }
}
