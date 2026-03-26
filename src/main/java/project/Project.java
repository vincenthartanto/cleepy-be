package project;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;

@Entity
@Table(name = "projects")
public class Project extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;

    public String title;
    public String status;

    @Column(name = "custom_prompt", columnDefinition = "TEXT")
    public String customPrompt;

    @Column(name = "thumbnail_url")
    public String thumbnailUrl;

    @Column(name = "thumbnail_storage_uri")
    public String thumbnailStorageUri;

    @Column(name = "thumbnail_bucket")
    public String thumbnailBucket;

    @Column(name = "thumbnail_object_path")
    public String thumbnailObjectPath;

    @Column(name = "user_id")
    public String userId;

    @Column(name = "source_url")
    public String sourceUrl;

    @Column(name = "source_kind")
    public String sourceKind;

    @Column(name = "source_origin_url")
    public String sourceOriginUrl;

    @Column(name = "source_provider")
    public String sourceProvider;

    @Column(name = "source_storage_uri")
    public String sourceStorageUri;

    @Column(name = "source_bucket")
    public String sourceBucket;

    @Column(name = "source_object_path")
    public String sourceObjectPath;

    @Column(name = "source_file_name")
    public String sourceFileName;

    @Column(name = "source_content_type")
    public String sourceContentType;

    @Column(name = "source_size_bytes")
    public Long sourceSizeBytes;

    @Column(name = "cost", columnDefinition = "integer default 1")
    public int cost;

    @Column(name = "duration_seconds")
    public Integer durationSeconds;

    @Column(name = "worker_retry_count", columnDefinition = "integer default 0")
    public int workerRetryCount;

    @Column(name = "last_failed_stage")
    public String lastFailedStage;

    @Column(name = "last_failure_reason", columnDefinition = "TEXT")
    public String lastFailureReason;

    @org.hibernate.annotations.Formula("(SELECT count(c.id) FROM clips c WHERE c.project_id = id)")
    public Integer clipsCount;

    @CreationTimestamp
    @Column(name = "created_at")
    public LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    public LocalDateTime updatedAt;

}
