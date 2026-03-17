package publish.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record PublishJobResponse(
        UUID id,
        UUID batchId,
        UUID clipId,
        UUID connectionId,
        String platform,
        String status,
        String requestedTitle,
        String requestedDescription,
        String requestedPrivacyLevel,
        String providerPublishId,
        String providerVideoId,
        String providerUrl,
        String providerStatus,
        String errorMessage,
        int retryCount,
        LocalDateTime nextAttemptAt,
        LocalDateTime publishedAt,
        LocalDateTime updatedAt) {
}
