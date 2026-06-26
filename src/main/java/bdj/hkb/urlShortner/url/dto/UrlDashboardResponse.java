package bdj.hkb.urlShortner.url.dto;

import java.time.OffsetDateTime;

public record UrlDashboardResponse(
        String shortCode,
        String title,
        OffsetDateTime createdAt,
        boolean isActive
) {
}
