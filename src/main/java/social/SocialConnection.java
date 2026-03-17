package social;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "social_connections")
public class SocialConnection extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @Column(name = "user_id")
    public String userId;

    public String platform;

    @Column(name = "provider_account_id")
    public String providerAccountId;

    @Column(name = "display_name")
    public String displayName;

    public String status;

    @Column(columnDefinition = "TEXT")
    public String scopes;

    @Column(name = "access_token_encrypted", columnDefinition = "TEXT")
    public String accessTokenEncrypted;

    @Column(name = "refresh_token_encrypted", columnDefinition = "TEXT")
    public String refreshTokenEncrypted;

    @Column(name = "token_expires_at")
    public LocalDateTime tokenExpiresAt;

    @CreationTimestamp
    @Column(name = "created_at")
    public LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    public LocalDateTime updatedAt;
}
