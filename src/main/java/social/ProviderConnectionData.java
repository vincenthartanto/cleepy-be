package social;

import java.time.LocalDateTime;

record ProviderConnectionData(
        String providerAccountId,
        String displayName,
        String accessToken,
        String refreshToken,
        String scopes,
        LocalDateTime tokenExpiresAt) {
}
