package it.unisa.ddditserver.validators.versioning.branch;

import it.unisa.ddditserver.db.gremlin.versioning.branch.GremlinBranchRepository;
import it.unisa.ddditserver.subsystems.versioning.exceptions.branch.*;
import it.unisa.ddditserver.validators.ValidationResult;
import it.unisa.ddditserver.validators.versioning.repo.RepositoryValidator;
import it.unisa.ddditserver.validators.versioning.resource.ResourceValidator;
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
class BranchValidatorImplTest {

    @Mock private RepositoryValidator repositoryValidator;
    @Mock private ResourceValidator resourceValidator;
    @Mock private GremlinBranchRepository gremlinService;

    private BranchValidatorImpl validator;

    @BeforeEach
    void setUp() {
        validator = new BranchValidatorImpl(repositoryValidator, resourceValidator, gremlinService);
    }

    static Stream<Arguments> validateBranchProvider() {
        return Stream.of(
                // repoValid, resValid, branchName, expectedEx
                Arguments.of(false, true, "main", null), // Parent Repo Invalid
                Arguments.of(true, false, "main", null), // Parent Res Invalid
                Arguments.of(true, true, "br", InvalidBranchNameException.class), // Too short
                Arguments.of(true, true, "valid_branch", null), // Success
                Arguments.of(true, true, "invalid-name!", InvalidBranchNameException.class) // Invalid chars
        );
    }

    @ParameterizedTest(name = "Validate Branch - RepoOK:{0}, ResOK:{1}, Name:{2}")
    @MethodSource("validateBranchProvider")
    void testValidateBranch(boolean repoValid, boolean resValid, String branch, Class<? extends Throwable> expectedEx) {
        BranchValidationDTO dto = new BranchValidationDTO("repo", "res", branch);

        when(repositoryValidator.validate(any())).thenReturn(
                repoValid ? ValidationResult.valid() : ValidationResult.invalid(new RuntimeException())
        );

        if (repoValid) {
            when(resourceValidator.validate(any())).thenReturn(
                    resValid ? ValidationResult.valid() : ValidationResult.invalid(new RuntimeException())
            );
        }

        if (expectedEx != null) {
            assertThrows(expectedEx, () -> validator.validateBranch(dto));
        } else {
            ValidationResult result = validator.validateBranch(dto);
            if (!repoValid || !resValid) {
                assertFalse(result.isValid());
            } else {
                assertTrue(result.isValid());
            }
        }
    }

    static Stream<Arguments> existenceProvider() {
        return Stream.of(
                // existsFlag (Expected), dbResponse, expectedEx
                Arguments.of(true,  false, BranchNotFoundException.class),
                Arguments.of(true,  true,  null),
                Arguments.of(false, true,  ExistingBranchException.class),
                Arguments.of(false, false, null)
        );
    }

    @ParameterizedTest(name = "Existence - Expected:{0}, InDB:{1}")
    @MethodSource("existenceProvider")
    void testValidateExistence(boolean existsFlag, boolean dbResponse, Class<? extends Throwable> expectedEx) {
        BranchValidationDTO dto = new BranchValidationDTO("repo", "res", "main");

        when(repositoryValidator.validate(any())).thenReturn(ValidationResult.valid());
        when(resourceValidator.validate(any())).thenReturn(ValidationResult.valid());

        when(gremlinService.existsByResource(any())).thenReturn(dbResponse);

        if (expectedEx != null) {
            assertThrows(expectedEx, () -> validator.validateExistence(dto, existsFlag));
        } else {
            assertDoesNotThrow(() -> validator.validateExistence(dto, existsFlag));
        }
    }
}