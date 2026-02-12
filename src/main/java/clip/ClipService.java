package clip;

import java.util.List;

import common.dto.request.SpecificationRequest;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ClipService {

    @Inject
    ClipRepository clipRepository;
    
    public Uni<List<Clip>> getClips(SpecificationRequest request) {
        return  clipRepository.findByTitleLike(request);
    }
}
