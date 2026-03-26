package project.dto;

import integration.dto.VideoMetadataDTO;

public record ProjectSourceEstimateResponse(
        String normalizedUrl,
        String provider,
        String title,
        Integer durationSeconds,
        int estimatedCost,
        boolean ingestable,
        String failureCode,
        String failureMessage) {

    public static ProjectSourceEstimateResponse from(VideoMetadataDTO metadata, int estimatedCost) {
        return new ProjectSourceEstimateResponse(
                metadata.normalizedUrl(),
                metadata.provider(),
                metadata.title(),
                metadata.duration(),
                estimatedCost,
                Boolean.TRUE.equals(metadata.ingestable()),
                metadata.failureCode(),
                metadata.failureMessage());
    }
}
