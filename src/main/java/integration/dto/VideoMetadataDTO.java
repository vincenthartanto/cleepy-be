package integration.dto;

public record VideoMetadataDTO(
        String normalizedUrl,
        String provider,
        String title,
        Integer duration,
        String thumbnail,
        Boolean ingestable,
        String failureCode,
        String failureMessage) {

    public VideoMetadataDTO(Integer duration) {
        this(null, null, null, duration, null, true, null, null);
    }
}
