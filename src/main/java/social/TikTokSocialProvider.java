package social;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.databind.JsonNode;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import social.dto.ConnectionPublishOptionsResponse;

@ApplicationScoped
public class TikTokSocialProvider implements SocialProvider {

    private static final String AUTH_URL = "https://www.tiktok.com/v2/auth/authorize/";
    private static final String TOKEN_URL = "https://open.tiktokapis.com/v2/oauth/token/";
    private static final String CREATOR_INFO_URL = "https://open.tiktokapis.com/v2/post/publish/creator_info/query/";
    private static final String SCOPE = "video.publish";

    @ConfigProperty(name = "social.tiktok.client-key", defaultValue = "")
    String clientKey;

    @ConfigProperty(name = "social.tiktok.client-secret", defaultValue = "")
    String clientSecret;

    @Inject
    ProviderHttpClient httpClient;

    @Override
    public SocialPlatform platform() {
        return SocialPlatform.TIKTOK;
    }

    @Override
    public String buildAuthorizeUrl(String state, String callbackUrl) {
        return AUTH_URL
                + "?client_key=" + Urls.encode(clientKey)
                + "&redirect_uri=" + Urls.encode(callbackUrl)
                + "&response_type=code"
                + "&scope=" + Urls.encode(SCOPE)
                + "&state=" + Urls.encode(state);
    }

    @Override
    public Uni<ProviderConnectionData> exchangeCode(String code, String callbackUrl) {
        return httpClient.postForm(TOKEN_URL, Map.of(
                "client_key", clientKey,
                "client_secret", clientSecret,
                "code", code,
                "grant_type", "authorization_code",
                "redirect_uri", callbackUrl), Map.of())
                .flatMap(token -> buildConnectionData(token, token.path("access_token").asText(),
                        token.path("refresh_token").asText(null)));
    }

    @Override
    public Uni<ProviderConnectionData> refresh(SocialConnection connection, String refreshToken) {
        return httpClient.postForm(TOKEN_URL, Map.of(
                "client_key", clientKey,
                "client_secret", clientSecret,
                "grant_type", "refresh_token",
                "refresh_token", refreshToken), Map.of())
                .flatMap(token -> buildConnectionData(token, token.path("access_token").asText(), refreshToken));
    }

    @Override
    public Uni<ConnectionPublishOptionsResponse> getPublishOptions(SocialConnection connection, String accessToken) {
        return creatorInfo(accessToken).map(info -> {
            JsonNode data = info.path("data");
            List<String> privacyLevels = data.path("privacy_level_options").isArray()
                    ? java.util.stream.StreamSupport.stream(data.path("privacy_level_options").spliterator(), false)
                            .map(JsonNode::asText)
                            .toList()
                    : List.of("SELF_ONLY");
            Integer maxDuration = data.path("max_video_post_duration_sec").isNumber()
                    ? data.path("max_video_post_duration_sec").asInt()
                    : null;
            String displayName = text(data, "creator_nickname", "creator_username", "display_name");
            return new ConnectionPublishOptionsResponse(
                    platform().name(),
                    displayName != null ? displayName : connection.displayName,
                    privacyLevels,
                    maxDuration);
        });
    }

    private Uni<ProviderConnectionData> buildConnectionData(JsonNode token, String accessToken, String refreshToken) {
        return creatorInfo(accessToken).map(info -> {
            JsonNode data = info.path("data");
            String providerAccountId = token.path("open_id").asText(text(data, "creator_username", "creator_id"));
            String displayName = text(data, "creator_nickname", "creator_username", "display_name");
            String scopes = token.path("scope").asText(SCOPE);
            LocalDateTime expiresAt = token.path("expires_in").isNumber()
                    ? LocalDateTime.now().plusSeconds(token.path("expires_in").asLong())
                    : null;
            return new ProviderConnectionData(
                    providerAccountId,
                    displayName != null ? displayName : providerAccountId,
                    accessToken,
                    refreshToken,
                    scopes,
                    expiresAt);
        });
    }

    private Uni<JsonNode> creatorInfo(String accessToken) {
        return httpClient.postJson(CREATOR_INFO_URL, Map.of(), Map.of("Authorization", "Bearer " + accessToken));
    }

    private String text(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode field = node.path(fieldName);
            if (!field.isMissingNode() && !field.isNull() && !field.asText().isBlank()) {
                return field.asText();
            }
        }
        return null;
    }
}
