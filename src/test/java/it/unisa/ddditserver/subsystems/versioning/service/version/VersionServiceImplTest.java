package it.unisa.ddditserver.subsystems.versioning.service.version;

import it.unisa.ddditserver.db.gremlin.versioning.repo.GremlinRepositoryRepository;
import it.unisa.ddditserver.db.gremlin.versioning.version.GremlinVersionRepository;
import it.unisa.ddditserver.subsystems.ai.service.TagClassificationService;
import it.unisa.ddditserver.subsystems.auth.dto.UserDTO;
import it.unisa.ddditserver.subsystems.auth.exceptions.NotLoggedUserException;
import it.unisa.ddditserver.subsystems.versioning.dto.RepositoryDTO;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VersionServiceImplTest {

    @Mock
    private GremlinRepositoryRepository gremlinRepositoryRepository;

    @Mock
    private GremlinVersionRepository gremlinVersionRepository;

    @Mock
    private JWTokenValidator jwTokenValidator;

    @Mock
    private TagClassificationService tagClassificationService;

    @Mock
    private UserValidator userValidator;

    @Mock
    private VersionValidator versionValidator;

    private VersionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new VersionServiceImpl(
                gremlinRepositoryRepository,
                gremlinVersionRepository,
                jwTokenValidator,
                tagClassificationService,
                userValidator,
                versionValidator
        );
    }

    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------

    private static VersionDTO baseVersionDTO() {
        VersionDTO dto = new VersionDTO();
        dto.setRepositoryName("repo1");
        dto.setResourceName("res1");
        dto.setBranchName("main");
        dto.setVersionName("versionOne");
        dto.setComment("my comment");
        return dto;
    }

    private static MultipartFile mockFile(String filename) throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        lenient().when(file.getOriginalFilename()).thenReturn(filename);
        return file;
    }

    private void allowAsContributor() {
        when(gremlinRepositoryRepository.isContributor(any(RepositoryDTO.class), any(UserDTO.class)))
                .thenReturn(true);
    }

    private void allowAsOwner() {
        when(gremlinRepositoryRepository.isContributor(any(RepositoryDTO.class), any(UserDTO.class)))
                .thenReturn(false);
        when(gremlinRepositoryRepository.isOwner(any(RepositoryDTO.class), any(UserDTO.class)))
                .thenReturn(true);
    }

    private void denyUser() {
        when(gremlinRepositoryRepository.isContributor(any(RepositoryDTO.class), any(UserDTO.class)))
                .thenReturn(false);
        when(gremlinRepositoryRepository.isOwner(any(RepositoryDTO.class), any(UserDTO.class)))
                .thenReturn(false);
    }

    // ============================================================
    // createVersion
    //
    // Categories:
    // - token: valid | invalid
    // - authorization: allowed | denied
    // - resource type: mesh | material
    // - saveVersion: ok | throws
    // ============================================================

    static Stream<Arguments> createVersionCases() {
        return Stream.of(
                Arguments.of("INVALID_TOKEN", false, false, NotLoggedUserException.class),
                Arguments.of("DENIED",        false, false, RepositoryException.class),
                Arguments.of("MESH_OK",       true,  false, null),
                Arguments.of("MATERIAL_OK",   false, false, null),
                Arguments.of("SAVE_THROWS",   true,  true,  VersionException.class)
        );
    }

    @ParameterizedTest
    @MethodSource("createVersionCases")
    void createVersion_categoryPartition(String behavior,
                                         boolean meshType,
                                         boolean saveThrows,
                                         Class<? extends Throwable> expectedEx) throws Exception {

        VersionDTO dto = baseVersionDTO();

        if (meshType) {
            MultipartFile mesh = mockFile("mesh.fbx");
            dto.setMesh(mesh);
            when(tagClassificationService.classify(any(VersionDTO.class))).thenReturn(List.of("tag1", "tag2"));
        } else if (!"INVALID_TOKEN".equals(behavior) && !"DENIED".equals(behavior)) {
            MultipartFile texture = mockFile("texture.png");
            dto.setMaterial(List.of(texture));
        }

        if ("INVALID_TOKEN".equals(behavior)) {
            when(jwTokenValidator.isTokenValid("bad-token")).thenReturn(null);
        } else {
            when(jwTokenValidator.isTokenValid("valid-token")).thenReturn("mario");

            if ("DENIED".equals(behavior)) {
                denyUser();
            } else {
                allowAsContributor();
            }

            if (saveThrows) {
                doThrow(new RuntimeException("boom"))
                        .when(gremlinVersionRepository)
                        .saveVersion(any(VersionDTO.class), anyBoolean());
            }
        }

        if (expectedEx != null) {
            String token = "INVALID_TOKEN".equals(behavior) ? "bad-token" : "valid-token";
            assertThrows(expectedEx, () -> service.createVersion(dto, token));
            return;
        }

        ResponseEntity<Map<String, String>> response = service.createVersion(dto, "valid-token");

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());

        String message = response.getBody().get("message");
        assertNotNull(message);
        assertTrue(message.contains("repo1"));
        assertTrue(message.contains("res1"));
        assertTrue(message.contains("main"));

        verify(userValidator).validateExistence(any(), eq(true));
        verify(versionValidator).validateVersion(any(), eq(meshType));
        verify(versionValidator).validateExistence(any(), eq(false));
        verify(gremlinVersionRepository).saveVersion(any(VersionDTO.class), eq(meshType));

        if (meshType) {
            verify(tagClassificationService).classify(any(VersionDTO.class));
        }
    }

    // ============================================================
    // pullVersion
    //
    // Categories:
    // - token: valid | invalid
    // - authorization: allowed | denied
    // - getFile: ok | throws
    // ============================================================

    static Stream<Arguments> pullVersionCases() {
        return Stream.of(
                Arguments.of("INVALID_TOKEN", NotLoggedUserException.class),
                Arguments.of("DENIED",        RepositoryException.class),
                Arguments.of("GET_THROWS",    VersionException.class),
                Arguments.of("OK",            null)
        );
    }

    @ParameterizedTest
    @MethodSource("pullVersionCases")
    void pullVersion_categoryPartition(String behavior,
                                       Class<? extends Throwable> expectedEx) {

        VersionDTO dto = baseVersionDTO();

        if ("INVALID_TOKEN".equals(behavior)) {
            when(jwTokenValidator.isTokenValid("bad-token")).thenReturn(null);
        } else {
            when(jwTokenValidator.isTokenValid("valid-token")).thenReturn("mario");

            if ("DENIED".equals(behavior)) {
                denyUser();
            } else {
                allowAsOwner();
            }

            if ("GET_THROWS".equals(behavior)) {
                when(gremlinVersionRepository.getFile(any(VersionDTO.class)))
                        .thenThrow(new RuntimeException("boom"));
            } else if ("OK".equals(behavior)) {
                NonClosingInputStreamResource resource = mock(NonClosingInputStreamResource.class);
                when(resource.getFilename()).thenReturn("mesh.fbx");

                when(gremlinVersionRepository.getFile(any(VersionDTO.class)))
                        .thenReturn(List.of(Pair.of(resource, "application/octet-stream")));
            }
        }

        if (expectedEx != null) {
            String token = "INVALID_TOKEN".equals(behavior) ? "bad-token" : "valid-token";
            assertThrows(expectedEx, () -> service.pullVersion(dto, token));
            return;
        }

        ResponseEntity<MultiValueMap<String, Object>> response = service.pullVersion(dto, "valid-token");

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("message"));
        assertTrue(response.getBody().containsKey("file"));

        List<Object> files = response.getBody().get("file");
        assertNotNull(files);
        assertEquals(1, files.size());
        assertTrue(files.get(0) instanceof HttpEntity<?>);

        verify(userValidator).validateExistence(any(), eq(true));
        verify(versionValidator).validateExistence(any(), eq(true));
        verify(gremlinVersionRepository).getFile(dto);
    }

    // ============================================================
    // showVersionMetadata
    //
    // Categories:
    // - token: valid | invalid
    // - authorization: allowed | denied
    // - metadata lookup: found | not found
    // ============================================================

    static Stream<Arguments> metadataCases() {
        return Stream.of(
                Arguments.of("INVALID_TOKEN", NotLoggedUserException.class, false),
                Arguments.of("DENIED",        RepositoryException.class,    false),
                Arguments.of("NOT_FOUND",     null,                         true),
                Arguments.of("OK",            null,                         false)
        );
    }

    @ParameterizedTest
    @MethodSource("metadataCases")
    void showVersionMetadata_categoryPartition(String behavior,
                                               Class<? extends Throwable> expectedEx,
                                               boolean notFound) {

        VersionDTO input = baseVersionDTO();

        if ("INVALID_TOKEN".equals(behavior)) {
            when(jwTokenValidator.isTokenValid("bad-token")).thenReturn(null);
        } else {
            when(jwTokenValidator.isTokenValid("valid-token")).thenReturn("mario");

            if ("DENIED".equals(behavior)) {
                denyUser();
            } else {
                allowAsContributor();
            }

            if (notFound) {
                when(gremlinVersionRepository.findVersionByBranch(any(VersionDTO.class)))
                        .thenThrow(new RuntimeException("not found"));
            } else if ("OK".equals(behavior)) {
                VersionDTO found = new VersionDTO();
                found.setVersionName("v123");
                found.setUsername("mario");
                found.setPushedAt(LocalDateTime.of(2025, 1, 1, 10, 30));
                found.setComment("hello");
                found.setTags(List.of("tag1", "tag2"));

                when(gremlinVersionRepository.findVersionByBranch(any(VersionDTO.class)))
                        .thenReturn(found);
            }
        }

        if (expectedEx != null) {
            String token = "INVALID_TOKEN".equals(behavior) ? "bad-token" : "valid-token";
            assertThrows(expectedEx, () -> service.showVersionMetadata(input, token));
            return;
        }

        ResponseEntity<Map<String, Object>> response = service.showVersionMetadata(input, "valid-token");

        assertNotNull(response);

        if (notFound) {
            assertEquals(404, response.getStatusCode().value());
            assertNotNull(response.getBody());
            assertEquals("Metadata not found", response.getBody().get("error"));
            return;
        }

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("Version information retrieved successfully", response.getBody().get("message"));
        assertEquals("v123", response.getBody().get("versionName"));
        assertEquals("mario", response.getBody().get("username"));
        assertEquals("hello", response.getBody().get("comment"));
        assertNotNull(response.getBody().get("pushedAt"));
        assertNotNull(response.getBody().get("tags"));

        verify(userValidator).validateExistence(any(), eq(true));
        verify(versionValidator).validateExistence(any(), eq(true));
        verify(gremlinVersionRepository).findVersionByBranch(any(VersionDTO.class));
    }
}