package project.dto;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

import clip.Clip;

public record ProjectClipResponse(
        UUID id,
        String title,
        String description,
        String videoUrl,
        String thumbnailUrl,
        LocalTime startTime,
        LocalTime endTime,
        Integer viralScore,
        String analysisResult,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static ProjectClipResponse from(Clip clip) {
        return new ProjectClipResponse(
                clip.id,
                clip.title,
                clip.description,
                clip.videoUrl,
                clip.thumbnailUrl,
                clip.startTime,
                clip.endTime,
                clip.viralScore,
                clip.analysisResult,
                clip.createdAt,
                clip.updatedAt);
    }
}
