package social;

import java.util.List;
import java.util.UUID;

import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SocialConnectionRepository implements PanacheRepositoryBase<SocialConnection, UUID> {

    public Uni<List<SocialConnection>> findByUserId(String userId) {
        return find("userId = ?1 order by createdAt asc", userId).list();
    }

    public Uni<SocialConnection> findByUserIdAndPlatform(String userId, SocialPlatform platform) {
        return find("userId = ?1 and platform = ?2", userId, platform.name()).firstResult();
    }

    public Uni<SocialConnection> findOwned(UUID id, String userId) {
        return find("id = ?1 and userId = ?2", id, userId).firstResult();
    }
}
