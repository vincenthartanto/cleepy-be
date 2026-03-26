package project.dto;

public record ProjectRequest(
        String title,
        String customPrompt,
        Integer durationSeconds,
        String sourceKind,
        String sourceOriginUrl,
        String sourceProvider,
        String sourceStorageUri,
        String sourceBucket,
        String sourceObjectPath,
        String sourceFileName,
        String sourceContentType,
        Long sourceSizeBytes) {

    public ProjectRequest(
            String title,
            String customPrompt,
            Integer durationSeconds,
            String sourceStorageUri,
            String sourceBucket,
            String sourceObjectPath,
            String sourceFileName,
            String sourceContentType,
            Long sourceSizeBytes) {
        this(
                title,
                customPrompt,
                durationSeconds,
                null,
                null,
                null,
                sourceStorageUri,
                sourceBucket,
                sourceObjectPath,
                sourceFileName,
                sourceContentType,
                sourceSizeBytes);
    }
}
