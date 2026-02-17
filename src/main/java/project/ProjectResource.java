package project;

import java.util.UUID;

import common.dto.request.SpecificationRequest;
import common.dto.response.PagedResponse;
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
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import project.dto.ProjectCompletionDTO;
import project.dto.ProjectRequest;

@Path("/project")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class ProjectResource {

    @Inject
    ProjectService projectService;

    @Inject
    JsonWebToken jwt;

    @GET
    public Uni<PagedResponse<Project>> getProjects(@BeanParam SpecificationRequest request) {
        String userId = getAuthenticatedUserId();
        return projectService.getProjects(request, userId);
    }

    @GET
    @Path("/{id}")
    public Uni<Project> getProjectById(@PathParam("id") UUID id) {
        String userId = getAuthenticatedUserId();
        return projectService.getProjectById(id, userId);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Project> createProject(ProjectRequest request) {
        String userId = getAuthenticatedUserId();
        return projectService.createProject(userId, request);
    }

    @POST
    @Path("/{id}/complete")
    @Consumes(MediaType.APPLICATION_JSON)
    @PermitAll
    public Uni<Response> onProjectComplete(
            @PathParam("id") UUID id,
            ProjectCompletionDTO completion) {
        return projectService.handleCompletion(id, completion)
                .map(v -> Response.ok().build());
    }

    private String getAuthenticatedUserId() {
        String userId = jwt.getSubject();
        if (userId == null || userId.isBlank()) {
            throw new BadRequestException("JWT subject (user ID) is required");
        }
        return userId;
    }
}
