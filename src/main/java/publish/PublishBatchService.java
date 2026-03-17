package publish;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import clip.Clip;
import clip.ClipRepository;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import project.Project;
import project.ProjectRepository;
import publish.dto.PublishBatchRequest;
import publish.dto.PublishBatchResponse;
import publish.dto.PublishJobRequest;
import publish.dto.PublishJobResponse;
import social.SocialConnection;
import social.SocialConnectionRepository;
import social.SocialPlatform;

@ApplicationScoped
public class PublishBatchService {

    @Inject
    PublishBatchRepository batchRepository;

    @Inject
    PublishJobRepository jobRepository;

    @Inject
    PublishMapper publishMapper;

    @Inject
    ProjectRepository projectRepository;

    @Inject
    ClipRepository clipRepository;

    @Inject
    SocialConnectionRepository socialConnectionRepository;

    @WithTransaction
    public Uni<PublishBatchResponse> createBatch(String userId, PublishBatchRequest request) {
        if (request == null || request.projectId() == null || request.jobs() == null || request.jobs().isEmpty()) {
            return Uni.createFrom().failure(new BadRequestException("At least one publish job is required."));
        }

        return validateProject(userId, request.projectId())
                .flatMap(project -> validateJobs(userId, project, request.jobs())
                        .flatMap(jobInputs -> {
                            PublishBatch batch = new PublishBatch();
                            batch.userId = userId;
                            batch.projectId = request.projectId();
                            batch.status = PublishBatchStatus.QUEUED.name();
                            batch.totalJobs = jobInputs.size();
                            batch.completedJobs = 0;
                            batch.failedJobs = 0;

                            return batchRepository.persist(batch)
                                    .flatMap(savedBatch -> {
                                        List<PublishJob> jobs = jobInputs.stream()
                                                .map(input -> toJob(savedBatch.id, userId, project.id, input.request(), input.connection()))
                                                .toList();
                                        return jobRepository.persist(jobs)
                                                .replaceWith(savedBatch)
                                                .flatMap(saved -> jobRepository.findByBatchId(saved.id)
                                                        .map(persistedJobs -> publishMapper.toBatchResponse(saved, persistedJobs)));
                                    });
                        }));
    }

    @WithSession
    public Uni<PublishBatchResponse> getBatch(String userId, UUID batchId) {
        return batchRepository.findOwned(batchId, userId)
                .onItem().ifNull().failWith(() -> new NotFoundException("Publish batch not found"))
                .flatMap(batch -> jobRepository.findByBatchId(batch.id)
                        .map(jobs -> publishMapper.toBatchResponse(batch, jobs)));
    }

    @WithSession
    public Uni<List<PublishJobResponse>> getProjectJobs(String userId, UUID projectId) {
        return validateProject(userId, projectId)
                .flatMap(project -> jobRepository.findByProjectIdAndUserId(project.id, userId)
                        .map(jobs -> jobs.stream().map(publishMapper::toJobResponse).toList()));
    }

    @WithTransaction
    public Uni<Void> refreshBatchStatus(UUID batchId) {
        return batchRepository.findById(batchId)
                .flatMap(batch -> {
                    if (batch == null) {
                        return Uni.createFrom().voidItem();
                    }
                    return jobRepository.findByBatchId(batchId)
                            .flatMap(jobs -> {
                                int completed = (int) jobs.stream()
                                        .filter(job -> PublishJobStatus.PUBLISHED.name().equals(job.status))
                                        .count();
                                int failed = (int) jobs.stream()
                                        .filter(job -> PublishJobStatus.FAILED.name().equals(job.status))
                                        .count();

                                batch.completedJobs = completed;
                                batch.failedJobs = failed;
                                batch.totalJobs = jobs.size();
                                batch.status = deriveBatchStatus(jobs);
                                return batchRepository.persist(batch).replaceWithVoid();
                            });
                });
    }

