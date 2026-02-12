package project;

import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;
import project.dto.ProjectRequest;

@ApplicationScoped
public class ProjectMapper {
    
    public Project toEntity(ProjectRequest request) {
        Project project = new Project();
        project.title = request.title();
        project.userId = UUID.fromString(request.userId());
        project.status = request.status();
        project.thumbnailUrl = request.thumbnailUrl();
        return project;
    }
}
