package security;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
public class JWTValidationTest {

  @Test
  public void testPublicEndpoint() {
    given()
        .when().get("/api/test/public")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("message", is("This is a public endpoint"));
  }

  @Test
  public void testSecuredEndpointWithoutToken() {
    given()
        .when().get("/api/test/secured")
        .then()
        .statusCode(401);
  }

  @Test
  public void testSecuredEndpointWithInvalidToken() {
    given()
        .header("Authorization", "Bearer invalid.token.here")
        .when().get("/api/test/secured")
        .then()
        .statusCode(401);
  }
}
