package project;

import jakarta.enterprise.context.ApplicationScoped;
import project.dto.ProjectRequest;

@ApplicationScoped
public class ProjectMapper {

    public Project toEntity(ProjectRequest request, String userId) {
        Project project = new Project();
        project.title = request.title();
        project.userId = userId;
        project.status = "processing";
        return project;
    }
}
