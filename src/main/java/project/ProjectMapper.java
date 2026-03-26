package project;

import jakarta.enterprise.context.ApplicationScoped;
import project.dto.ProjectRequest;

@ApplicationScoped
public class ProjectMapper {

    public Project toEntity(ProjectRequest request, String userId) {
        Project project = new Project();
        project.title = request.title();
        project.userId = userId;
        project.status = "PROCESSING";
        project.customPrompt = request.customPrompt();
        project.sourceKind = request.sourceKind();
        project.sourceOriginUrl = request.sourceOriginUrl();
        project.sourceProvider = request.sourceProvider();
        project.sourceStorageUri = request.sourceStorageUri();
        project.sourceBucket = request.sourceBucket();
        project.sourceObjectPath = request.sourceObjectPath();
        project.sourceFileName = request.sourceFileName();
        project.sourceContentType = request.sourceContentType();
        project.sourceSizeBytes = request.sourceSizeBytes();
        return project;
    }
}
