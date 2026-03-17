package social;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.arc.All;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import social.dto.AuthorizeUrlResponse;
import social.dto.ConnectionPublishOptionsResponse;
import social.dto.SocialConnectionResponse;

@ApplicationScoped
public class SocialConnectionService {

    @ConfigProperty(name = "social.oauth.backend-base-url", defaultValue = "http://localhost:8080/api")
    String backendBaseUrl;

    @ConfigProperty(name = "social.oauth.frontend-base-url", defaultValue = "http://localhost:3000")
    String frontendBaseUrl;

    @Inject
    SocialConnectionRepository repository;

    @Inject
    SocialTokenCipher tokenCipher;

    @Inject
    SocialAuthStateService authStateService;

    @Inject
    @All
    List<SocialProvider> providers;

    public Uni<List<SocialConnectionResponse>> listConnections(String userId) {
        return repository.findByUserId(userId)
                .map(connections -> connections.stream()
                        .sorted(Comparator.comparing(connection -> connection.platform))
                        .map(this::toResponse)
                        .toList());
    }

    public AuthorizeUrlResponse startConnection(String userId, SocialPlatform platform) {
        SocialProvider provider = providerFor(platform);
        String state = authStateService.create(userId, platform);
        String callbackUrl = callbackUrl(platform);
        return new AuthorizeUrlResponse(provider.buildAuthorizeUrl(state, callbackUrl));
    }

    @WithTransaction
    public Uni<URI> handleCallback(SocialPlatform platform, String code, String state) {
        String userId = authStateService.verify(state, platform);
        SocialProvider provider = providerFor(platform);
        return provider.exchangeCode(code, callbackUrl(platform))
                .flatMap(connectionData -> repository.findByUserIdAndPlatform(userId, platform)
                        .flatMap(existing -> {
                            SocialConnection connection = existing != null ? existing : new SocialConnection();
                            connection.userId = userId;
                            connection.platform = platform.name();
                            connection.providerAccountId = connectionData.providerAccountId();
                            connection.displayName = connectionData.displayName();
                            connection.status = SocialConnectionStatus.CONNECTED.name();
                            connection.scopes = connectionData.scopes();
                            connection.accessTokenEncrypted = tokenCipher.encrypt(connectionData.accessToken());
                            connection.refreshTokenEncrypted = tokenCipher.encrypt(
                                    connectionData.refreshToken() != null ? connectionData.refreshToken()
                                            : existing != null ? tokenCipher.decrypt(existing.refreshTokenEncrypted) : null);
                            connection.tokenExpiresAt = connectionData.tokenExpiresAt();
                            return repository.persist(connection);
                        }))
                .replaceWith(URI.create(frontendBaseUrl + "/app/settings/integrations?platform="
                        + platform.name().toLowerCase() + "&status=success"));
    }

    @WithTransaction
    public Uni<Void> disconnect(String userId, UUID connectionId) {
        return repository.findOwned(connectionId, userId)
                .onItem().ifNull().failWith(() -> new NotFoundException("Connection not found"))
                .flatMap(repository::delete)
                .replaceWithVoid();
    }

    public Uni<ConnectionPublishOptionsResponse> getPublishOptions(String userId, UUID connectionId) {
        return getOperationalConnection(connectionId, userId)
                .flatMap(connection -> providerFor(SocialPlatform.valueOf(connection.platform))
                        .getPublishOptions(connection, decryptAccessToken(connection)));
    }

    public Uni<SocialConnection> getOperationalConnection(UUID connectionId, String userId) {
        return repository.findOwned(connectionId, userId)
                .onItem().ifNull().failWith(() -> new NotFoundException("Connection not found"))
                .flatMap(this::refreshIfNeeded);
    }

    @WithTransaction
    public Uni<SocialConnection> refreshIfNeeded(SocialConnection connection) {
        if (connection == null) {
            return Uni.createFrom().failure(new NotFoundException("Connection not found"));
        }

        if (connection.tokenExpiresAt == null || connection.tokenExpiresAt.isAfter(LocalDateTime.now().plusMinutes(1))) {
            return Uni.createFrom().item(connection);
        }

        String refreshToken = tokenCipher.decrypt(connection.refreshTokenEncrypted);
        if (refreshToken == null || refreshToken.isBlank()) {
            connection.status = SocialConnectionStatus.EXPIRED.name();
            return repository.persist(connection)
                    .flatMap(ignored -> Uni.createFrom()
                            .failure(new ForbiddenException("Connection expired. Reconnect the account.")));
        }

        SocialProvider provider = providerFor(SocialPlatform.valueOf(connection.platform));
        return provider.refresh(connection, refreshToken)
                .flatMap(tokens -> {
                    connection.accessTokenEncrypted = tokenCipher.encrypt(tokens.accessToken());
                    connection.refreshTokenEncrypted = tokenCipher.encrypt(
                            tokens.refreshToken() != null ? tokens.refreshToken() : refreshToken);
                    connection.providerAccountId = tokens.providerAccountId() != null ? tokens.providerAccountId()
                            : connection.providerAccountId;
                    connection.displayName = tokens.displayName() != null ? tokens.displayName() : connection.displayName;
                    connection.status = SocialConnectionStatus.CONNECTED.name();
                    connection.scopes = tokens.scopes() != null ? tokens.scopes() : connection.scopes;
                    connection.tokenExpiresAt = tokens.tokenExpiresAt();
                    return repository.persist(connection);
                })
                .onFailure().recoverWithUni(t -> {
                    connection.status = SocialConnectionStatus.ERROR.name();
                    return repository.persist(connection)
                            .flatMap(ignored -> Uni.createFrom().failure(new ForbiddenException(
                                    "Failed to refresh social connection. Please reconnect the account.")));
                });
    }

    public String decryptAccessToken(SocialConnection connection) {
        return tokenCipher.decrypt(connection.accessTokenEncrypted);
    }

    private SocialProvider providerFor(SocialPlatform platform) {
        return providers.stream()
                .filter(candidate -> candidate.platform() == platform)
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Unsupported social platform: " + platform));
    }

    private String callbackUrl(SocialPlatform platform) {
        return backendBaseUrl + "/social/connections/" + platform.name().toLowerCase() + "/callback";
    }

    private SocialConnectionResponse toResponse(SocialConnection connection) {
        return new SocialConnectionResponse(
                connection.id,
                connection.platform,
                connection.displayName,
                connection.status,
                connection.scopes,
                connection.tokenExpiresAt,
                connection.createdAt,
                connection.updatedAt);
    }
}
