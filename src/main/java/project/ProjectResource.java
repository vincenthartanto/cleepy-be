package project;

import java.util.List;

import common.dto.request.SpecificationRequest;
import io.smallrye.mutiny.Uni;
import io.quarkus.security.Authenticated;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;
import project.dto.ProjectRequest;

@Path("/project")
@Produces(MediaType.APPLICATION_JSON)
public class ProjectResource {

    @Inject
    ProjectService projectService;

    @Inject
    JsonWebToken jwt;

    @GET
    @PermitAll
    public Uni<List<Project>> getProjects(@BeanParam SpecificationRequest request) {
        return projectService.getProjects(request);
    }

    @POST
    @Authenticated
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Project> createProject(ProjectRequest request) {
        String userId = jwt.getSubject();
        if (userId == null || userId.isBlank()) {
            throw new BadRequestException("JWT subject (user ID) is required");
        }
        return projectService.createProject(userId, request);
    }
}
