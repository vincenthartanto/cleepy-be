package publish;

import java.util.List;
import java.util.UUID;

import org.eclipse.microprofile.jwt.JsonWebToken;

import io.smallrye.mutiny.Uni;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import publish.dto.PublishBatchRequest;
import publish.dto.PublishBatchResponse;
import publish.dto.PublishJobResponse;

@Path("/publish")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class PublishResource {

    @Inject
    PublishBatchService publishBatchService;

    @Inject
    JsonWebToken jwt;

    @POST
    @Path("/batches")
    public Uni<PublishBatchResponse> createBatch(PublishBatchRequest request) {
        return publishBatchService.createBatch(getAuthenticatedUserId(), request);
    }

    @GET
    @Path("/batches/{batchId}")
    public Uni<PublishBatchResponse> getBatch(@PathParam("batchId") UUID batchId) {
        return publishBatchService.getBatch(getAuthenticatedUserId(), batchId);
    }

    @GET
    @Path("/project/{projectId}/jobs")
    public Uni<List<PublishJobResponse>> getProjectJobs(@PathParam("projectId") UUID projectId) {
        return publishBatchService.getProjectJobs(getAuthenticatedUserId(), projectId);
    }

    private String getAuthenticatedUserId() {
        String userId = jwt.getSubject();
        if (userId == null || userId.isBlank()) {
            throw new BadRequestException("JWT subject (user ID) is required");
        }
        return userId;
    }
}
