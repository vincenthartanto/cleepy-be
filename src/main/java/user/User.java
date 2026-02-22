package user;

import java.time.LocalDateTime;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User extends PanacheEntityBase {

    @Id
    public String id;

    public String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_mode")
    public PlanMode planMode = PlanMode.FREE_TRIAL;

    @Column(name = "credits_remaining")
    public int creditsRemaining = 3;

    @Column(name = "created_at")
    public LocalDateTime createdAt;

    @Column(name = "updated_at")
    public LocalDateTime updatedAt;

}
