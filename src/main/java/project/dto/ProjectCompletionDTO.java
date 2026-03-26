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
                String sourceStorageUri,
                String sourceBucket,
                String sourceObjectPath,
                String sourceFileName,
                String sourceContentType,
                Long sourceSizeBytes,
                List<ClipDTO> clips,
                Integer actualDuration) {

        public ProjectCompletionDTO(
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
                this(
                                status,
                                error,
                                failedStage,
                                retryable,
                                thumbnailUrl,
                                thumbnailStorageUri,
                                thumbnailBucket,
                                thumbnailObjectPath,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                clips,
                                actualDuration);
        }

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