    private String deriveBatchStatus(List<PublishJob> jobs) {
        if (jobs.isEmpty()) {
            return PublishBatchStatus.QUEUED.name();
        }
        long published = jobs.stream().filter(job -> PublishJobStatus.PUBLISHED.name().equals(job.status)).count();
        long failed = jobs.stream().filter(job -> PublishJobStatus.FAILED.name().equals(job.status)).count();
        if (published == jobs.size()) {
            return PublishBatchStatus.COMPLETED.name();
        }
        if (failed == jobs.size()) {
            return PublishBatchStatus.FAILED.name();
        }
        if (published + failed == jobs.size() && failed > 0) {
            return PublishBatchStatus.PARTIAL_FAILURE.name();
        }
        if (jobs.stream().anyMatch(job -> !PublishJobStatus.QUEUED.name().equals(job.status))) {
            return PublishBatchStatus.IN_PROGRESS.name();
        }
        return PublishBatchStatus.QUEUED.name();
    }

    private Uni<Project> validateProject(String userId, UUID projectId) {
        return projectRepository.findById(projectId)
                .onItem().ifNull().failWith(() -> new NotFoundException("Project not found"))
                .invoke(project -> {
                    if (!userId.equals(project.userId)) {
                        throw new ForbiddenException("You do not have permission to publish this project");
                    }
                    if (!"COMPLETED".equals(project.status)) {
                        throw new BadRequestException("Only completed projects can be published.");
                    }
                });
    }

    private Uni<List<ValidatedJobInput>> validateJobs(String userId, Project project, List<PublishJobRequest> requests) {
        return Multi.createFrom().iterable(requests)
                .onItem().transformToUniAndConcatenate(request -> validateJob(userId, project, request))
                .collect().asList();
    }

    private Uni<ValidatedJobInput> validateJob(String userId, Project project, PublishJobRequest request) {
        if (request.clipId() == null || request.connectionId() == null || request.platform() == null || request.platform().isBlank()) {
            return Uni.createFrom().failure(new BadRequestException("Each publish job requires clipId, platform, and connectionId."));
        }

        SocialPlatform platform = SocialPlatform.fromPath(request.platform());
        return clipRepository.findById(request.clipId())
                .onItem().ifNull().failWith(() -> new NotFoundException("Clip not found"))
                .flatMap(clip -> {
                    if (clip.project == null || !project.id.equals(clip.project.id)) {
                        return Uni.createFrom().failure(new BadRequestException("All publish jobs must target clips from the selected project."));
                    }
                    if (clip.videoBucket == null || clip.videoObjectPath == null) {
                        return Uni.createFrom().failure(new BadRequestException("Clip " + clip.id + " is not publishable because it lacks object storage metadata."));
                    }
                    return socialConnectionRepository.findOwned(request.connectionId(), userId)
                            .onItem().ifNull().failWith(() -> new NotFoundException("Social connection not found"))
                            .map(connection -> {
                                if (!platform.name().equals(connection.platform)) {
                                    throw new BadRequestException("Connection platform does not match publish job platform.");
                                }
                                return new ValidatedJobInput(request, clip, connection);
                            });
                });
    }

    private PublishJob toJob(UUID batchId, String userId, UUID projectId, PublishJobRequest request, SocialConnection connection) {
        PublishJob job = new PublishJob();
        job.batchId = batchId;
        job.userId = userId;
        job.projectId = projectId;
        job.clipId = request.clipId();
        job.connectionId = connection.id;
        job.platform = SocialPlatform.fromPath(request.platform()).name();
        job.status = PublishJobStatus.QUEUED.name();
        job.requestedTitle = request.title();
        job.requestedDescription = request.description();
        job.requestedPrivacyLevel = request.privacyLevel();
        job.retryCount = 0;
        job.nextAttemptAt = LocalDateTime.now();
        return job;
    }

    private record ValidatedJobInput(PublishJobRequest request, Clip clip, SocialConnection connection) {
    }
}
