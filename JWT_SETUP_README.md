# Firebase JWT Validation Setup

This project now has SmallRye JWT configured to validate Firebase JWT tokens.

## Configuration Details

- **Firebase Project ID**: cleepy-aa466
- **JWKS Endpoint**: `https://www.googleapis.com/service_accounts/v1/jwk/cleepy-aa466`
- **Issuer**: `https://securetoken.google.com/cleepy-aa466`

## Testing the Implementation

### 1. Start the Application

```bash
mvn quarkus:dev
```

### 2. Test Endpoints

#### Public Endpoint (No JWT Required)

```bash
curl http://localhost:8080/test/public
```

#### Secured Endpoint (Requires JWT)

```bash
# This will return 401 Unauthorized without a valid JWT
curl http://localhost:8080/test/secured
```

### 3. Testing with Firebase JWT

To test with a real Firebase JWT token:

1. Get a Firebase ID token from your client application
2. Include it in the Authorization header:

```bash
curl -H "Authorization: Bearer <YOUR_FIREBASE_JWT_TOKEN>" \
     http://localhost:8080/test/secured
```

### 4. Testing with Generated Token (Development Only)

For development testing, you can use the SecurityConfig to generate a test token:

```bash
# Get a test token (you'll need to implement an endpoint for this or use the SecurityConfig directly)
# Then use it to test:
curl -H "Authorization: Bearer <TEST_TOKEN>" \
     http://localhost:8080/test/user-info
```

## Protecting Your Endpoints

To protect endpoints, use these annotations:

- `@PermitAll` - Allows anyone to access (default)
- `@Authenticated` - Requires a valid JWT token
- `@RolesAllowed("user")` - Requires valid JWT and specific role

Example:

```java
@GET
@Authenticated
public Response securedMethod() {
    // This method requires a valid JWT
}
```

## JWT Claims Available

Once authenticated, you can inject these claims:

- `JsonWebToken jwt` - Full JWT token object
- `Principal principal` - User principal
- `@Claim(standard = Claims.email) String email` - Email from JWT
- Custom claims like `user_id` from Firebase

## Notes

- SmallRye JWT automatically validates:
  - Token signature using Firebase's public keys
  - Token expiration
  - Issuer (must match `https://securetoken.google.com/cleepy-aa466`)
  - Audience (must match cleepy-aa466)
- Public keys are fetched and cached automatically from the JWKS endpoint
- No manual validation code is needed in your endpoints
