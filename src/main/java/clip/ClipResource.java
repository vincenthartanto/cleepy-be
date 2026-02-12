package clip;

import java.util.List;

import common.dto.request.SpecificationRequest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/clips")

public class ClipResource {
    
    @Inject
    ClipService clipService;
    
    @GET
    @Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
    public Uni<List<Clip>> getClips(@BeanParam SpecificationRequest request) {
        return clipService.getClips(request);
    }
}
