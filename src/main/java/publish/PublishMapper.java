package publish;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import publish.dto.PublishBatchResponse;
import publish.dto.PublishJobResponse;

@ApplicationScoped
public class PublishMapper {

    public PublishBatchResponse toBatchResponse(PublishBatch batch, List<PublishJob> jobs) {
        return new PublishBatchResponse(
                batch.id,
                batch.projectId,
                batch.status,
                batch.totalJobs,
                batch.completedJobs,
                batch.failedJobs,
                batch.createdAt,
                batch.updatedAt,
                jobs.stream().map(this::toJobResponse).toList());
    }

    public PublishJobResponse toJobResponse(PublishJob job) {
        return new PublishJobResponse(
                job.id,
                job.batchId,
                job.clipId,
                job.connectionId,
                job.platform,
                job.status,
                job.requestedTitle,
                job.requestedDescription,
                job.requestedPrivacyLevel,
                job.providerPublishId,
                job.providerVideoId,
                job.providerUrl,
                job.providerStatus,
                job.errorMessage,
                job.retryCount,
                job.nextAttemptAt,
                job.publishedAt,
                job.updatedAt);
    }
}
