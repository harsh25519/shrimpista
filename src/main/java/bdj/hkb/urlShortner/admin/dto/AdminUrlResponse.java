package bdj.hkb.urlShortner.admin.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminUrlResponse (
        Long id,
        String shortCode,
        String longUrl,
        UUID ownerId, // Crucial for admins to see who created it
        boolean isActive,
        boolean isDeleted,
        OffsetDateTime createdAt
){
}
