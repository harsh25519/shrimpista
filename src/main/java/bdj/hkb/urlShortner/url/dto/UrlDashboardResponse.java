package bdj.hkb.urlShortner.url.dto;

import java.time.OffsetDateTime;

public record UrlDashboardResponse(
        Long urlId,
        String shortCode,
        String title,
        OffsetDateTime createdAt,
        OffsetDateTime expiresAt,
        boolean isActive
) {
}
