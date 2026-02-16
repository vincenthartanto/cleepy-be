package project;

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
public class ProjectRepository implements PanacheRepositoryBase<Project, UUID> {

    private static final String SEARCH_QUERY = "LOWER(title) LIKE LOWER(?1) AND userId = ?2";

    @WithSession
    public Uni<List<Project>> findByTitleLike(SpecificationRequest request, String userId) {
        String searchPattern = buildSearchPattern(request.search);
        return find(SEARCH_QUERY, Sort.by("createdAt").descending(), searchPattern, userId)
                .page(Page.of(request.page, request.size))
                .list();
    }

    @WithSession
    public Uni<Long> countByTitleLike(SpecificationRequest request, String userId) {
        String searchPattern = buildSearchPattern(request.search);
        return count(SEARCH_QUERY, searchPattern, userId);
    }

    private String buildSearchPattern(String search) {
        return (search == null || search.isBlank()) ? "%" : "%" + search + "%";
    }
}
