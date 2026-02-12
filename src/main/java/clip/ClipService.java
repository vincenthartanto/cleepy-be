package clip;

import java.util.List;
import java.util.UUID;

import clip.dto.ClipRequest;
import common.dto.request.SpecificationRequest;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import project.ProjectRepository;

@ApplicationScoped
public class ClipService {

    @Inject
    ClipRepository clipRepository;
    
    @Inject
    ProjectRepository projectRepository;
    
    @Inject
    ClipMapper clipMapper;
    
    public Uni<List<Clip>> getClips(SpecificationRequest request) {
        return clipRepository.findByTitleLike(request);
    }
    
    @WithTransaction
    public Uni<Clip> createClip(ClipRequest request) {
        return projectRepository.findById(UUID.fromString(request.projectId()))
            .chain(project -> {
                if (project == null) {
                    return Uni.createFrom().failure(new IllegalArgumentException("Project not found"));
                }
                
                Clip clip = clipMapper.toEntity(request, project);
                return clipRepository.persist(clip);
            });
    }
}
