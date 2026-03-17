package publish;

import java.util.UUID;

import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PublishBatchRepository implements PanacheRepositoryBase<PublishBatch, UUID> {

    public Uni<PublishBatch> findOwned(UUID batchId, String userId) {
        return find("id = ?1 and userId = ?2", batchId, userId).firstResult();
    }
}
