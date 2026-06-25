package bdj.hkb.urlShortner.stats.dto;

import java.time.OffsetDateTime;

public record StatsResponse(
        String shortCode,
        String url,
        Long totalClicks,
        Long uniqueVisitors,
        OffsetDateTime lastUpdatedAt
) {
}
