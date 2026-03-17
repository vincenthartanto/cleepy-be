package publish;

import java.util.List;
import java.util.UUID;

import io.quarkus.arc.All;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduled.ConcurrentExecution;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import social.SocialConnectionService;
import social.SocialPlatform;

@ApplicationScoped
public class PublishWorkerService {

    @Inject
    PublishJobRepository publishJobRepository;

    @Inject
    PublishJobLifecycleService publishJobLifecycleService;

    @Inject
    SocialConnectionService socialConnectionService;

    @Inject
    @All
    List<PublisherAdapter> adapters;

    @Scheduled(every = "15s", concurrentExecution = ConcurrentExecution.SKIP)
    public Uni<Void> dispatchQueuedJobs() {
        return publishJobRepository.findRunnableJobs(4)
                .flatMap(jobs -> Multi.createFrom().iterable(jobs)
                        .onItem().transformToUniAndMerge(job -> processQueuedJob(job.id))
                        .collect().asList()
                        .replaceWithVoid());
    }

    @Scheduled(every = "30s", concurrentExecution = ConcurrentExecution.SKIP)
    public Uni<Void> refreshProcessingJobs() {
        return publishJobRepository.findProcessingJobs(8)
                .flatMap(jobs -> Multi.createFrom().iterable(jobs)
                        .onItem().transformToUniAndMerge(job -> refreshJob(job.id))
                        .collect().asList()
                        .replaceWithVoid());
    }

    public Uni<Void> processQueuedJob(UUID jobId) {
        return publishJobLifecycleService.claimRunnableJob(jobId)
                .flatMap(job -> {
                    if (job == null) {
                        return Uni.createFrom().voidItem();
                    }
                    return socialConnectionService.getOperationalConnection(job.connectionId, job.userId)
                            .flatMap(connection -> publishJobLifecycleService.loadContext(job, connection))
                            .flatMap(context -> adapterFor(context.job().platform).submit(context))
                            .flatMap(result -> publishJobLifecycleService.applySubmission(job.id, result))
                            .onFailure().recoverWithUni(t -> publishJobLifecycleService.handleFailure(job.id, t));
                });
    }

    public Uni<Void> refreshJob(UUID jobId) {
        return publishJobRepository.findById(jobId)
                .flatMap(job -> {
                    if (job == null || !PublishJobStatus.PROCESSING.name().equals(job.status)) {
                        return Uni.createFrom().voidItem();
                    }
                    return socialConnectionService.getOperationalConnection(job.connectionId, job.userId)
                            .flatMap(connection -> publishJobLifecycleService.loadContext(job, connection))
                            .flatMap(context -> adapterFor(context.job().platform).refresh(context))
                            .flatMap(result -> publishJobLifecycleService.applyRefresh(job.id, result))
                            .onFailure().recoverWithUni(t -> publishJobLifecycleService.handleFailure(job.id, t));
                });
    }

    private PublisherAdapter adapterFor(String platform) {
        SocialPlatform socialPlatform = SocialPlatform.valueOf(platform);
        return adapters.stream()
                .filter(adapter -> adapter.platform() == socialPlatform)
                .findFirst()
                .orElseThrow(() -> new PublishFailure("No publisher adapter for platform " + platform, false));
    }
}
