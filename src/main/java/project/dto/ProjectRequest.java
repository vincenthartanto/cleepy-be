package project.dto;

public record ProjectRequest(
        String title,
        String customPrompt,
        Integer durationSeconds,
        String sourceStorageUri,
        String sourceBucket,
        String sourceObjectPath,
        String sourceFileName,
        String sourceContentType,
        Long sourceSizeBytes) {
}
