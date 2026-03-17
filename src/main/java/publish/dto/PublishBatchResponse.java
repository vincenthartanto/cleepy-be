package publish.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PublishBatchResponse(
        UUID id,
        UUID projectId,
        String status,
        int totalJobs,
        int completedJobs,
        int failedJobs,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<PublishJobResponse> jobs) {
}
