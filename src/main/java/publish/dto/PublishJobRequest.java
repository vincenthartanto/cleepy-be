package publish.dto;

import java.util.UUID;

public record PublishJobRequest(
        UUID clipId,
        String platform,
        UUID connectionId,
        String title,
        String description,
        String privacyLevel) {
}
