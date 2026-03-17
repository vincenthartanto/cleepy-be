package publish;

import java.time.LocalDateTime;
import java.util.UUID;

import clip.Clip;
import clip.ClipRepository;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import social.SocialConnection;

@ApplicationScoped
public class PublishJobLifecycleService {

    private static final int MAX_RETRIES = 2;

    @Inject
    PublishJobRepository jobRepository;

    @Inject
    ClipRepository clipRepository;

    @Inject
    PublishBatchService publishBatchService;

    @WithTransaction
    public Uni<PublishJob> claimRunnableJob(UUID jobId) {
        return jobRepository.findById(jobId)
                .flatMap(job -> {
                    if (job == null) {
                        return Uni.createFrom().nullItem();
                    }
                    boolean ready = PublishJobStatus.QUEUED.name().equals(job.status)
                            || (PublishJobStatus.FAILED.name().equals(job.status)
                                    && job.nextAttemptAt != null
                                    && !job.nextAttemptAt.isAfter(LocalDateTime.now())
                                    && job.retryCount <= MAX_RETRIES);
                    if (!ready) {
                        return Uni.createFrom().nullItem();
                    }

                    job.status = PublishJobStatus.UPLOADING.name();
                    job.errorMessage = null;
                    job.nextAttemptAt = null;
                    return jobRepository.persist(job);
                });
    }

    @WithSession
    public Uni<PublishExecutionContext> loadContext(PublishJob job, SocialConnection connection) {
        return clipRepository.findById(job.clipId)
                .onItem().ifNull().failWith(() -> new NotFoundException("Clip not found"))
                .map(clip -> new PublishExecutionContext(job, clip, connection));
    }

    @WithTransaction
    public Uni<Void> applySubmission(UUID jobId, PublishSubmissionResult result) {
        return jobRepository.findById(jobId)
                .flatMap(job -> {
                    if (job == null) {
                        return Uni.createFrom().voidItem();
                    }
                    job.status = result.status().name();
                    job.providerPublishId = result.providerPublishId();
                    job.providerVideoId = result.providerVideoId();
                    job.providerUrl = result.providerUrl();
                    job.providerStatus = result.providerStatus();
                    job.errorMessage = null;
                    if (result.status() == PublishJobStatus.PUBLISHED) {
                        job.publishedAt = LocalDateTime.now();
                    }
                    return jobRepository.persist(job)
                            .flatMap(ignored -> publishBatchService.refreshBatchStatus(job.batchId));
                });
    }

    @WithTransaction
    public Uni<Void> applyRefresh(UUID jobId, PublishStatusResult result) {
        return jobRepository.findById(jobId)
                .flatMap(job -> {
                    if (job == null) {
                        return Uni.createFrom().voidItem();
                    }
                    job.status = result.status().name();
                    job.providerStatus = result.providerStatus();
                    if (result.providerUrl() != null && !result.providerUrl().isBlank()) {
                        job.providerUrl = result.providerUrl();
                    }
                    job.errorMessage = result.errorMessage();
                    if (result.status() == PublishJobStatus.PUBLISHED) {
                        job.publishedAt = LocalDateTime.now();
                    }
                    return jobRepository.persist(job)
                            .flatMap(ignored -> publishBatchService.refreshBatchStatus(job.batchId));
                });
    }

    @WithTransaction
    public Uni<Void> handleFailure(UUID jobId, Throwable throwable) {
        return jobRepository.findById(jobId)
                .flatMap(job -> {
                    if (job == null) {
                        return Uni.createFrom().voidItem();
                    }

                    boolean retryable = !(throwable instanceof PublishFailure publishFailure) || publishFailure.retryable();
                    job.retryCount += 1;
                    job.errorMessage = throwable.getMessage();

                    if (retryable && job.retryCount <= MAX_RETRIES) {
                        job.status = PublishJobStatus.FAILED.name();
                        job.nextAttemptAt = LocalDateTime.now().plusMinutes(job.retryCount);
                    } else {
                        job.status = PublishJobStatus.FAILED.name();
                        job.nextAttemptAt = null;
                    }

                    return jobRepository.persist(job)
                            .flatMap(ignored -> publishBatchService.refreshBatchStatus(job.batchId));
                });
    }
}
