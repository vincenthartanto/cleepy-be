package clip;

import java.util.List;
import java.util.UUID;

import common.dto.request.SpecificationRequest;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ClipRepository implements PanacheRepositoryBase<Clip, UUID>{

    @WithSession
    public Uni<List<Clip>> findByTitleLike(SpecificationRequest request) {
        String searchPattern = (request.search == null || request.search.isBlank()) 
        ? "%" 
        : "%" + request.search + "%";
    
            return find("LOWER(title) LIKE LOWER(?1)", Sort.by("createdAt").descending(), searchPattern)
            .page(Page.of(request.page, request.size))
            .list();
    }
}
