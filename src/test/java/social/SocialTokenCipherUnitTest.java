package social;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SocialTokenCipherUnitTest {

    private SocialTokenCipher socialTokenCipher;

    @BeforeEach
    void setUp() {
        socialTokenCipher = new SocialTokenCipher();
        socialTokenCipher.base64Key = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
        socialTokenCipher.init();
    }

    @Test
    void encryptAndDecrypt_shouldRoundTripToken() {
        String encrypted = socialTokenCipher.encrypt("refresh-token-value");

        assertEquals("refresh-token-value", socialTokenCipher.decrypt(encrypted));
    }

    @Test
    void encrypt_shouldReturnNullForBlankValues() {
        assertNull(socialTokenCipher.encrypt(null));
        assertNull(socialTokenCipher.encrypt(" "));
    }
}
