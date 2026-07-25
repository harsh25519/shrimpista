package bdj.hkb.urlShortner.admin.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminUrlResponse (
        Long id,
        String shortCode,
        String longUrl,
        UUID ownerId,
        boolean isActive,
        boolean isDeleted,
        OffsetDateTime createdAt
){
}
