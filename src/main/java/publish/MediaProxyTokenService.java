package publish;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ForbiddenException;

@ApplicationScoped
public class MediaProxyTokenService {

    @ConfigProperty(name = "social.oauth.state-secret", defaultValue = "cleepy-social-state-secret")
    String secret;

    public String createToken(PublishJob job) {
        long expiresAt = Instant.now().plusSeconds(3600).toEpochMilli();
        String payload = job.id + "|" + job.clipId + "|" + expiresAt;
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(sign(payload));
        return encodedPayload + "." + signature;
    }

    public void verify(PublishJob job, String token) {
        if (token == null || token.isBlank() || !token.contains(".")) {
            throw new ForbiddenException("Invalid media token");
        }

        String[] parts = token.split("\\.", 2);
        String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        String expectedSignature = Base64.getUrlEncoder().withoutPadding().encodeToString(sign(payload));
        if (!expectedSignature.equals(parts[1])) {
            throw new ForbiddenException("Invalid media signature");
        }

        String[] payloadParts = payload.split("\\|", 3);
        if (payloadParts.length != 3 || !payloadParts[0].equals(job.id.toString())
                || !payloadParts[1].equals(job.clipId.toString())) {
            throw new ForbiddenException("Invalid media token payload");
        }

        long expiresAt = Long.parseLong(payloadParts[2]);
        if (Instant.now().toEpochMilli() > expiresAt) {
            throw new ForbiddenException("Media token expired");
        }
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign media token", e);
        }
    }
}
