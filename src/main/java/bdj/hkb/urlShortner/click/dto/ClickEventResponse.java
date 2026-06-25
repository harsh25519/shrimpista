package bdj.hkb.urlShortner.click.dto;

import java.time.OffsetDateTime;

public record ClickEventResponse(
        String ipAddress,
        String userAgent,
        String referrer,
        OffsetDateTime clickedAt
) {
}
