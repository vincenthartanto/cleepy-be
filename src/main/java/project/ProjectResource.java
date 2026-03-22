package project;

import java.util.List;
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
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;
import project.dto.ProjectCompletionDTO;
import project.dto.ProjectClipResponse;
import project.dto.ProjectDetailsResponse;
import project.dto.ProjectRequest;
import project.dto.ProjectUploadRequest;

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
    public Uni<ProjectDetailsResponse> getProjectById(@PathParam("id") UUID id) {
        String userId = getAuthenticatedUserId();
        return projectService.getProjectById(id, userId)
                .map(ProjectDetailsResponse::from);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<Project> createProject(ProjectRequest request) {
        String userId = getAuthenticatedUserId();
        return projectService.createProject(userId, request);
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Uni<Project> createProject(ProjectUploadRequest request) {
        String userId = getAuthenticatedUserId();
        return projectService.createProjectFromUpload(userId, request);
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

    @POST
    @Path("/{id}/retry")
    public Uni<Response> retryProject(@PathParam("id") UUID id) {
        String userId = getAuthenticatedUserId();
        return projectService.retryProject(id, userId)
                .map(p -> Response.ok(p).build());
    }

    @GET
    @Path("/estimate-cost")
    public Uni<Response> estimateCost(@jakarta.ws.rs.QueryParam("url") String url) {
        return Uni.createFrom()
                .item(Response.status(Response.Status.GONE)
                        .entity("{\"error\": \"URL ingestion is disabled\"}")
                        .build());
    }

    @GET
    @Path("/{id}/clips")
    public Uni<List<ProjectClipResponse>> getProjectClips(@PathParam("id") UUID id) {
        String userId = getAuthenticatedUserId();
        return projectService.getProjectClips(id, userId)
                .map(clips -> clips.stream().map(ProjectClipResponse::from).toList());
    }

    @GET
    @Path("/{id}/media/source")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Uni<Response> streamProjectSource(
            @PathParam("id") UUID id,
            @HeaderParam("Range") String rangeHeader) {
        String userId = getAuthenticatedUserId();
        return projectService.streamProjectMedia(id, userId, ProjectService.ProjectMediaKind.SOURCE, rangeHeader);
    }

    @GET
    @Path("/{id}/media/thumbnail")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Uni<Response> streamProjectThumbnail(@PathParam("id") UUID id) {
        String userId = getAuthenticatedUserId();
        return projectService.streamProjectMedia(id, userId, ProjectService.ProjectMediaKind.THUMBNAIL, null);
    }

    @GET
    @Path("/{id}/clips/{clipId}/media/video")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Uni<Response> streamClipVideo(
            @PathParam("id") UUID id,
            @PathParam("clipId") UUID clipId,
            @HeaderParam("Range") String rangeHeader) {
        String userId = getAuthenticatedUserId();
        return projectService.streamClipMedia(id, clipId, userId, ProjectService.ClipMediaKind.VIDEO, rangeHeader);
    }

    @GET
    @Path("/{id}/clips/{clipId}/media/thumbnail")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Uni<Response> streamClipThumbnail(
            @PathParam("id") UUID id,
            @PathParam("clipId") UUID clipId) {
        String userId = getAuthenticatedUserId();
        return projectService.streamClipMedia(id, clipId, userId, ProjectService.ClipMediaKind.THUMBNAIL, null);
    }

    private String getAuthenticatedUserId() {
        String userId = jwt.getSubject();
        if (userId == null || userId.isBlank()) {
            throw new BadRequestException("JWT subject (user ID) is required");
        }
        return userId;
    }
}
