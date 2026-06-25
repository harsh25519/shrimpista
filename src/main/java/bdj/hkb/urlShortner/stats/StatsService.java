package bdj.hkb.urlShortner.stats;

import bdj.hkb.urlShortner.stats.dto.StatsResponse;
import bdj.hkb.urlShortner.url.Url;
import bdj.hkb.urlShortner.url.UrlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class StatsService {
    private final UrlStatsRepository statsRepository;
    private final UrlRepository urlRepository; // Inject the repository instead

    public StatsResponse getStatsSummary(String shortCode) {
        // 1. Get the URL entity directly from the database to find its ID
        Url url = urlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new RuntimeException("Short code not found"));

        // 2. Fetch stats, or default to 0 if the link has never been clicked
        UrlStats stats = statsRepository.findById(url.getId())
                .orElse(UrlStats.builder()
                        .urlId(url.getId())
                        .totalClicks(0L)
                        .uniqueVisitors(0L)
                        .lastUpdatedAt(OffsetDateTime.now())
                        .build());

        // 3. Map to the clean DTO
        return new StatsResponse(
                shortCode,
                url.getLongUrl(),
                stats.getTotalClicks(),
                stats.getUniqueVisitors(),
                stats.getLastUpdatedAt()
        );
    }
}
