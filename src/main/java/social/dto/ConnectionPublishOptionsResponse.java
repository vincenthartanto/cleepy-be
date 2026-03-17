package social.dto;

import java.util.List;

public record ConnectionPublishOptionsResponse(
        String platform,
        String displayName,
        List<String> privacyLevels,
        Integer maxDurationSeconds) {
}
