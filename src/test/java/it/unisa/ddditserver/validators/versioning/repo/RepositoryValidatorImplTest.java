package it.unisa.ddditserver.validators.versioning.repo;

import it.unisa.ddditserver.db.gremlin.versioning.repo.GremlinRepositoryRepository;
import it.unisa.ddditserver.subsystems.versioning.exceptions.repo.*;
import it.unisa.ddditserver.validators.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RepositoryValidatorImplTest {

    @Mock
    private GremlinRepositoryRepository gremlinService;

    private RepositoryValidatorImpl validator;

    @BeforeEach
    void setUp() {
        validator = new RepositoryValidatorImpl(gremlinService);
    }

    static Stream<Arguments> validateRepositoryProvider() {
        return Stream.of(
                Arguments.of("my.repo_v1", null), // Valid (mix of chars)
                Arguments.of("ab", InvalidRepositoryNameException.class), // Too short (min 3)
                Arguments.of("abc", null), // Boundary min
                Arguments.of("a".repeat(31), InvalidRepositoryNameException.class), // Too long (max 30)
                Arguments.of("repo!name", InvalidRepositoryNameException.class) // Invalid special char
        );
    }

    @ParameterizedTest(name = "Format Check - Name: {0}")
    @MethodSource("validateRepositoryProvider")
    void testValidateRepository(String name, Class<? extends Throwable> expectedEx) {
        RepositoryValidationDTO dto = new RepositoryValidationDTO(name);

        if (expectedEx != null) {
            assertThrows(expectedEx, () -> validator.validateRepository(dto));
        } else {
            ValidationResult result = validator.validateRepository(dto);
            assertTrue(result.isValid());
        }
    }

    static Stream<Arguments> existenceProvider() {
        return Stream.of(
                // existsFlag (Input), dbResponse, expectedEx
                Arguments.of(true,  false, RepositoryNotFoundException.class),
                Arguments.of(true,  true,  null),
                Arguments.of(false, true,  ExistingRepositoryException.class),
                Arguments.of(false, false, null)
        );
    }

    @ParameterizedTest(name = "Existence - Expected:{0}, InDB:{1}")
    @MethodSource("existenceProvider")
    void testValidateExistence(boolean existsFlag, boolean dbResponse, Class<? extends Throwable> expectedEx) {
        RepositoryValidationDTO dto = new RepositoryValidationDTO("valid.repo");

        when(gremlinService.existsByRepository(any())).thenReturn(dbResponse);

        if (expectedEx != null) {
            assertThrows(expectedEx, () -> validator.validateExistence(dto, existsFlag));
        } else {
            assertDoesNotThrow(() -> validator.validateExistence(dto, existsFlag));
        }
    }

    @Test
    void testValidateExistence_InvalidName() {
        RepositoryValidationDTO dto = new RepositoryValidationDTO("a!");
        assertThrows(InvalidRepositoryNameException.class, () -> validator.validateExistence(dto, true));
        verifyNoInteractions(gremlinService);
    }
}