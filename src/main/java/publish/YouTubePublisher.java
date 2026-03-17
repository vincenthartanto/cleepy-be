package publish;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import project.StorageService;
import project.StorageService.StoredObject;
import social.SocialConnectionService;
import social.SocialPlatform;

@ApplicationScoped
public class YouTubePublisher implements PublisherAdapter {

    private static final String RESUMABLE_URL = "https://www.googleapis.com/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status";

    @Inject
    StorageService storageService;

    @Inject
    SocialConnectionService socialConnectionService;

    @Inject
    ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    @Override
    public SocialPlatform platform() {
        return SocialPlatform.YOUTUBE;
    }

    @Override
    public Uni<PublishSubmissionResult> submit(PublishExecutionContext context) {
        return Uni.createFrom().item(() -> {
            try {
                StoredObject storedObject = storageService.readObject(context.clip().videoBucket, context.clip().videoObjectPath);
                String accessToken = socialConnectionService.decryptAccessToken(context.connection());
                String title = preferred(context.job().requestedTitle, context.clip().title, "Cleepy Clip");
                String description = preferred(context.job().requestedDescription, context.clip().description, "");
                String privacy = preferred(context.job().requestedPrivacyLevel, "private");

                Map<String, Object> metadata = Map.of(
                        "snippet", Map.of(
                                "title", title,
                                "description", description),
                        "status", Map.of(
                                "privacyStatus", privacy));

                HttpRequest metadataRequest = HttpRequest.newBuilder(URI.create(RESUMABLE_URL))
                        .timeout(Duration.ofMinutes(2))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Content-Type", "application/json; charset=UTF-8")
                        .header("X-Upload-Content-Length", String.valueOf(storedObject.bytes().length))
                        .header("X-Upload-Content-Type", storedObject.contentType())
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(metadata)))
                        .build();

                HttpResponse<String> metadataResponse = httpClient.send(metadataRequest, HttpResponse.BodyHandlers.ofString());
                if (metadataResponse.statusCode() < 200 || metadataResponse.statusCode() >= 300) {
                    throw new PublishFailure("YouTube upload session failed: " + metadataResponse.body(), true);
                }

                String uploadUrl = metadataResponse.headers().firstValue("Location")
                        .orElseThrow(() -> new PublishFailure("YouTube upload session did not return a Location header.", true));

                HttpRequest uploadRequest = HttpRequest.newBuilder(URI.create(uploadUrl))
                        .timeout(Duration.ofMinutes(5))
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Content-Type", storedObject.contentType())
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(storedObject.bytes()))
                        .build();

                HttpResponse<String> uploadResponse = httpClient.send(uploadRequest, HttpResponse.BodyHandlers.ofString());
                if (uploadResponse.statusCode() < 200 || uploadResponse.statusCode() >= 300) {
                    throw new PublishFailure("YouTube upload failed: " + uploadResponse.body(), true);
                }

                JsonNode responseJson = objectMapper.readTree(uploadResponse.body());
                String videoId = responseJson.path("id").asText(null);
                if (videoId == null || videoId.isBlank()) {
                    throw new PublishFailure("YouTube upload did not return a video id.", true);
                }

                return new PublishSubmissionResult(
                        PublishJobStatus.PUBLISHED,
                        null,
                        videoId,
                        "https://www.youtube.com/watch?v=" + videoId,
                        "uploaded");
            } catch (PublishFailure failure) {
                throw failure;
            } catch (Exception e) {
                throw new PublishFailure("YouTube publish failed: " + e.getMessage(), e, true);
            }
        }).runSubscriptionOn(Infrastructure.getDefaultExecutor());
    }

    @Override
    public Uni<PublishStatusResult> refresh(PublishExecutionContext context) {
        return Uni.createFrom().item(new PublishStatusResult(
                PublishJobStatus.PUBLISHED,
                context.job().providerStatus,
                context.job().providerUrl,
                null));
    }

    private String preferred(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
