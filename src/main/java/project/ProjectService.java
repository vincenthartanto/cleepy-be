package project;

import java.util.UUID;

import common.dto.request.SpecificationRequest;
import common.dto.response.PagedResponse;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import project.dto.ProjectRequest;

@ApplicationScoped
public class ProjectService {

    @Inject
    ProjectRepository projectRepository;

    @Inject
    ProjectMapper projectMapper;

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

    @WithTransaction
    public Uni<Project> createProject(String userId, ProjectRequest request) {
        Project project = projectMapper.toEntity(request, userId);
        return projectRepository.persist(project);
    }
}
