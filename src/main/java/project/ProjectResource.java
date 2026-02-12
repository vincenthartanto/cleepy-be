package project;

import java.util.List;

import common.dto.request.SpecificationRequest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/projects")
@Produces(MediaType.APPLICATION_JSON)
public class ProjectResource {
    
    @Inject
    ProjectService projectService;
    
    @GET
    public Uni<List<Project>> getProjects(@BeanParam SpecificationRequest request) {
        return projectService.getProjects(request);
    }
}
