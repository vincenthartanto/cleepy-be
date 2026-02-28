package user;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.SecurityContext;
import io.quarkus.security.Authenticated;
import user.dto.TopUpRequest;
import user.dto.UpgradeRequest;

@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
public class UserResource {

    @Inject
    UserService userService;

    @Path("/me")
    @GET
    public Uni<User> getMe(@Context SecurityContext securityContext) {
        String userId = securityContext.getUserPrincipal().getName();
        return userService.getOrCreateUser(userId);
    }

    @Path("/upgrade")
    @POST
    public Uni<User> upgradePlan(@Context SecurityContext securityContext, UpgradeRequest request) {
        String userId = securityContext.getUserPrincipal().getName();
        return userService.upgradePlan(userId, request);
    }

    @Path("/top-up")
    @POST
    public Uni<User> topUpCredits(@Context SecurityContext securityContext, TopUpRequest request) {
        String userId = securityContext.getUserPrincipal().getName();
        return userService.topUpCredits(userId, request);
    }
}
