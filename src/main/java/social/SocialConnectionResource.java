package social;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import io.quarkus.security.Authenticated;
import social.dto.AuthorizeUrlResponse;
import social.dto.ConnectionPublishOptionsResponse;
import social.dto.SocialConnectionResponse;

@Path("/social/connections")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class SocialConnectionResource {

    @ConfigProperty(name = "social.oauth.frontend-base-url", defaultValue = "http://localhost:3000")
    String frontendBaseUrl;

    @Inject
    SocialConnectionService socialConnectionService;

    @Inject
    JsonWebToken jwt;

    @GET
    public Uni<List<SocialConnectionResponse>> listConnections() {
        return socialConnectionService.listConnections(getAuthenticatedUserId());
    }

    @POST
    @Path("/{platform}/start")
    public AuthorizeUrlResponse startConnection(@PathParam("platform") String platform) {
        return socialConnectionService.startConnection(getAuthenticatedUserId(), parsePlatform(platform));
    }

    @GET
    @Path("/{connectionId}/publish-options")
    public Uni<ConnectionPublishOptionsResponse> getPublishOptions(@PathParam("connectionId") UUID connectionId) {
        return socialConnectionService.getPublishOptions(getAuthenticatedUserId(), connectionId);
    }

    @DELETE
    @Path("/{connectionId}")
    public Uni<Response> disconnect(@PathParam("connectionId") UUID connectionId) {
        return socialConnectionService.disconnect(getAuthenticatedUserId(), connectionId)
                .replaceWith(Response.noContent().build());
    }

    @GET
    @PermitAll
    @Path("/{platform}/callback")
    public Uni<Response> callback(
            @PathParam("platform") String platform,
            @QueryParam("code") String code,
            @QueryParam("state") String state,
            @QueryParam("error") String error) {
        SocialPlatform socialPlatform = parsePlatform(platform);
        if (error != null && !error.isBlank()) {
            return Uni.createFrom().item(Response.seeOther(URI.create(
                    frontendBaseUrl + "/app/settings/integrations?platform=" + socialPlatform.name().toLowerCase()
                            + "&status=error"))
                    .build());
        }
        if (code == null || code.isBlank()) {
            throw new BadRequestException("OAuth code is required");
        }
        return socialConnectionService.handleCallback(socialPlatform, code, state)
                .map(uri -> Response.seeOther(uri).build());
    }

    private String getAuthenticatedUserId() {
        String userId = jwt.getSubject();
        if (userId == null || userId.isBlank()) {
            throw new BadRequestException("JWT subject (user ID) is required");
        }
        return userId;
    }

    private SocialPlatform parsePlatform(String platform) {
        try {
            return SocialPlatform.fromPath(platform);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unsupported social platform: " + platform);
        }
    }
}
