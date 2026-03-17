package publish;

record PublishStatusResult(
        PublishJobStatus status,
        String providerStatus,
        String providerUrl,
        String errorMessage) {
}
