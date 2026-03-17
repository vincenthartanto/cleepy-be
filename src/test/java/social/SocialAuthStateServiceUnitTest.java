package social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.ws.rs.BadRequestException;

class SocialAuthStateServiceUnitTest {

    private SocialAuthStateService authStateService;

    @BeforeEach
    void setUp() {
        authStateService = new SocialAuthStateService();
        authStateService.stateSecret = "unit-test-secret";
    }

    @Test
    void createAndVerify_shouldReturnOriginalUserId() {
        String state = authStateService.create("user-123", SocialPlatform.TIKTOK);

        assertEquals("user-123", authStateService.verify(state, SocialPlatform.TIKTOK));
    }

    @Test
    void verify_shouldRejectPlatformMismatch() {
        String state = authStateService.create("user-123", SocialPlatform.TIKTOK);

        assertThrows(BadRequestException.class, () -> authStateService.verify(state, SocialPlatform.YOUTUBE));
    }
}
