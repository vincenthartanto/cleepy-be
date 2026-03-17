package social.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record SocialConnectionResponse(
        UUID id,
        String platform,
        String displayName,
        String status,
        String scopes,
        LocalDateTime tokenExpiresAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
