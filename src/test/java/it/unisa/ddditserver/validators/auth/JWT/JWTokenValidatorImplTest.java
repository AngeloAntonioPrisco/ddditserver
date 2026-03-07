package it.unisa.ddditserver.validators.auth.JWT;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import it.unisa.ddditserver.db.cosmos.auth.CosmosAuthRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import javax.crypto.SecretKey;
import java.lang.reflect.Field;
import java.util.Base64;
import java.util.Date;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JWTokenValidatorImplTest {

    @Mock private CosmosAuthRepository cosmosAuthRepository;

    private JWTokenValidatorImpl validator;
    private SecretKey key;

    @BeforeEach
    void setUp() throws Exception {
        key = Keys.secretKeyFor(SignatureAlgorithm.HS256);
        String base64Secret = Base64.getEncoder().encodeToString(key.getEncoded());

        validator = new JWTokenValidatorImpl(cosmosAuthRepository);

        Field field = JWTokenValidatorImpl.class.getDeclaredField("jwtSecretBase64");
        field.setAccessible(true);
        field.set(validator, base64Secret);

        validator.init();
    }

    static Stream<Arguments> isTokenValidProvider() {
        return Stream.of(
                // Case 1: Success path
                Arguments.of("VALID_USER", false, "mario"),
                // Case 2: Null token input (Boundary)
                Arguments.of(null, false, null),
                // Case 3: Token is in blacklist (Security check)
                Arguments.of("VALID_USER", true, null),
                // Case 4: Malformed JWT (Parsing error)
                Arguments.of("MALFORMED", false, null),
                // Case 5: Subject is blank (Validation error)
                Arguments.of("BLANK_SUBJECT", false, null),
                // Case 6: Subject is null (Validation error)
                Arguments.of("NULL_SUBJECT", false, null)
        );
    }

    @ParameterizedTest(name = "JWT Validate - Type: {0}, Blacklisted: {1}")
    @MethodSource("isTokenValidProvider")
    void testIsTokenValid(String tokenType, boolean isBlacklisted, String expectedUsername) {
        String token = generateTokenForScenario(tokenType);

        if (token != null) {
            when(cosmosAuthRepository.isTokenBlacklisted(token)).thenReturn(isBlacklisted);
        }

        String result = validator.isTokenValid(token);
        assertEquals(expectedUsername, result);
    }

    private String generateTokenForScenario(String type) {
        if (type == null) return null;
        if (type.equals("MALFORMED")) return "not.a.valid.jwt";

        String subject = switch (type) {
            case "BLANK_SUBJECT" -> "  ";
            case "NULL_SUBJECT" -> null;
            default -> "mario";
        };

        return Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(new Date())
                .signWith(key)
                .compact();
    }
}