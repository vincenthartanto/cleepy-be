package publish;

import java.util.UUID;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import clip.ClipRepository;
import project.StorageService;
import project.StorageService.StoredObject;

@Path("/publish/media")
@PermitAll
public class PublishMediaResource {

    @Inject
    PublishJobRepository publishJobRepository;

    @Inject
    MediaProxyTokenService mediaProxyTokenService;

    @Inject
    StorageService storageService;

    @Inject
    ClipRepository clipRepository;

    @GET
    @Path("/{jobId}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Uni<Response> streamClip(@PathParam("jobId") UUID jobId, @QueryParam("token") String token) {
        return publishJobRepository.findById(jobId)
                .onItem().ifNull().failWith(() -> new NotFoundException("Publish job not found"))
                .flatMap(job -> {
                    mediaProxyTokenService.verify(job, token);
                    return clipRepository.findById(job.clipId)
                            .onItem().ifNull().failWith(() -> new NotFoundException("Clip not found"))
                            .map(clip -> {
                                StoredObject storedObject = storageService.readObject(clip.videoBucket, clip.videoObjectPath);
                                return Response.ok(storedObject.bytes(), storedObject.contentType())
                                        .header("Cache-Control", "private, max-age=300")
                                        .build();
                            });
                });
    }
}
