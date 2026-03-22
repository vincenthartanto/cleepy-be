package clip;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import project.Project;

@Entity
@Table(name = "clips")
public class Clip extends PanacheEntityBase {

    @Id
    public UUID id;

    public String title;
    public String description;

    @Column(name = "video_url")
    public String videoUrl;

    @Column(name = "thumbnail_url")
    public String thumbnailUrl;

    @Column(name = "storage_provider")
    public String storageProvider;

    @Column(name = "video_storage_uri")
    public String videoStorageUri;

    @Column(name = "video_bucket")
    public String videoBucket;

    @Column(name = "video_object_path")
    public String videoObjectPath;

    @Column(name = "thumbnail_storage_uri")
    public String thumbnailStorageUri;

    @Column(name = "thumbnail_bucket")
    public String thumbnailBucket;

    @Column(name = "thumbnail_object_path")
    public String thumbnailObjectPath;

    @Column(name = "start_time")
    public LocalTime startTime;

    @Column(name = "end_time")
    public LocalTime endTime;

    @Column(name = "viral_score")
    public Integer viralScore;

    @Column(name = "analysis_result", columnDefinition = "TEXT")
    public String analysisResult;

    @CreationTimestamp
    @Column(name = "created_at")
    public LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    public LocalDateTime updatedAt;

    @ManyToOne
    @JoinColumn(name = "project_id", referencedColumnName = "id")
    public Project project;
}
