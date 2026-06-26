package bdj.hkb.urlShortner.url.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.OffsetDateTime;

public record UrlUpdateRequest(
        @NotBlank String longUrl,

        String title,
        Boolean isActive,
        OffsetDateTime expiresAt
) {
}
