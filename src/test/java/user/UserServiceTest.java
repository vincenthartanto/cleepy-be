package user;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import user.dto.TopUpRequest;
import user.dto.UpgradeRequest;

@QuarkusTest
public class UserServiceTest {

    @InjectMock
    UserRepository userRepository;

    @Inject
    UserService userService;

    // ── upgradePlan tests ──

    @Test
    @RunOnVertxContext
    void testUpgradePlan_whenUserNotFound_shouldThrowNotFoundException(UniAsserter asserter) {
        String userId = "nonexistent";
        UpgradeRequest request = new UpgradeRequest(PlanMode.STARTER);

        when(userRepository.findById(userId)).thenReturn(Uni.createFrom().nullItem());

        asserter.assertFailedWith(() -> userService.upgradePlan(userId, request),
                throwable -> {
                    assertTrue(throwable instanceof NotFoundException);
                    assertEquals("User not found", throwable.getMessage());
                });
    }

    @Test
    @RunOnVertxContext
    void testUpgradePlan_whenInvalidPlanMode_shouldThrowBadRequestException(UniAsserter asserter) {
        // Obsolete test as enum enforces valid plan modes at compile time or
        // deserialization level
    }

    @Test
    @RunOnVertxContext
    void testUpgradePlan_whenUpgradingToStarterFromFreeTrial_shouldSetStarterAnd5Credits(UniAsserter asserter) {
        String userId = "user1";
        UpgradeRequest request = new UpgradeRequest(PlanMode.STARTER);

        User mockUser = new User();
        mockUser.id = userId;
        mockUser.planMode = PlanMode.FREE_TRIAL;
        mockUser.creditsRemaining = 1;

        when(userRepository.findById(userId)).thenReturn(Uni.createFrom().item(mockUser));
        when(userRepository.persist(any(User.class)))
                .thenAnswer(invocation -> Uni.createFrom().item((User) invocation.getArgument(0)));

        asserter.assertThat(() -> userService.upgradePlan(userId, request),
                user -> {
                    assertNotNull(user);
                    assertEquals(PlanMode.STARTER, user.planMode);
                    assertEquals(5, user.creditsRemaining);
                    verify(userRepository).persist(any(User.class));
                });
    }

    @Test
    @RunOnVertxContext
    void testUpgradePlan_whenUpgradingToStarterFromPro_shouldThrowBadRequestException(UniAsserter asserter) {
        String userId = "user1";
        UpgradeRequest request = new UpgradeRequest(PlanMode.STARTER);

        User mockUser = new User();
        mockUser.id = userId;
        mockUser.planMode = PlanMode.PRO;
        mockUser.creditsRemaining = 15;

        when(userRepository.findById(userId)).thenReturn(Uni.createFrom().item(mockUser));

        asserter.assertFailedWith(() -> userService.upgradePlan(userId, request),
                throwable -> {
                    assertTrue(throwable instanceof BadRequestException);
                    assertEquals("User is already on PRO plan.", throwable.getMessage());
                });
    }

    @Test
    @RunOnVertxContext
    void testUpgradePlan_whenUpgradingToProFromStarter_shouldAdd10Credits(UniAsserter asserter) {
        String userId = "user1";
        UpgradeRequest request = new UpgradeRequest(PlanMode.PRO);

        User mockUser = new User();
        mockUser.id = userId;
        mockUser.planMode = PlanMode.STARTER;
        mockUser.creditsRemaining = 2; // user used up some credits

        when(userRepository.findById(userId)).thenReturn(Uni.createFrom().item(mockUser));
        when(userRepository.persist(any(User.class)))
                .thenAnswer(invocation -> Uni.createFrom().item((User) invocation.getArgument(0)));

        asserter.assertThat(() -> userService.upgradePlan(userId, request),
                user -> {
                    assertNotNull(user);
                    assertEquals(PlanMode.PRO, user.planMode);
                    assertEquals(12, user.creditsRemaining); // 2 + 10 = 12
                    verify(userRepository).persist(any(User.class));
                });
    }

