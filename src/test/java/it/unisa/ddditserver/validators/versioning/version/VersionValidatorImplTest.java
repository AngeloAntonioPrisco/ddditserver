package it.unisa.ddditserver.validators.versioning.version;

import it.unisa.ddditserver.db.gremlin.versioning.version.GremlinVersionRepository;
import it.unisa.ddditserver.subsystems.versioning.exceptions.version.*;
import it.unisa.ddditserver.validators.ValidationResult;
import it.unisa.ddditserver.validators.versioning.branch.BranchValidator;
import it.unisa.ddditserver.validators.versioning.repo.RepositoryValidator;
import it.unisa.ddditserver.validators.versioning.resource.ResourceValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;
import java.util.Collections;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VersionValidatorImplTest {

    @Mock private RepositoryValidator repositoryValidator;
    @Mock private ResourceValidator resourceValidator;
    @Mock private BranchValidator branchValidator;
    @Mock private GremlinVersionRepository gremlinService;

    private VersionValidatorImpl validator;

    @BeforeEach
    void setUp() {
        validator = new VersionValidatorImpl(gremlinService, branchValidator, resourceValidator, repositoryValidator);

        lenient().when(repositoryValidator.validate(any())).thenReturn(ValidationResult.valid());
        lenient().when(resourceValidator.validate(any())).thenReturn(ValidationResult.valid());
        lenient().when(branchValidator.validate(any())).thenReturn(ValidationResult.valid());
    }

    static Stream<Arguments> validateVersionProvider() {
        return Stream.of(
                // Case 1: Perfect Mesh (resourceType = true)
                Arguments.of("v1_name", "Cool version", "model.fbx", true, null),
                // Case 2: Perfect Texture (resourceType = false)
                Arguments.of("v1_name", "Cool version", "texture.png", false, null),
                // Case 3: Invalid Extension for Mesh
                Arguments.of("v1_name", "Cool version", "model.exe", true, InvalidMeshException.class),
                // Case 4: Invalid Extension for Texture
                Arguments.of("v1_name", "Cool version", "texture.jpg", false, InvalidMaterialException.class),
                // Case 5: Comment too long (> 200)
                Arguments.of("v1_name", "a".repeat(201), "model.fbx", true, InvalidCommentException.class),
                // Case 6: Version name invalid (special chars)
                Arguments.of("v1.bad", "Comment", "model.fbx", true, InvalidVersionNameException.class)
        );
    }

    @ParameterizedTest(name = "Validate Version - Name:{0}, File:{2}, Type:{3}")
    @MethodSource("validateVersionProvider")
    void testValidateVersion(String name, String comment, String fileName, boolean isResourceType, Class<? extends Throwable> expectedEx) {
        MultipartFile mockFile = mockFile(fileName, 1024L); // 1KB

        VersionValidationDTO dto = new VersionValidationDTO(
                "repo", "res", "branch", name, comment,
                isResourceType ? mockFile : null,
                isResourceType ? null : Collections.singletonList(mockFile)
        );

        if (expectedEx != null) {
            assertThrows(expectedEx, () -> validator.validateVersion(dto, isResourceType));
        } else {
            ValidationResult result = validator.validateVersion(dto, isResourceType);
            assertTrue(result.isValid());
        }
    }

    @Test
    void testFileTooLarge() {
        long tooBig = (long) Math.pow(1024, 3) + 1;
        MultipartFile bigFile = mockFile("big.fbx", tooBig);

        VersionValidationDTO dto = new VersionValidationDTO(
                "repo", "res", "branch", "v1_valida", "comment", bigFile, null
        );

        assertThrows(InvalidMeshException.class, () -> validator.validateVersion(dto, true));
    }

    private MultipartFile mockFile(String filename, long size) {
        MultipartFile file = mock(MultipartFile.class);
        lenient().when(file.getOriginalFilename()).thenReturn(filename);
        lenient().when(file.getSize()).thenReturn(size);
        lenient().when(file.isEmpty()).thenReturn(false);
        return file;
    }
}