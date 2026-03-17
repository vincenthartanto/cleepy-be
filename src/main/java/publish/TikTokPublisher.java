package publish;

import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.JsonNode;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import social.ProviderHttpClient;
import social.SocialConnectionService;
import social.SocialPlatform;
import social.Urls;

@ApplicationScoped
public class TikTokPublisher implements PublisherAdapter {

    private static final String INIT_URL = "https://open.tiktokapis.com/v2/post/publish/video/init/";
    private static final String STATUS_URL = "https://open.tiktokapis.com/v2/post/publish/status/fetch/";

    @ConfigProperty(name = "social.media.base-url", defaultValue = "http://localhost:8080/api")
    String mediaBaseUrl;

    @Inject
    ProviderHttpClient providerHttpClient;

    @Inject
    MediaProxyTokenService mediaProxyTokenService;

    @Inject
    SocialConnectionService socialConnectionService;

    @Override
    public SocialPlatform platform() {
        return SocialPlatform.TIKTOK;
    }

    @Override
    public Uni<PublishSubmissionResult> submit(PublishExecutionContext context) {
        String accessToken = socialConnectionService.decryptAccessToken(context.connection());
        String mediaUrl = mediaBaseUrl + "/publish/media/" + context.job().id + "?token="
                + Urls.encode(mediaProxyTokenService.createToken(context.job()));
        String title = fallback(context.job().requestedTitle, context.clip().title);
        String description = fallback(context.job().requestedDescription, context.clip().description);
        String privacyLevel = fallback(context.job().requestedPrivacyLevel, "SELF_ONLY");

        Map<String, Object> payload = Map.of(
                "post_info", Map.of(
                        "title", title,
                        "description", description == null ? "" : description,
                        "privacy_level", privacyLevel,
                        "disable_comment", false,
                        "disable_duet", false,
                        "disable_stitch", false),
                "source_info", Map.of(
                        "source", "PULL_FROM_URL",
                        "video_url", mediaUrl));

        return providerHttpClient.postJson(INIT_URL, payload, Map.of("Authorization", "Bearer " + accessToken))
                .map(response -> {
                    String publishId = readText(response, "data.publish_id", "data.publishId", "publish_id", "publishId");
                    if (publishId == null || publishId.isBlank()) {
                        throw new PublishFailure("TikTok did not return a publish id.", true);
                    }
                    return new PublishSubmissionResult(
                            PublishJobStatus.PROCESSING,
                            publishId,
                            null,
                            null,
                            "submitted");
                });
    }

    @Override
    public Uni<PublishStatusResult> refresh(PublishExecutionContext context) {
        if (context.job().providerPublishId == null || context.job().providerPublishId.isBlank()) {
            return Uni.createFrom().failure(new PublishFailure("TikTok publish id is missing.", false));
        }

        String accessToken = socialConnectionService.decryptAccessToken(context.connection());
        return providerHttpClient.postJson(STATUS_URL, Map.of("publish_id", context.job().providerPublishId),
                Map.of("Authorization", "Bearer " + accessToken))
                .map(response -> {
                    String providerStatus = readText(response,
                            "data.publish_status",
                            "data.status",
                            "data.status_code",
                            "status");
                    String normalized = providerStatus == null ? "" : providerStatus.toUpperCase();

                    if (normalized.contains("FAIL") || normalized.contains("ERROR") || normalized.contains("REJECT")) {
                        return new PublishStatusResult(PublishJobStatus.FAILED, providerStatus, context.job().providerUrl,
                                readText(response, "data.fail_reason", "data.error_message", "message"));
                    }
                    if (normalized.contains("PUBLISH") || normalized.contains("SUCCESS")) {
                        return new PublishStatusResult(PublishJobStatus.PUBLISHED, providerStatus,
                                context.job().providerUrl, null);
                    }
                    return new PublishStatusResult(PublishJobStatus.PROCESSING, providerStatus,
                            context.job().providerUrl, null);
                });
    }

    private String fallback(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred : fallback;
    }

    private String readText(JsonNode node, String... paths) {
        for (String path : paths) {
            JsonNode cursor = node;
            for (String segment : path.split("\\.")) {
                cursor = cursor.path(segment);
            }
            if (!cursor.isMissingNode() && !cursor.isNull() && !cursor.asText().isBlank()) {
                return cursor.asText();
            }
        }
        return null;
    }
}
