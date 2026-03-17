package publish;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PublishJobRepository implements PanacheRepositoryBase<PublishJob, UUID> {

    public Uni<List<PublishJob>> findByBatchId(UUID batchId) {
        return find("batchId = ?1 order by createdAt asc", batchId).list();
    }

    public Uni<List<PublishJob>> findByProjectIdAndUserId(UUID projectId, String userId) {
        return find("projectId = ?1 and userId = ?2 order by createdAt desc", projectId, userId).list();
    }

    public Uni<List<PublishJob>> findRunnableJobs(int limit) {
        return find("(status = ?1 or (status = ?2 and nextAttemptAt <= ?3)) order by createdAt asc",
                PublishJobStatus.QUEUED.name(),
                PublishJobStatus.FAILED.name(),
                LocalDateTime.now())
                .page(Page.ofSize(limit))
                .list();
    }

    public Uni<List<PublishJob>> findProcessingJobs(int limit) {
        return find("status = ?1 order by updatedAt asc", PublishJobStatus.PROCESSING.name())
                .page(Page.ofSize(limit))
                .list();
    }
}
