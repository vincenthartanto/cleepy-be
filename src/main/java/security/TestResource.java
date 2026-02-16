package security;

import java.security.Principal;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.jwt.Claim;
import org.eclipse.microprofile.jwt.Claims;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Path("/test")
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
public class TestResource {
    
    @Inject
    Principal principal;
    
    @Inject
    JsonWebToken jwt;
    
    @Inject
    @Claim(standard = Claims.email)
    String email;
    
    @GET
    @Path("/public")
    @PermitAll
    public Response publicEndpoint() {
        return Response.ok("{\"message\": \"This is a public endpoint\"}").build();
    }
    
    @GET
    @Path("/secured")
    @RolesAllowed("user")
    public Response securedEndpoint() {
        return Response.ok("{\"message\": \"This is a secured endpoint\", \"user\": \"" + principal.getName() + "\"}").build();
    }
    
    @GET
    @Path("/user-info")
    public Response userInfo() {
        StringBuilder info = new StringBuilder();
        info.append("{");
        info.append("\"email\": \"").append(email != null ? email : "").append("\",");
        info.append("\"name\": \"").append(principal.getName() != null ? principal.getName() : "").append("\",");
        info.append("\"user_id\": \"").append(jwt.getClaim("user_id") != null ? jwt.getClaim("user_id").toString() : "").append("\",");
        info.append("\"is_email_verified\": \"").append(jwt.getClaim("email_verified") != null ? jwt.getClaim("email_verified").toString() : "").append("\"");
        info.append("}");
        return Response.ok(info.toString()).build();
    }
}