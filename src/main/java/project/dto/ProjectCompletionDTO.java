package project.dto;

import java.util.List;

public record ProjectCompletionDTO(
                String status,
                List<ClipDTO> clips) {

        public record ClipDTO(
                        String id,
                        String name,
                        String description,
                        String videoUrl,
                        String thumbnailUrl,
                        double startTime,
                        double endTime,
                        double viralityScore) {
        }
}
