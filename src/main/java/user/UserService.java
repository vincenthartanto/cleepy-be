package user;

import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import user.dto.TopUpRequest;
import user.dto.UpgradeRequest;

@ApplicationScoped
public class UserService {

    @Inject
    UserRepository userRepository;

    @WithTransaction
    public Uni<User> getOrCreateUser(String userId) {
        return userRepository.findById(userId)
                .onItem().ifNull().switchTo(() -> {
                    User newUser = new User();
                    newUser.id = userId;
                    newUser.planMode = PlanMode.FREE_TRIAL;
                    newUser.creditsRemaining = 3;
                    newUser.createdAt = java.time.LocalDateTime.now();
                    newUser.updatedAt = java.time.LocalDateTime.now();
                    return userRepository.persist(newUser);
                });
    }

    @WithTransaction
    public Uni<User> upgradePlan(String userId, UpgradeRequest request) {
        PlanMode newPlan = request.planMode();
        if (newPlan != PlanMode.STARTER && newPlan != PlanMode.PRO) {
            return Uni.createFrom().failure(new BadRequestException("Invalid plan mode. Options: STARTER, PRO."));
        }

        return userRepository.findById(userId)
                .onItem().ifNull().failWith(() -> new NotFoundException("User not found"))
                .flatMap(user -> {
                    if (newPlan == PlanMode.STARTER) {
                        // User cannot upgrade from PRO to STARTER
                        if (user.planMode == PlanMode.PRO) {
                            return Uni.createFrom()
                                    .failure(new BadRequestException("User is already on PRO plan."));
                        }
                        user.planMode = PlanMode.STARTER;
                        user.creditsRemaining = 5;
                    } else if (newPlan == PlanMode.PRO) {
                        // Upgrade to PRO gives additional 10 credits no matter if they have some or not
                        if (user.planMode == PlanMode.PRO) {
                            return Uni.createFrom()
                                    .failure(new BadRequestException("User is already on PRO plan."));
                        }
                        user.planMode = PlanMode.PRO;
                        user.creditsRemaining += 10;
                    }
                    return userRepository.persist(user);
                });
    }

    @WithTransaction
    public Uni<User> topUpCredits(String userId, TopUpRequest request) {
        if (request.credits() <= 0) {
            return Uni.createFrom().failure(new BadRequestException("Credits to top-up must be greater than 0."));
        }

        return userRepository.findById(userId)
                .onItem().ifNull().failWith(() -> new NotFoundException("User not found"))
                .flatMap(user -> {
                    if (user.planMode == PlanMode.FREE_TRIAL) {
                        return Uni.createFrom().failure(new BadRequestException(
                                "Top-ups are not allowed for FREE_TRIAL users. Please upgrade to STARTER or PRO."));
                    }
                    user.creditsRemaining += request.credits();
                    return userRepository.persist(user);
                });
    }

}
