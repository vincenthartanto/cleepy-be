package payment;

import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import user.PlanMode;
import user.User;
import user.UserRepository;

@Path("/webhooks/paddle")
public class PaddleWebhookResource {

    private static final Logger LOG = Logger.getLogger(PaddleWebhookResource.class);

    @Inject
    UserRepository userRepository;

    @POST
    @PermitAll
    @Consumes(MediaType.APPLICATION_JSON)
    @WithTransaction
    public Uni<Response> handleWebhook(JsonObject payload) {
        LOG.info("Received Paddle Webhook: " + payload.encode());

        String eventType = payload.getString("event_type");
        if (eventType == null) {
            return Uni.createFrom().item(Response.ok().build()); // Ignore but acknowledge
        }

        if (eventType.equals("subscription.created") || eventType.equals("subscription.updated")) {
            JsonObject data = payload.getJsonObject("data");
            if (data == null) {
                return Uni.createFrom().item(Response.ok().build());
            }

            // Map custom_data.userId to your User ID.
            // Requires passing userId in custom_data from the frontend during Paddle
            // checkout
            JsonObject customData = data.getJsonObject("custom_data");
            if (customData == null || !customData.containsKey("userId")) {
                LOG.error("No userId found in custom_data. Cannot process webhook.");
                return Uni.createFrom().item(Response.ok().build());
            }

            String userId = customData.getString("userId");
            String status = data.getString("status");

            if ("active".equals(status)) {
                return userRepository.findById(userId)
                        .onItem().ifNull().switchTo(() -> {
                            User newUser = new User();
                            newUser.id = userId;
                            // For a brand new user paying immediately
                            return userRepository.persist(newUser);
                        })
                        .flatMap(user -> {
                            user.planMode = PlanMode.PRO;
                            user.creditsRemaining = 100; // Set Pro limits here
                            return userRepository.persist(user);
                        })
                        .replaceWith(Response.ok().build());
            } else if ("canceled".equals(status) || "past_due".equals(status)) {
                return userRepository.findById(userId)
                        .onItem().ifNotNull().transformToUni(user -> {
                            user.planMode = PlanMode.STARTER;
                            user.creditsRemaining = Math.min(user.creditsRemaining, 10);
                            return userRepository.persist(user);
                        })
                        .replaceWith(Response.ok().build());
            }
        }

        return Uni.createFrom().item(Response.ok().build());
    }
}
