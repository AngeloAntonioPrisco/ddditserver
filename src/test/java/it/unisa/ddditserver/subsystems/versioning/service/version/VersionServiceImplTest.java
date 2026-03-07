package it.unisa.ddditserver.subsystems.versioning.service.version;

import it.unisa.ddditserver.db.gremlin.versioning.repo.GremlinRepositoryRepository;
import it.unisa.ddditserver.db.gremlin.versioning.version.GremlinVersionRepository;
import it.unisa.ddditserver.subsystems.ai.service.TagClassificationService;
import it.unisa.ddditserver.subsystems.auth.exceptions.NotLoggedUserException;
import it.unisa.ddditserver.subsystems.versioning.dto.version.VersionDTO;
import it.unisa.ddditserver.subsystems.versioning.exceptions.repo.RepositoryException;
import it.unisa.ddditserver.subsystems.versioning.exceptions.version.VersionException;
import it.unisa.ddditserver.validators.auth.JWT.JWTokenValidator;
import it.unisa.ddditserver.validators.auth.user.UserValidator;
import it.unisa.ddditserver.validators.versioning.version.VersionValidator;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VersionServiceImplTest {

    @Mock private GremlinRepositoryRepository gremlinRepositoryRepository;
    @Mock private GremlinVersionRepository gremlinVersionRepository;
    @Mock private JWTokenValidator jwTokenValidator;
    @Mock private TagClassificationService tagClassificationService;
    @Mock private UserValidator userValidator;
    @Mock private VersionValidator versionValidator;

    private VersionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new VersionServiceImpl(
                gremlinRepositoryRepository, gremlinVersionRepository, jwTokenValidator,
                tagClassificationService, userValidator, versionValidator
        );
    }

    static Stream<Arguments> createVersionProvider() {
        return Stream.of(
                // Case 1: Mesh Success (triggers AI Classification)
                Arguments.of("token", true, true, false, null),
                // Case 2: Material Success
                Arguments.of("token", true, false, false, null),
                // Case 3: Unauthorized
                Arguments.of("token", false, true, false, RepositoryException.class),
                // Case 4: Missing Token
                Arguments.of(null, false, true, false, NotLoggedUserException.class),
                // Case 5: DB Failure on Save
                Arguments.of("token", true, true, true, VersionException.class)
        );
    }

    @ParameterizedTest(name = "CreateVersion - Auth: {1}, isMesh: {2}, DBError: {3}")
    @MethodSource("createVersionProvider")
    void testCreateVersion(String token, boolean authorized, boolean isMesh, boolean dbError, Class<? extends Throwable> expectedEx) {
        VersionDTO dto = new VersionDTO();
        dto.setRepositoryName("repo");
        dto.setResourceName("res");
        dto.setBranchName("main");
        dto.setVersionName("v1");

        if (isMesh) {
            dto.setMesh(mock(MultipartFile.class));
        } else {
            dto.setMaterial(List.of(mock(MultipartFile.class)));
        }

        when(jwTokenValidator.isTokenValid(token)).thenReturn(token != null ? "mario" : null);

        if (token != null) {
            if (isMesh) {
                when(tagClassificationService.classify(any())).thenReturn(List.of("ai-tag"));
            }

            when(gremlinRepositoryRepository.isContributor(any(), any())).thenReturn(authorized);
            if (!authorized) {
                when(gremlinRepositoryRepository.isOwner(any(), any())).thenReturn(false);
            }

            if (authorized && dbError) {
                doThrow(new RuntimeException()).when(gremlinVersionRepository).saveVersion(any(), anyBoolean());
            }
        }

        if (expectedEx != null) {
            assertThrows(expectedEx, () -> service.createVersion(dto, token));
        } else {
            ResponseEntity<Map<String, String>> response = service.createVersion(dto, token);
            assertEquals(200, response.getStatusCode().value());

            if (isMesh) verify(tagClassificationService).classify(any());
            verify(gremlinVersionRepository).saveVersion(any(), eq(isMesh));
        }
    }

    static Stream<Arguments> pullVersionProvider() {
        return Stream.of(
                Arguments.of(true, false, null),
                Arguments.of(true, true, VersionException.class),
                Arguments.of(false, false, RepositoryException.class)
        );
    }

    @ParameterizedTest(name = "PullVersion - Auth: {0}, DBError: {1}")
    @MethodSource("pullVersionProvider")
    void testPullVersion(boolean authorized, boolean dbError, Class<? extends Throwable> expectedEx) {
        VersionDTO dto = new VersionDTO();
        dto.setRepositoryName("r"); dto.setVersionName("v");
        String token = "valid";

        when(jwTokenValidator.isTokenValid(token)).thenReturn("mario");
        when(gremlinRepositoryRepository.isContributor(any(), any())).thenReturn(authorized);

        if (authorized) {
            if (dbError) {
                when(gremlinVersionRepository.getFile(any())).thenThrow(new RuntimeException());
            } else {
                NonClosingInputStreamResource res = mock(NonClosingInputStreamResource.class);
                when(res.getFilename()).thenReturn("file.fbx");
                when(gremlinVersionRepository.getFile(any())).thenReturn(List.of(Pair.of(res, "application/octet-stream")));
            }
        }

        if (expectedEx != null) {
            assertThrows(expectedEx, () -> service.pullVersion(dto, token));
        } else {
            ResponseEntity<MultiValueMap<String, Object>> response = service.pullVersion(dto, token);
            assertNotNull(response.getBody().get("file"));
        }
    }

    static Stream<Arguments> metadataProvider() {
        return Stream.of(
                Arguments.of("mario", "comment", null),
                Arguments.of(null, null, null)
        );
    }

    @ParameterizedTest(name = "Metadata - User: {0}, Comment: {1}")
    @MethodSource("metadataProvider")
    void testShowVersionMetadata(String user, String comment) {
        VersionDTO dto = new VersionDTO();
        dto.setRepositoryName("r");
        when(jwTokenValidator.isTokenValid(any())).thenReturn("mario");
        when(gremlinRepositoryRepository.isContributor(any(), any())).thenReturn(true);

        VersionDTO found = new VersionDTO();
        found.setUsername(user);
        found.setComment(comment);
        when(gremlinVersionRepository.findVersionByBranch(any())).thenReturn(found);

        ResponseEntity<Map<String, Object>> response = service.showVersionMetadata(dto, "token");

        assertEquals(user != null ? user : "anonymous", response.getBody().get("username"));
        assertEquals(comment != null ? comment : "", response.getBody().get("comment"));
    }
}