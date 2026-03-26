package project.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import project.Project;

public record ProjectDetailsResponse(
        UUID id,
        String title,
        String status,
        String customPrompt,
        String thumbnailUrl,
        String userId,
        String sourceUrl,
        String sourceKind,
        String sourceOriginUrl,
        String sourceProvider,
        String sourceFileName,
        String sourceContentType,
        Long sourceSizeBytes,
        int cost,
        Integer durationSeconds,
        int workerRetryCount,
        String lastFailedStage,
        String lastFailureReason,
        Integer clipsCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static ProjectDetailsResponse from(Project project) {
        return new ProjectDetailsResponse(
                project.id,
                project.title,
                project.status,
                project.customPrompt,
                project.thumbnailUrl,
                project.userId,
                project.sourceUrl,
                project.sourceKind,
                project.sourceOriginUrl,
                project.sourceProvider,
                project.sourceFileName,
                project.sourceContentType,
                project.sourceSizeBytes,
                project.cost,
                project.durationSeconds,
                project.workerRetryCount,
                project.lastFailedStage,
                project.lastFailureReason,
                project.clipsCount,
                project.createdAt,
                project.updatedAt);
    }
}
