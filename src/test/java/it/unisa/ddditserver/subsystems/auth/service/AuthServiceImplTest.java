package it.unisa.ddditserver.subsystems.auth.service;

import it.unisa.ddditserver.db.cosmos.auth.CosmosAuthRepository;
import it.unisa.ddditserver.db.gremlin.auth.GremlinAuthRepository;
import it.unisa.ddditserver.subsystems.auth.dto.UserDTO;
import it.unisa.ddditserver.subsystems.auth.exceptions.AuthException;
import it.unisa.ddditserver.subsystems.auth.exceptions.LoggedUserException;
import it.unisa.ddditserver.subsystems.auth.exceptions.NotLoggedUserException;
import it.unisa.ddditserver.validators.auth.JWT.JWTokenValidator;
import it.unisa.ddditserver.validators.auth.user.UserValidationDTO;
import it.unisa.ddditserver.validators.auth.user.UserValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.ArgumentCaptor;
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

    @Mock
    private GremlinAuthRepository gremlinAuthRepository;

    @Mock
    private CosmosAuthRepository cosmosAuthRepository;

    @Mock
    private JWTokenValidator jwtTokenValidator;

    @Mock
    private UserValidator userValidator;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(
                gremlinAuthRepository,
                cosmosAuthRepository,
                jwtTokenValidator,
                userValidator
        );

        // valid base64 secret, enough length for HMAC signing
        String secret = Base64.getEncoder().encodeToString(
                "12345678901234567890123456789012".getBytes(StandardCharsets.UTF_8)
        );
        authService.jwtSecretBase64 = secret;
        authService.init();
    }

    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------

    private static UserDTO user(String username, String password) {
        return new UserDTO(username, password);
    }

    // ============================================================
    // Category Partition: signup
    //
    // Categories:
    // - token already valid -> LoggedUserException
    // - validateUser: ok | throws
    // - validateExistence(false): ok | throws
    // - saveUser: ok | throws
    //
    // Notes:
    // - token generation succeeds because we initialize a valid secret
    // ============================================================
    static Stream<Arguments> signupCases() {
        return Stream.of(
                // alreadyLogged, validateUserThrows, validateExistenceThrows, saveThrows, expectedException
                Arguments.of(true,  false, false, false, LoggedUserException.class),
                Arguments.of(false, true,  false, false, RuntimeException.class),
                Arguments.of(false, false, true,  false, RuntimeException.class),
                Arguments.of(false, false, false, true,  AuthException.class),
                Arguments.of(false, false, false, false, null)
        );
    }

    @ParameterizedTest
    @MethodSource("signupCases")
    void signup_categoryPartition(boolean alreadyLogged,
                                  boolean validateUserThrows,
                                  boolean validateExistenceThrows,
                                  boolean saveThrows,
                                  Class<? extends Throwable> expectedException) {

        UserDTO userDTO = user("mario", "password123");

        if (alreadyLogged) {
            when(jwtTokenValidator.isTokenValid("token")).thenReturn("mario");
        } else {
            when(jwtTokenValidator.isTokenValid("token")).thenReturn(null);
        }

        if (validateUserThrows) {
            doThrow(new RuntimeException("invalid user"))
                    .when(userValidator).validateUser(any(UserValidationDTO.class));
        }

        if (validateExistenceThrows) {
            doThrow(new RuntimeException("user already exists"))
                    .when(userValidator).validateExistence(any(UserValidationDTO.class), eq(false));
        }

        if (saveThrows) {
            doThrow(new RuntimeException("db error"))
                    .when(gremlinAuthRepository).saveUser(any(UserDTO.class));
        }

        if (expectedException != null) {
            assertThrows(expectedException, () -> authService.signup(userDTO, "token"));
            return;
        }

        ResponseEntity<Map<String, String>> response = authService.signup(userDTO, "token");

        assertNotNull(response);
        assertNotNull(response.getBody());
        assertEquals("User mario registered successfully", response.getBody().get("message"));
        assertNotNull(response.getBody().get("token"));
        assertFalse(response.getBody().get("token").isBlank());

        verify(userValidator).validateUser(any(UserValidationDTO.class));
        verify(userValidator).validateExistence(any(UserValidationDTO.class), eq(false));

        ArgumentCaptor<UserDTO> captor = ArgumentCaptor.forClass(UserDTO.class);
        verify(gremlinAuthRepository).saveUser(captor.capture());

        UserDTO savedUser = captor.getValue();
        assertEquals("mario", savedUser.getUsername());
        assertNotEquals("password123", savedUser.getPassword()); // password must be hashed
        assertNotNull(savedUser.getPassword());
        assertFalse(savedUser.getPassword().isBlank());
    }

    // ============================================================
    // Category Partition: login
    //
    // Categories:
    // - token already valid -> LoggedUserException
    // - validateUser: ok | throws
    // - validateExistence(true): ok | throws
    // - validateMatchingPasswords: ok | throws
    // ============================================================
    static Stream<Arguments> loginCases() {
        return Stream.of(
                // alreadyLogged, validateUserThrows, validateExistenceThrows, validatePasswordThrows, expectedException
                Arguments.of(true,  false, false, false, LoggedUserException.class),
                Arguments.of(false, true,  false, false, RuntimeException.class),
                Arguments.of(false, false, true,  false, RuntimeException.class),
                Arguments.of(false, false, false, true,  RuntimeException.class),
                Arguments.of(false, false, false, false, null)
        );
    }

    @ParameterizedTest
    @MethodSource("loginCases")
    void login_categoryPartition(boolean alreadyLogged,
                                 boolean validateUserThrows,
                                 boolean validateExistenceThrows,
                                 boolean validatePasswordThrows,
                                 Class<? extends Throwable> expectedException) {

        UserDTO userDTO = user("mario", "password123");

        if (alreadyLogged) {
            when(jwtTokenValidator.isTokenValid("token")).thenReturn("mario");
        } else {
            when(jwtTokenValidator.isTokenValid("token")).thenReturn(null);
        }

        if (validateUserThrows) {
            doThrow(new RuntimeException("invalid user"))
                    .when(userValidator).validateUser(any(UserValidationDTO.class));
        }

        if (validateExistenceThrows) {
            doThrow(new RuntimeException("user not found"))
                    .when(userValidator).validateExistence(any(UserValidationDTO.class), eq(true));
        }

        if (validatePasswordThrows) {
            doThrow(new RuntimeException("wrong password"))
                    .when(userValidator).validateMatchingPasswords(any(UserValidationDTO.class));
        }

        if (expectedException != null) {
            assertThrows(expectedException, () -> authService.login(userDTO, "token"));
            return;
        }

        ResponseEntity<Map<String, String>> response = authService.login(userDTO, "token");

        assertNotNull(response);
        assertNotNull(response.getBody());
        assertEquals("User mario logged in successfully", response.getBody().get("message"));
        assertNotNull(response.getBody().get("token"));
        assertFalse(response.getBody().get("token").isBlank());

        verify(userValidator).validateUser(any(UserValidationDTO.class));
        verify(userValidator).validateExistence(any(UserValidationDTO.class), eq(true));
        verify(userValidator).validateMatchingPasswords(any(UserValidationDTO.class));
    }

    // ============================================================
    // Category Partition: logout
    //
    // Categories:
    // - token valid: yes | no
    // - blacklistToken: ok | throws
    // ============================================================
    static Stream<Arguments> logoutCases() {
        return Stream.of(
                // tokenValid, blacklistThrows, expectedException
                Arguments.of(false, false, NotLoggedUserException.class),
                Arguments.of(true,  true,  AuthException.class),
                Arguments.of(true,  false, null)
        );
    }

    @ParameterizedTest
    @MethodSource("logoutCases")
    void logout_categoryPartition(boolean tokenValid,
                                  boolean blacklistThrows,
                                  Class<? extends Throwable> expectedException) {

        if (tokenValid) {
            when(jwtTokenValidator.isTokenValid("token")).thenReturn("mario");
        } else {
            when(jwtTokenValidator.isTokenValid("token")).thenReturn(null);
        }

        if (blacklistThrows) {
            doThrow(new RuntimeException("blacklist failed"))
                    .when(cosmosAuthRepository).blacklistToken("token");
        }

        if (expectedException != null) {
            assertThrows(expectedException, () -> authService.logout("token"));
            return;
        }

        ResponseEntity<Map<String, String>> response = authService.logout("token");

        assertNotNull(response);
        assertNotNull(response.getBody());
        assertEquals("User mario logged out successfully, token will be blacklisted",
                response.getBody().get("message"));

        verify(cosmosAuthRepository).blacklistToken("token");
    }
}