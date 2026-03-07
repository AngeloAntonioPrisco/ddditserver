package it.unisa.ddditserver.validators.versioning.resource;

import it.unisa.ddditserver.db.gremlin.versioning.resource.GremlinResourceRepository;
import it.unisa.ddditserver.subsystems.versioning.exceptions.resource.*;
import it.unisa.ddditserver.validators.ValidationResult;
import it.unisa.ddditserver.validators.versioning.repo.RepositoryValidator;
import org.junit.jupiter.api.BeforeEach;
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
class ResourceValidatorImplTest {

    @Mock private RepositoryValidator repositoryValidator;
    @Mock private GremlinResourceRepository gremlinService;

    private ResourceValidatorImpl validator;

    @BeforeEach
    void setUp() {
        validator = new ResourceValidatorImpl(repositoryValidator, gremlinService);
    }

    static Stream<Arguments> validateResourceProvider() {
        return Stream.of(
                // repoValid, name, expectedEx
                Arguments.of(false, "my_resource", null),
                Arguments.of(true,  "ab", InvalidResourceNameException.class),
                Arguments.of(true,  "abc", null),
                Arguments.of(true,  "a".repeat(31), InvalidResourceNameException.class),
                Arguments.of(true,  "res-name!", InvalidResourceNameException.class),
                Arguments.of(true,  "valid_res_123", null)
        );
    }

    @ParameterizedTest(name = "Format - RepoOK:{0}, Name:{1}")
    @MethodSource("validateResourceProvider")
    void testValidateResource(boolean repoValid, String name, Class<? extends Throwable> expectedEx) {
        ResourceValidationDTO dto = new ResourceValidationDTO("repo", name);

        when(repositoryValidator.validate(any())).thenReturn(
                repoValid ? ValidationResult.valid() : ValidationResult.invalid(new RuntimeException("Repo error"))
        );

        if (expectedEx != null) {
            assertThrows(expectedEx, () -> validator.validateResource(dto));
        } else {
            ValidationResult result = validator.validateResource(dto);
            assertEquals(repoValid, result.isValid());
        }
    }

    static Stream<Arguments> existenceProvider() {
        return Stream.of(
                // existsFlag (Expected), dbResponse, expectedEx
                Arguments.of(true,  false, ResourceNotFoundException.class),
                Arguments.of(true,  true,  null),
                Arguments.of(false, true,  ExistingResourceException.class),
                Arguments.of(false, false, null)
        );
    }

    @ParameterizedTest(name = "Existence - Expected:{0}, InDB:{1}")
    @MethodSource("existenceProvider")
    void testValidateExistence(boolean expectedToExist, boolean dbFound, Class<? extends Throwable> expectedEx) {
        ResourceValidationDTO dto = new ResourceValidationDTO("repo", "valid_resource");

        when(repositoryValidator.validate(any())).thenReturn(ValidationResult.valid());
        when(gremlinService.existsByRepository(any())).thenReturn(dbFound);

        if (expectedEx != null) {
            assertThrows(expectedEx, () -> validator.validateExistence(dto, expectedToExist));
        } else {
            assertDoesNotThrow(() -> validator.validateExistence(dto, expectedToExist));
        }
    }
}