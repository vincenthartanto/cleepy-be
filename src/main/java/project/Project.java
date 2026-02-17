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

    @Column(name = "thumbnail_url")
    public String thumbnailUrl;

    @Column(name = "user_id")
    public String userId;

    @Column(name = "source_url")
    public String sourceUrl;

    @CreationTimestamp
    @Column(name = "created_at")
    public LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    public LocalDateTime updatedAt;

}
