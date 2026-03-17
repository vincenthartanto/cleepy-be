package social;

import social.dto.ConnectionPublishOptionsResponse;

import io.smallrye.mutiny.Uni;

public interface SocialProvider {

    SocialPlatform platform();

    String buildAuthorizeUrl(String state, String callbackUrl);

    Uni<ProviderConnectionData> exchangeCode(String code, String callbackUrl);

    Uni<ProviderConnectionData> refresh(SocialConnection connection, String refreshToken);

    Uni<ConnectionPublishOptionsResponse> getPublishOptions(SocialConnection connection, String accessToken);
}
