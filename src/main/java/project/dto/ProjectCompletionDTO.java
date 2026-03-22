package project.dto;

import java.util.List;

public record ProjectCompletionDTO(
                String status,
                String error,
                String failedStage,
                Boolean retryable,
                String thumbnailUrl,
                String thumbnailStorageUri,
                String thumbnailBucket,
                String thumbnailObjectPath,
                List<ClipDTO> clips,
                Integer actualDuration) {

        public record ClipDTO(
                        String id,
                        String name,
                        String description,
                        String videoUrl,
                        String thumbnailUrl,
                        String videoStorageUri,
                        String videoBucket,
                        String videoObjectPath,
                        String thumbnailStorageUri,
                        String thumbnailBucket,
                        String thumbnailObjectPath,
                        double startTime,
                        double endTime,
                        double viralityScore) {
        }
}
