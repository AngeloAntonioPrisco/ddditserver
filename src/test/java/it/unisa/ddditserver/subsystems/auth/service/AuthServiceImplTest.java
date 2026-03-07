package it.unisa.ddditserver.subsystems.auth.service;

import it.unisa.ddditserver.db.cosmos.auth.CosmosAuthRepository;
import it.unisa.ddditserver.db.gremlin.auth.GremlinAuthRepository;
import it.unisa.ddditserver.subsystems.auth.dto.UserDTO;
import it.unisa.ddditserver.subsystems.auth.exceptions.AuthException;
import it.unisa.ddditserver.subsystems.auth.exceptions.LoggedUserException;
import it.unisa.ddditserver.subsystems.auth.exceptions.NotLoggedUserException;
import it.unisa.ddditserver.validators.auth.JWT.JWTokenValidator;
import it.unisa.ddditserver.validators.auth.user.UserValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private GremlinAuthRepository gremlinAuthRepository;
    @Mock private CosmosAuthRepository cosmosAuthRepository;
    @Mock private JWTokenValidator jwtTokenValidator;
    @Mock private UserValidator userValidator;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(gremlinAuthRepository, cosmosAuthRepository, jwtTokenValidator, userValidator);
        String secret = Base64.getEncoder().encodeToString("12345678901234567890123456789012".getBytes(StandardCharsets.UTF_8));
        authService.jwtSecretBase64 = secret;
        authService.init();
    }

    static Stream<Arguments> signupProvider() {
        return Stream.of(
                // Case 1: Success (Not logged, valid data, unique user, DB ok)
                Arguments.of(false, false, false, false, null),
                // Case 2: Already Logged (Error Partition)
                Arguments.of(true, false, false, false, LoggedUserException.class),
                // Case 3: Malformed Credentials
                Arguments.of(false, true, false, false, RuntimeException.class),
                // Case 4: Duplicate Username (Boundary Case)
                Arguments.of(false, false, true, false, RuntimeException.class),
                // Case 5: Database failure during save
                Arguments.of(false, false, false, true, AuthException.class)
        );
    }

    @ParameterizedTest(name = "Signup Partition - AlreadyLogged: {0}, Malformed: {1}, Exists: {2}, DBError: {3}")
    @MethodSource("signupProvider")
    void testSignup(boolean alreadyLogged, boolean malformed, boolean exists, boolean dbError, Class<? extends Throwable> expectedEx) {
        UserDTO dto = new UserDTO("mario", "password123");
        String token = "some_token";

        when(jwtTokenValidator.isTokenValid(token)).thenReturn(alreadyLogged ? "mario" : null);

        if (!alreadyLogged) {
            if (malformed) {
                doThrow(new RuntimeException()).when(userValidator).validateUser(any());
            } else if (exists) {
                doThrow(new RuntimeException()).when(userValidator).validateExistence(any(), eq(false));
            } else if (dbError) {
                doThrow(new RuntimeException("Gremlin down")).when(gremlinAuthRepository).saveUser(any());
            }
        }

        if (expectedEx != null) {
            assertThrows(expectedEx, () -> authService.signup(dto, token));
        } else {
            ResponseEntity<Map<String, String>> response = authService.signup(dto, token);
            assertNotNull(response.getBody().get("token"));
            verify(gremlinAuthRepository).saveUser(any());
        }
    }

    static Stream<Arguments> loginProvider() {
        return Stream.of(
                // Case 1: Successful Login
                Arguments.of(false, false, false, null),
                // Case 2: Already Logged In
                Arguments.of(true, false, false, LoggedUserException.class),
                // Case 3: User Not Found (Existence check fails)
                Arguments.of(false, true, false, RuntimeException.class),
                // Case 4: Wrong Password (Matching check fails)
                Arguments.of(false, false, true, RuntimeException.class)
        );
    }

    @ParameterizedTest(name = "Login Partition - AlreadyLogged: {0}, UserNotFound: {1}, WrongPass: {2}")
    @MethodSource("loginProvider")
    void testLogin(boolean alreadyLogged, boolean userNotFound, boolean wrongPass, Class<? extends Throwable> expectedEx) {
        UserDTO dto = new UserDTO("mario", "pass");
        String token = "token";

        when(jwtTokenValidator.isTokenValid(token)).thenReturn(alreadyLogged ? "mario" : null);

        if (!alreadyLogged) {
            if (userNotFound) {
                doThrow(new RuntimeException()).when(userValidator).validateExistence(any(), eq(true));
            } else if (wrongPass) {
                doThrow(new RuntimeException()).when(userValidator).validateMatchingPasswords(any());
            }
        }

        if (expectedEx != null) {
            assertThrows(expectedEx, () -> authService.login(dto, token));
        } else {
            ResponseEntity<Map<String, String>> response = authService.login(dto, token);
            assertNotNull(response.getBody().get("token"));
            assertEquals("User mario logged in successfully", response.getBody().get("message"));
        }
    }

    static Stream<Arguments> logoutProvider() {
        return Stream.of(
                // Case 1: Valid Logout
                Arguments.of(true, false, null),
                // Case 2: No session found (Boundary)
                Arguments.of(false, false, NotLoggedUserException.class),
                // Case 3: Blacklist DB fails
                Arguments.of(true, true, AuthException.class)
        );
    }

    @ParameterizedTest(name = "Logout Partition - ValidToken: {0}, BlacklistError: {1}")
    @MethodSource("logoutProvider")
    void testLogout(boolean tokenValid, boolean dbError, Class<? extends Throwable> expectedEx) {
        String token = "active_token";
        when(jwtTokenValidator.isTokenValid(token)).thenReturn(tokenValid ? "mario" : null);

        if (tokenValid && dbError) {
            doThrow(new RuntimeException("Cosmos down")).when(cosmosAuthRepository).blacklistToken(token);
        }

        if (expectedEx != null) {
            assertThrows(expectedEx, () -> authService.logout(token));
        } else {
            ResponseEntity<Map<String, String>> response = authService.logout(token);
            assertTrue(response.getBody().get("message").contains("successfully"));
            verify(cosmosAuthRepository).blacklistToken(token);
        }
    }
}