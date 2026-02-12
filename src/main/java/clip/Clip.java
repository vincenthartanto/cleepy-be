package clip;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Column;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import project.Project;

@Entity
@Table(name = "clips")
public class Clip extends PanacheEntityBase{

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    public UUID id;
    public String title;
    public String description;
    
    @Column(name = "video_url")
    public String videoUrl;
    
    @Column(name = "thumbnail_url")
    public String thumbnailUrl;
    
    @Column(name = "start_time")
    public LocalTime startTime;
    
    @Column(name = "end_time")
    public LocalTime endTime;
    
    @Column(name = "viral_score")
    public Integer viralScore;
    
    @Column(name = "analysis_result")
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
