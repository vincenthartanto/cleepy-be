package integration;

import integration.dto.VideoProcessRequest;
import integration.dto.VideoProcessResponse;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/process")
@RegisterRestClient(configKey = "ai-clipper")
public interface AiClipperClient {

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<VideoProcessResponse> processVideo(VideoProcessRequest request);
}
