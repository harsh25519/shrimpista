package bdj.hkb.urlShortner.url.dto;

import java.time.OffsetDateTime;

public record UrlResponse(
        Long urlId,
        String shortCode,
        String longUrl,
        String title,
        OffsetDateTime createdAt
) {
}
