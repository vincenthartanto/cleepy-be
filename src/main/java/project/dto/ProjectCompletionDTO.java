package project.dto;

import java.util.List;

public record ProjectCompletionDTO(
                String status,
                String thumbnailUrl,
                String thumbnailStorageUri,
                String thumbnailBucket,
                String thumbnailObjectPath,
                List<ClipDTO> clips) {

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
