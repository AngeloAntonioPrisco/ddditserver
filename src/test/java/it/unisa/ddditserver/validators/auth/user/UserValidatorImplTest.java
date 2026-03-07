package it.unisa.ddditserver.validators.auth.user;

import it.unisa.ddditserver.db.gremlin.auth.GremlinAuthRepository;
import it.unisa.ddditserver.subsystems.auth.dto.UserDTO;
import it.unisa.ddditserver.subsystems.auth.exceptions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserValidatorImplTest {

    @Mock
    private GremlinAuthRepository gremlinAuthRepository;

    private UserValidatorImpl userValidator;

    @BeforeEach
    void setUp() {
        userValidator = new UserValidatorImpl(gremlinAuthRepository);
    }

    @ParameterizedTest(name = "Username [{0}] should be valid: {1}")
    @CsvSource({
            "angelo_123, true",
            "ab,          false", // Too short (min 3)
            "abc,         true",  // Boundary min
            "a_very_long_username_that_is_invalid, false", // Too long (max 30)
            "user!name,   false", // Invalid special char
            "'',          false"  // Empty
    })
    void testIsValidUsername(String username, boolean expected) {
        assertEquals(expected, userValidator.isValidUsername(username));
    }

    @ParameterizedTest(name = "Password [{0}] should be valid: {1}")
    @CsvSource({
            "Password123!, true",
            "pass123!,     false", // No uppercase
            "PASS123!,     false", // No lowercase
            "Password!,    false", // No digit
            "Password123,  false", // No special char
            "P1a!,         false"  // Too short (min 8)
    })
    void testIsValidPassword(String password, boolean expected) {
        assertEquals(expected, userValidator.isValidPassword(password));
    }


    static Stream<Arguments> existenceProvider() {
        return Stream.of(
                // exists flag, repo response, expected exception
                Arguments.of(true,  true,  null),                   // Case: check if exists, and it does (OK)
                Arguments.of(true,  false, UserNotFoundException.class), // Case: check if exists, but missing (Error)
                Arguments.of(false, false, null),                   // Case: check if new, and it is (OK)
                Arguments.of(false, true,  ExistingUserException.class)  // Case: check if new, but taken (Error)
        );
    }

    @ParameterizedTest(name = "Existence check: expectedExist={0}, foundInDB={1}")
    @MethodSource("existenceProvider")
    void testValidateExistence(boolean expectedToExist, boolean foundInDB, Class<? extends Throwable> expectedEx) {
        UserValidationDTO dto = new UserValidationDTO("mario_rossi", null);
        when(gremlinAuthRepository.existsByUser(any(UserDTO.class))).thenReturn(foundInDB);

        if (expectedEx != null) {
            assertThrows(expectedEx, () -> userValidator.validateExistence(dto, expectedToExist));
        } else {
            assertDoesNotThrow(() -> userValidator.validateExistence(dto, expectedToExist));
        }
    }

    @Test
    void testValidateMatchingPasswords_Success() {
        String rawPassword = "Password123!";
        String encodedPassword = new BCryptPasswordEncoder().encode(rawPassword);

        UserValidationDTO dto = new UserValidationDTO("mario", rawPassword);
        UserDTO retrievedUser = new UserDTO("mario", encodedPassword);

        when(gremlinAuthRepository.findByUser(any(UserDTO.class))).thenReturn(retrievedUser);

        assertDoesNotThrow(() -> userValidator.validateMatchingPasswords(dto));
    }

    @Test
    void testValidateMatchingPasswords_Mismatch() {
        String rawPassword = "Password123!";
        String wrongPassword = "WrongPassword123!";
        String encodedPassword = new BCryptPasswordEncoder().encode(rawPassword);

        UserValidationDTO dto = new UserValidationDTO("mario", wrongPassword);
        UserDTO retrievedUser = new UserDTO("mario", encodedPassword);

        when(gremlinAuthRepository.findByUser(any(UserDTO.class))).thenReturn(retrievedUser);

        assertThrows(PasswordsMismatchException.class, () -> userValidator.validateMatchingPasswords(dto));
    }
}