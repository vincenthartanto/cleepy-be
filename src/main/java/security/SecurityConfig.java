package security;

import java.util.HashSet;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;

import org.eclipse.microprofile.jwt.Claims;

import io.smallrye.jwt.build.Jwt;

@ApplicationScoped
public class SecurityConfig {
    
    /**
     * This method can be used to generate JWT tokens for testing purposes
     * In production, you'll use Firebase to generate tokens
     */
    @Produces
    @Named("testToken")
    public String generateTestToken() {
        Set<String> groups = new HashSet<>();
        groups.add("user");
        
        return Jwt.issuer("https://securetoken.google.com/cleepy-aa466")
                .upn("test@example.com")
                .groups(groups)
                .claim(Claims.email.name(), "test@example.com")
                .claim(Claims.email_verified.name(), true)
                .claim("user_id", "123456789")
                .sign();
    }
}
