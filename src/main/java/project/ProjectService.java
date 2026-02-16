package project;

import java.util.List;

import common.dto.request.SpecificationRequest;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import project.dto.ProjectRequest;

@ApplicationScoped
public class ProjectService {

    @Inject
    ProjectRepository projectRepository;

    @Inject
    ProjectMapper projectMapper;

    public Uni<List<Project>> getProjects(SpecificationRequest request) {
        return projectRepository.findByTitleLike(request);
    }

    @WithTransaction
    public Uni<Project> createProject(String userId, ProjectRequest request) {
        Project project = projectMapper.toEntity(request, userId);
        return projectRepository.persist(project);
    }
}
