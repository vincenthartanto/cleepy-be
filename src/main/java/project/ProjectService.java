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

@ApplicationScoped
public class ProjectService {

    @Inject
    ProjectRepository projectRepository;

    @Inject
    ProjectMapper projectMapper;

    @Inject
    @RestClient
    AiClipperClient aiClipperClient;

    @Inject
    ClipRepository clipRepository;

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
        Project project = projectMapper.toEntity(request, userId);
        project.status = "PROCESSING";

        return projectRepository.persist(project)
                .flatMap(saved -> {
                    VideoProcessRequest processReq = new VideoProcessRequest(
                            saved.id.toString(),
                            userId,
                            request.sourceUrl(),
                            request.customPrompt());
                    return aiClipperClient.processVideo(processReq)
                            .replaceWith(saved);
                });
    }

    @WithTransaction
    public Uni<Void> handleCompletion(UUID projectId, ProjectCompletionDTO completion) {
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
                .onItem().ifNotNull().invoke(p -> p.status = "FAILED")
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
                                return aiClipperClient.processVideo(processReq)
                                        .replaceWith(project);
                            });
                });
    }
}
