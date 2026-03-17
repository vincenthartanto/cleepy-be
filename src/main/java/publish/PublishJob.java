package publish;

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
@Table(name = "publish_jobs")
public class PublishJob extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    @Column(name = "batch_id")
    public UUID batchId;

    @Column(name = "user_id")
    public String userId;

    @Column(name = "project_id")
    public UUID projectId;

    @Column(name = "clip_id")
    public UUID clipId;

    @Column(name = "connection_id")
    public UUID connectionId;

    public String platform;
    public String status;

    @Column(name = "requested_title")
    public String requestedTitle;

    @Column(name = "requested_description", columnDefinition = "TEXT")
    public String requestedDescription;

    @Column(name = "requested_privacy_level")
    public String requestedPrivacyLevel;

    @Column(name = "provider_publish_id")
    public String providerPublishId;

    @Column(name = "provider_video_id")
    public String providerVideoId;

    @Column(name = "provider_url")
    public String providerUrl;

    @Column(name = "provider_status")
    public String providerStatus;

    @Column(name = "error_message", columnDefinition = "TEXT")
    public String errorMessage;

    @Column(name = "retry_count")
    public int retryCount;

    @Column(name = "next_attempt_at")
    public LocalDateTime nextAttemptAt;

    @Column(name = "published_at")
    public LocalDateTime publishedAt;

    @CreationTimestamp
    @Column(name = "created_at")
    public LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    public LocalDateTime updatedAt;
}
