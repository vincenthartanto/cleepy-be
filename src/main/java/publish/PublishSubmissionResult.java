package publish;

record PublishSubmissionResult(
        PublishJobStatus status,
        String providerPublishId,
        String providerVideoId,
        String providerUrl,
        String providerStatus) {
}
