package project;

import java.util.List;

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
import project.dto.ProjectRequest;

@Path("/projects")
@Produces(MediaType.APPLICATION_JSON)
public class ProjectResource {
    
    @Inject
    ProjectService projectService;
    
    @GET
    public Uni<List<Project>> getProjects(@BeanParam SpecificationRequest request) {
        return projectService.getProjects(request);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Project> createProject(ProjectRequest request) {
        return projectService.createProject(request);
    }
}
