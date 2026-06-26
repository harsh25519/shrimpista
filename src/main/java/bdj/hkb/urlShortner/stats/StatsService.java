package bdj.hkb.urlShortner.stats;

import bdj.hkb.urlShortner.exceptionHandler.AccessDeniedException;
import bdj.hkb.urlShortner.exceptionHandler.UrlNotFoundException;
import bdj.hkb.urlShortner.security.dto.JwtPrincipal;
import bdj.hkb.urlShortner.stats.dto.StatsResponse;
import bdj.hkb.urlShortner.url.Url;
import bdj.hkb.urlShortner.url.UrlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final UrlStatsRepository statsRepository;
    private final UrlRepository urlRepository;
    private final StringRedisTemplate redisTemplate;

    private static final String CLICK_UNIQUE_PREFIX = "click:unique:";

    public StatsResponse getStatsSummary(String shortCode, JwtPrincipal principal) {

        // 1. Resolve shortCode
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException("URL not found"));

        // 2. Ownership check
        if (url.getUserId() == null || !url.getUserId().equals(principal.userId())) {
            throw new AccessDeniedException("You don't have access to this URL's stats");
        }

        // 3. Soft-delete guard
        if (url.getDeletedAt() != null) {
            throw new UrlNotFoundException("URL not found");
        }

        // 4. Fetch persisted stats from DB
        UrlStats stats = statsRepository.findById(url.getId())
                .orElse(UrlStats.builder()
                        .urlId(url.getId())
                        .totalClicks(0L)
                        .uniqueVisitors(0L)
                        .lastUpdatedAt(OffsetDateTime.now())
                        .build());


        // Safe — HyperLogLog is additive, not summed with DB value
        long liveUniqueVisitors = redisTemplate.opsForHyperLogLog()
                .size(CLICK_UNIQUE_PREFIX + url.getId());

        return new StatsResponse(
                shortCode,
                url.getLongUrl(),
                stats.getTotalClicks(),      // DB
                Math.max(stats.getUniqueVisitors(), liveUniqueVisitors), // take the higher of the two
                stats.getLastUpdatedAt()
        );
    }
}
