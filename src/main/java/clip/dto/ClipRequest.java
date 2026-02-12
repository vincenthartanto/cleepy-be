package clip.dto;

import java.time.LocalTime;

public record ClipRequest(
    String projectId,
    String title,
    String description,
    String videoUrl,
    String thumbnailUrl,
    LocalTime startTime,
    LocalTime endTime,
    Integer viralScore,
    String analysisResult
) {}
