package publish.dto;

import java.util.List;
import java.util.UUID;

public record PublishBatchRequest(
        UUID projectId,
        List<PublishJobRequest> jobs) {
}
