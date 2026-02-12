package project;

import java.util.List;

import common.dto.request.SpecificationRequest;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ProjectService {

    @Inject
    ProjectRepository projectRepository;
    
    public Uni<List<Project>> getProjects(SpecificationRequest request) {
        return projectRepository.findByTitleLike(request);
    }
}
