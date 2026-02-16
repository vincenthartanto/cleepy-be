package clip;

import java.util.List;

import clip.dto.ClipRequest;
import common.dto.request.SpecificationRequest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/clip")
@Produces(MediaType.APPLICATION_JSON)
public class ClipResource {
    
    @Inject
    ClipService clipService;
    
    @GET
    public Uni<List<Clip>> getClips(@BeanParam SpecificationRequest request) {
        return clipService.getClips(request);
    }
    
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Clip> createClip(ClipRequest request) {
        return clipService.createClip(request);
    }
}
