package project;

import java.util.List;
import java.util.UUID;

import clip.Clip;
import clip.ClipRepository;
import common.dto.request.SpecificationRequest;
import common.dto.response.PagedResponse;
import integration.AiClipperClient;
import integration.dto.VideoProcessRequest;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import project.dto.ProjectCompletionDTO;
import project.dto.ProjectRequest;
import user.PlanMode;
import user.User;
import user.UserRepository;
import io.quarkus.scheduler.Scheduled;
import java.time.LocalDateTime;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ProjectService {

    private static final Logger LOG = Logger.getLogger(ProjectService.class);

    @Inject
    ProjectRepository projectRepository;

    @Inject
    ProjectMapper projectMapper;

    @Inject
    @RestClient
    AiClipperClient aiClipperClient;

    @Inject
    ClipRepository clipRepository;

    @Inject
    UserRepository userRepository;

    public Uni<PagedResponse<Project>> getProjects(SpecificationRequest request, String userId) {
        return projectRepository.findByTitleLike(request, userId)
                .flatMap(projects -> projectRepository.countByTitleLike(request, userId)
                        .map(total -> PagedResponse.of(projects, total, request.page, request.size)));
    }

    @WithSession
    public Uni<Project> getProjectById(UUID projectId, String userId) {
        return projectRepository.findById(projectId)
                .onItem().ifNull().failWith(() -> new NotFoundException("Project not found"))
                .onItem().ifNotNull().invoke(project -> {
                    if (!userId.equals(project.userId)) {
                        throw new ForbiddenException("You do not have permission to view this project");
                    }
                });
    }

    public Uni<List<Clip>> getProjectClips(UUID projectId, String userId) {
        return getProjectById(projectId, userId)
                .flatMap(project -> clipRepository.list("project.id", projectId));
    }

    @WithTransaction
    public Uni<Project> createProject(String userId, ProjectRequest request) {
        // 1. Calculate duration and cost upfront
        Uni<Integer> durationUni;
        if (request.durationSeconds() != null) {
            durationUni = Uni.createFrom().item(request.durationSeconds());
        } else if (request.sourceUrl() != null && !request.sourceUrl().isBlank()) {
            durationUni = aiClipperClient.getMetadata(request.sourceUrl())
                    .map(metadata -> metadata.duration())
                    .onFailure().recoverWithItem(0); // fallback to 0 if yt-dlp fails
        } else {
            durationUni = Uni.createFrom().item(0);
        }

        return durationUni.flatMap(duration -> {
            int cost = calculateCost(duration);

            return userRepository.findById(userId)
                    .onItem().ifNull().switchTo(() -> {
                        User newUser = new User();
                        newUser.id = userId;
                        newUser.creditsRemaining = 3;
                        newUser.planMode = PlanMode.FREE_TRIAL;
                        return userRepository.persist(newUser);
                    })
                    .flatMap(user -> {
                        if (user.creditsRemaining < cost) {
                            return Uni.createFrom().failure(new ForbiddenException(
                                    "Insufficient credits. This <b>%d-minute</b> video requires <b>%d credits</b>, but you only have <b>%d</b>. Please top up."
                                            .formatted((duration / 60), cost, user.creditsRemaining)));
                        }
                        user.creditsRemaining -= cost;
                        return userRepository.persist(user);
                    })
                    .flatMap(user -> {
                        Project project = projectMapper.toEntity(request, userId);
                        project.status = "PROCESSING";
                        project.durationSeconds = duration;
                        project.cost = cost;

                        return projectRepository.persist(project)
                                .flatMap(saved -> {
                                    VideoProcessRequest processReq = new VideoProcessRequest(
                                            saved.id.toString(),
                                            userId,
                                            request.sourceUrl(),
                                            request.customPrompt());
                                    return aiClipperClient.processVideo(processReq)
                                            .replaceWith(saved)
                                            .onFailure().recoverWithUni(t -> {
                                                LOG.errorf(t, "Failed to reach AI Worker for project %s", saved.id);
                                                return markProjectFailed(saved.id,
                                                        "AI service unavailable: " + t.getMessage())
                                                        .replaceWith(saved);
                                            });
                                });
                    });
        });
    }

    @WithTransaction
    public Uni<Void> handleCompletion(UUID projectId, ProjectCompletionDTO completion) {
        if ("FAILED".equals(completion.status())) {
            // 2. Webhook Failure Catch: Python reported a specific crash
            return markProjectFailed(projectId, "Worker reported FAILED status via webhook.");
        }

        return projectRepository.findById(projectId)
                .onItem().ifNull().failWith(() -> new NotFoundException("Project not found"))
                .flatMap(project -> {
                    project.status = completion.status();
                    if (completion.thumbnailUrl() != null && !completion.thumbnailUrl().isBlank()) {
                        project.thumbnailUrl = completion.thumbnailUrl();
                    }

                    if ("COMPLETED".equals(completion.status()) && completion.clips() != null) {
                        try {
                            List<Clip> clips = completion.clips().stream().map(dto -> {
                                Clip clip = new Clip();
                                clip.id = UUID.fromString(dto.id());
                                clip.title = dto.name();
                                clip.description = dto.description();
                                clip.videoUrl = dto.videoUrl();
                                clip.thumbnailUrl = dto.thumbnailUrl();
                                clip.viralScore = (int) dto.viralityScore();
                                clip.startTime = java.time.LocalTime.ofSecondOfDay((long) dto.startTime());
                                clip.endTime = java.time.LocalTime.ofSecondOfDay((long) dto.endTime());
                                clip.project = project;
                                return clip;
                            }).toList();

                            return clipRepository.persist(clips).replaceWithVoid();
                        } catch (Exception e) {
                            // If parsing fails or any other error, mark as failed
                            return markProjectFailed(projectId, "Failed to save clips: " + e.getMessage());
                        }
                    }
                    return Uni.createFrom().voidItem();
                })
                .onFailure(t -> !(t instanceof NotFoundException))
                .recoverWithUni(t -> markProjectFailed(projectId, t.getMessage()));
    }

    @WithTransaction
    public Uni<Void> markProjectFailed(UUID projectId, String reason) {
        return projectRepository.findById(projectId)
                .onItem().ifNotNull().transformToUni(project -> {
                    if ("FAILED".equals(project.status)) {
                        // Prevent duplicate refunds if job already failed
                        return Uni.createFrom().voidItem();
                    }
                    project.status = "FAILED";
                    LOG.warnf("Project %s marked as FAILED. Reason: %s", projectId, reason);

                    return userRepository.findById(project.userId)
                            .flatMap(user -> {
                                if (user != null) {
                                    int refundAmount = project.cost > 0 ? project.cost : 1;
                                    user.creditsRemaining += refundAmount;
                                    LOG.infof("Refunded %d credit(s) to user %s for failed project %s", refundAmount,
                                            user.id, projectId);
                                    return userRepository.persist(user);
                                }
                                return Uni.createFrom().nullItem();
                            })
                            .flatMap(ignored -> projectRepository.persist(project));
                })
                .replaceWithVoid();
    }

    @WithTransaction
    public Uni<Project> retryProject(UUID projectId, String userId) {
        return projectRepository.findById(projectId)
                .onItem().ifNull().failWith(() -> new NotFoundException("Project not found"))
                .onItem().invoke(project -> {
                    if (!userId.equals(project.userId)) {
                        throw new ForbiddenException("You do not have permission to retry this project");
                    }
                })
                .flatMap(project -> {
                    project.status = "PROCESSING";
                    // Clear previous clips if any, to avoid duplicates?
                    // Actually, pipeline creates new clip IDs, so we might end up with duplicates
                    // or mixed sets
                    // But if we delete them, we lose old clips if pipeline fails again.
                    // For now, let's keep them. Pipeline overwrites if IDs match (unlikely with
                    // UUID)
                    // Ideally we should clear clips or pipeline handles it.
                    // Let's rely on smart pipeline. The pipeline generates new clip IDs each run.
                    // So we should probably delete existing clips for this project to keep it
                    // clean.

                    return clipRepository.delete("project.id", projectId)
                            .flatMap(ignored -> {
                                VideoProcessRequest processReq = new VideoProcessRequest(
                                        project.id.toString(),
                                        userId,
                                        project.sourceUrl,
                                        null // customPrompt not stored in project entity currently, would need to add
                                             // it if we want to persist it
                                );

                                // Make sure we deduct a credit on retry as well.
                                // NOTE: Normally retries should also deduct credits if they represent a brand
                                // new try.
                                return userRepository.findById(userId)
                                        .flatMap(user -> {
                                            if (user.creditsRemaining <= 0) {
                                                return Uni.createFrom().failure(
                                                        new ForbiddenException("Insufficient credits for retry."));
                                            }
                                            user.creditsRemaining -= 1;
                                            return userRepository.persist(user);
                                        })
                                        .flatMap(userPersisted -> aiClipperClient.processVideo(processReq) // Changed
                                                                                                           // 'ignored'
                                                                                                           // to
                                                                                                           // 'userPersisted'
                                                .replaceWith(project)
                                                .onFailure().recoverWithUni(t -> {
                                                    LOG.errorf(t, "Failed to reach AI Worker for project retry %s",
                                                            project.id);
                                                    return markProjectFailed(project.id,
                                                            "AI service unavailable during retry: " + t.getMessage())
                                                            .replaceWith(project);
                                                }));
                            });
                });
    }

    /**
     * 3. The 24-Hour Sweeper (Async Cron Job)
     * Handles silent mid-flight crashes (OOM kills, absolute server wipeouts).
     * Sweeps every 1 hour, looking for projects stuck in 'PROCESSING' for > 24
     * hours.
     */
    @Scheduled(every = "1h")
    @WithTransaction
    public Uni<Void> sweepStuckProjects() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(24);
        return projectRepository.find("status = ?1 and createdAt < ?2", "PROCESSING", threshold)
                .list()
                .flatMap(stuckProjects -> {
                    if (stuckProjects.isEmpty()) {
                        return Uni.createFrom().voidItem();
                    }
                    LOG.warnf("Sweeper found %d jobs stuck in PROCESSING for over 24 hours. Issuing refunds.",
                            stuckProjects.size());

                    // Route each stuck project through the centralized refund logic
                    return io.smallrye.mutiny.Multi.createFrom().iterable(stuckProjects)
                            .onItem()
                            .transformToUniAndConcatenate(project -> markProjectFailed(project.id,
                                    "24-hour timeout sweep (Silent crash presumed)"))
                            .collect().asList()
                            .replaceWithVoid();
                });
    }

    public Uni<Integer> estimateCost(String url) {
        if (url == null || url.isBlank()) {
            return Uni.createFrom().item(1);
        }
        return aiClipperClient.getMetadata(url)
                .map(metadata -> calculateCost(metadata.duration()))
                .onFailure().recoverWithItem(1); // fallback to 1 credit if yt-dlp fails
    }

    public int calculateCost(Integer durationSeconds) {
        if (durationSeconds == null || durationSeconds <= 0) {
            return 1; // Minimum 1 credit fallback
        }
        // Formula: 1 Credit per 2 Minutes (120 seconds). Rounded up.
        int minutes = (int) Math.ceil(durationSeconds / 60.0);
        int cost = (int) Math.ceil(minutes / 2.0);
        return Math.max(1, cost); // At least 1 credit
    }
}
