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
public class GoogleSocialProvider implements SocialProvider {

    private static final String AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String CHANNEL_URL = "https://www.googleapis.com/youtube/v3/channels?part=snippet&mine=true";
    private static final String SCOPE = "https://www.googleapis.com/auth/youtube.upload";

    @ConfigProperty(name = "social.google.client-id", defaultValue = "")
    String clientId;

    @ConfigProperty(name = "social.google.client-secret", defaultValue = "")
    String clientSecret;

    @Inject
    ProviderHttpClient httpClient;

    @Override
    public SocialPlatform platform() {
        return SocialPlatform.YOUTUBE;
    }

    @Override
    public String buildAuthorizeUrl(String state, String callbackUrl) {
        return AUTH_URL
                + "?client_id=" + Urls.encode(clientId)
                + "&redirect_uri=" + Urls.encode(callbackUrl)
                + "&response_type=code"
                + "&scope=" + Urls.encode(SCOPE)
                + "&access_type=offline"
                + "&include_granted_scopes=true"
                + "&prompt=consent"
                + "&state=" + Urls.encode(state);
    }

    @Override
    public Uni<ProviderConnectionData> exchangeCode(String code, String callbackUrl) {
        return httpClient.postForm(TOKEN_URL, Map.of(
                "code", code,
                "client_id", clientId,
                "client_secret", clientSecret,
                "redirect_uri", callbackUrl,
                "grant_type", "authorization_code"), Map.of())
                .flatMap(token -> buildConnectionData(token, token.path("access_token").asText(), token.path("refresh_token").asText(null)));
    }

    @Override
    public Uni<ProviderConnectionData> refresh(SocialConnection connection, String refreshToken) {
        return httpClient.postForm(TOKEN_URL, Map.of(
                "client_id", clientId,
                "client_secret", clientSecret,
                "refresh_token", refreshToken,
                "grant_type", "refresh_token"), Map.of())
                .flatMap(token -> buildConnectionData(token, token.path("access_token").asText(), refreshToken));
    }

    @Override
    public Uni<ConnectionPublishOptionsResponse> getPublishOptions(SocialConnection connection, String accessToken) {
        return Uni.createFrom().item(new ConnectionPublishOptionsResponse(
                platform().name(),
                connection.displayName,
                List.of("private", "unlisted", "public"),
                null));
    }

    private Uni<ProviderConnectionData> buildConnectionData(JsonNode token, String accessToken, String refreshToken) {
        return httpClient.get(CHANNEL_URL, Map.of("Authorization", "Bearer " + accessToken))
                .map(channels -> {
                    JsonNode item = channels.path("items").isArray() && channels.path("items").size() > 0
                            ? channels.path("items").get(0)
                            : null;
                    if (item == null) {
                        throw new IllegalStateException("No YouTube channel found for this account.");
                    }

                    String channelId = item.path("id").asText();
                    String displayName = item.path("snippet").path("title").asText(channelId);
                    String scopes = token.path("scope").asText(SCOPE);
                    LocalDateTime expiresAt = token.path("expires_in").isNumber()
                            ? LocalDateTime.now().plusSeconds(token.path("expires_in").asLong())
                            : null;
                    return new ProviderConnectionData(channelId, displayName, accessToken, refreshToken, scopes, expiresAt);
                });
    }
}
