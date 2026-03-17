package social;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.BadRequestException;

@ApplicationScoped
public class SocialAuthStateService {

    private static final long STATE_TTL_SECONDS = 600;

    @ConfigProperty(name = "social.oauth.state-secret", defaultValue = "cleepy-social-state-secret")
    String stateSecret;

    public String create(String userId, SocialPlatform platform) {
        long expiresAt = Instant.now().plusSeconds(STATE_TTL_SECONDS).toEpochMilli();
        String payload = String.join("|", userId, platform.name(), String.valueOf(expiresAt), UUID.randomUUID().toString());
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(hmac(payload));
        return encodedPayload + "." + signature;
    }

    public String verify(String state, SocialPlatform expectedPlatform) {
        if (state == null || state.isBlank() || !state.contains(".")) {
            throw new BadRequestException("Invalid OAuth state");
        }

        String[] parts = state.split("\\.", 2);
        String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        String actualSignature = Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(payload));
        if (!actualSignature.equals(parts[1])) {
            throw new BadRequestException("Invalid OAuth signature");
        }

        String[] payloadParts = payload.split("\\|", 4);
        if (payloadParts.length < 3) {
            throw new BadRequestException("Malformed OAuth state");
        }

        SocialPlatform platform = SocialPlatform.valueOf(payloadParts[1]);
        if (platform != expectedPlatform) {
            throw new BadRequestException("OAuth platform mismatch");
        }

        long expiresAt = Long.parseLong(payloadParts[2]);
        if (Instant.now().toEpochMilli() > expiresAt) {
            throw new BadRequestException("OAuth state expired");
        }

        return payloadParts[0];
    }

    private byte[] hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(stateSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to sign OAuth state", e);
        }
    }
}
