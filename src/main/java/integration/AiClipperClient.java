package integration;

import integration.dto.VideoMetadataDTO;
import integration.dto.VideoProcessRequest;
import integration.dto.VideoProcessResponse;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "ai-clipper")
public interface AiClipperClient {

    @POST
    @Path("/process")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<VideoProcessResponse> processVideo(VideoProcessRequest request);

    @GET
    @Path("/metadata")
    Uni<VideoMetadataDTO> getMetadata(@jakarta.ws.rs.QueryParam("url") String url);
}