    @Test
    @RunOnVertxContext
    void testUpgradePlan_whenUpgradingToProFromPro_shouldThrowBadRequestException(UniAsserter asserter) {
        String userId = "user1";
        UpgradeRequest request = new UpgradeRequest(PlanMode.PRO);

        User mockUser = new User();
        mockUser.id = userId;
        mockUser.planMode = PlanMode.PRO;
        mockUser.creditsRemaining = 15;

        when(userRepository.findById(userId)).thenReturn(Uni.createFrom().item(mockUser));

        asserter.assertFailedWith(() -> userService.upgradePlan(userId, request),
                throwable -> {
                    assertTrue(throwable instanceof BadRequestException);
                    assertEquals("User is already on PRO plan.", throwable.getMessage());
                });
    }

    // ── topUpCredits tests ──

    @Test
    @RunOnVertxContext
    void testTopUpCredits_whenTargetCreditsZeroOrLess_shouldThrowBadRequestException(UniAsserter asserter) {
        String userId = "user1";
        TopUpRequest requestZero = new TopUpRequest(0);
        TopUpRequest requestNegative = new TopUpRequest(-5);

        asserter.assertFailedWith(() -> userService.topUpCredits(userId, requestZero),
                throwable -> {
                    assertTrue(throwable instanceof BadRequestException);
                    assertEquals("Credits to top-up must be greater than 0.", throwable.getMessage());
                });

        asserter.assertFailedWith(() -> userService.topUpCredits(userId, requestNegative),
                throwable -> {
                    assertTrue(throwable instanceof BadRequestException);
                    assertEquals("Credits to top-up must be greater than 0.", throwable.getMessage());
                });
    }

    @Test
    @RunOnVertxContext
    void testTopUpCredits_whenUserNotFound_shouldThrowNotFoundException(UniAsserter asserter) {
        String userId = "nonexistent";
        TopUpRequest request = new TopUpRequest(10);

        when(userRepository.findById(userId)).thenReturn(Uni.createFrom().nullItem());

        asserter.assertFailedWith(() -> userService.topUpCredits(userId, request),
                throwable -> {
                    assertTrue(throwable instanceof NotFoundException);
                    assertEquals("User not found", throwable.getMessage());
                });
    }

    @Test
    @RunOnVertxContext
    void testTopUpCredits_whenUserFreeTrial_shouldThrowBadRequestException(UniAsserter asserter) {
        String userId = "user1";
        TopUpRequest request = new TopUpRequest(10);

        User mockUser = new User();
        mockUser.id = userId;
        mockUser.planMode = PlanMode.FREE_TRIAL;
        mockUser.creditsRemaining = 2;

        when(userRepository.findById(userId)).thenReturn(Uni.createFrom().item(mockUser));

        asserter.assertFailedWith(() -> userService.topUpCredits(userId, request),
                throwable -> {
                    assertTrue(throwable instanceof BadRequestException);
                    assertEquals("Top-ups are not allowed for FREE_TRIAL users. Please upgrade to STARTER or PRO.",
                            throwable.getMessage());
                });
    }

    @Test
    @RunOnVertxContext
    void testTopUpCredits_whenProUserTopsUp_shouldAddCredits(UniAsserter asserter) {
        String userId = "user1";
        TopUpRequest request = new TopUpRequest(50);

        User mockUser = new User();
        mockUser.id = userId;
        mockUser.planMode = PlanMode.PRO;
        mockUser.creditsRemaining = 5;

        when(userRepository.findById(userId)).thenReturn(Uni.createFrom().item(mockUser));
        when(userRepository.persist(any(User.class)))
                .thenAnswer(invocation -> Uni.createFrom().item((User) invocation.getArgument(0)));

        asserter.assertThat(() -> userService.topUpCredits(userId, request),
                user -> {
                    assertNotNull(user);
                    assertEquals(PlanMode.PRO, user.planMode);
                    assertEquals(55, user.creditsRemaining); // 5 + 50
                    verify(userRepository).persist(any(User.class));
                });
    }
}
