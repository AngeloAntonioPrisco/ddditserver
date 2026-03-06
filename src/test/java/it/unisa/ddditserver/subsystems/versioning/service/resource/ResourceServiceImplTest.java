package it.unisa.ddditserver.subsystems.versioning.service.resource;

import it.unisa.ddditserver.db.gremlin.versioning.branch.GremlinBranchRepository;
import it.unisa.ddditserver.db.gremlin.versioning.repo.GremlinRepositoryRepository;
import it.unisa.ddditserver.db.gremlin.versioning.resource.GremlinResourceRepository;
import it.unisa.ddditserver.db.gremlin.versioning.version.GremlinVersionRepository;
import it.unisa.ddditserver.subsystems.auth.dto.UserDTO;
import it.unisa.ddditserver.subsystems.auth.exceptions.NotLoggedUserException;
import it.unisa.ddditserver.subsystems.versioning.dto.BranchDTO;
import it.unisa.ddditserver.subsystems.versioning.dto.RepositoryDTO;
import it.unisa.ddditserver.subsystems.versioning.dto.ResourceDTO;
import it.unisa.ddditserver.subsystems.versioning.dto.version.VersionDTO;
import it.unisa.ddditserver.subsystems.versioning.exceptions.repo.RepositoryException;
import it.unisa.ddditserver.subsystems.versioning.exceptions.resource.ResourceException;
import it.unisa.ddditserver.validators.auth.JWT.JWTokenValidator;
import it.unisa.ddditserver.validators.auth.user.UserValidator;
import it.unisa.ddditserver.validators.versioning.repo.RepositoryValidator;
import it.unisa.ddditserver.validators.versioning.resource.ResourceValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResourceServiceImplTest {

    @Mock
    private GremlinBranchRepository gremlinBranchRepository;

    @Mock
    private GremlinRepositoryRepository gremlinRepositoryRepository;

    @Mock
    private GremlinResourceRepository gremlinResourceRepository;

    @Mock
    private GremlinVersionRepository gremlinVersionRepository;

    @Mock
    private JWTokenValidator jwTokenValidator;

    @Mock
    private RepositoryValidator repositoryValidator;

    @Mock
    private ResourceValidator resourceValidator;

    @Mock
    private UserValidator userValidator;

    private ResourceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ResourceServiceImpl(
                gremlinBranchRepository,
                gremlinRepositoryRepository,
                gremlinResourceRepository,
                gremlinVersionRepository,
                jwTokenValidator,
                repositoryValidator,
                resourceValidator,
                userValidator
        );
    }

    private static ResourceDTO resource(String repo, String res) {
        return new ResourceDTO(repo, res);
    }

    private static RepositoryDTO repository(String repo) {
        return new RepositoryDTO(repo);
    }

    private static BranchDTO branch(String repo, String res, String branchName) {
        return new BranchDTO(repo, res, branchName);
    }

    private static VersionDTO version(String name) {
        VersionDTO dto = new VersionDTO();
        dto.setVersionName(name);
        return dto;
    }

    private void allowUserAsOwner() {
        when(gremlinRepositoryRepository.isContributor(any(RepositoryDTO.class), any(UserDTO.class))).thenReturn(false);
        when(gremlinRepositoryRepository.isOwner(any(RepositoryDTO.class), any(UserDTO.class))).thenReturn(true);
    }

    private void allowUserAsContributor() {
        when(gremlinRepositoryRepository.isContributor(any(RepositoryDTO.class), any(UserDTO.class))).thenReturn(true);
    }

    private void denyUser() {
        when(gremlinRepositoryRepository.isContributor(any(RepositoryDTO.class), any(UserDTO.class))).thenReturn(false);
        when(gremlinRepositoryRepository.isOwner(any(RepositoryDTO.class), any(UserDTO.class))).thenReturn(false);
    }

    // ============================================================
    // createResource
    // ============================================================
    static Stream<Arguments> createResourceCases() {
        return Stream.of(
                Arguments.of("INVALID_TOKEN", NotLoggedUserException.class),
                Arguments.of("DENIED", RepositoryException.class),
                Arguments.of("SAVE_THROWS", ResourceException.class),
                Arguments.of("OK", null)
        );
    }

    @ParameterizedTest
    @MethodSource("createResourceCases")
    void createResource_categoryPartition(String behavior,
                                          Class<? extends Throwable> expectedEx) {
        ResourceDTO resourceDTO = resource("repo1", "res1");

        if ("INVALID_TOKEN".equals(behavior)) {
            when(jwTokenValidator.isTokenValid("bad-token")).thenReturn(null);
        } else {
            when(jwTokenValidator.isTokenValid("valid-token")).thenReturn("mario");

            if ("DENIED".equals(behavior)) {
                denyUser();
            } else {
                allowUserAsOwner();
            }

            if ("SAVE_THROWS".equals(behavior)) {
                doThrow(new RuntimeException("boom")).when(gremlinResourceRepository).saveResource(any(ResourceDTO.class));
            }
        }

        if (expectedEx != null) {
            String token = "INVALID_TOKEN".equals(behavior) ? "bad-token" : "valid-token";
            assertThrows(expectedEx, () -> service.createResource(resourceDTO, token));
            return;
        }

        ResponseEntity<Map<String, String>> response = service.createResource(resourceDTO, "valid-token");

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("Resource res1 created successfully in repo1 repository", response.getBody().get("message"));

        verify(gremlinResourceRepository).saveResource(resourceDTO);
        verify(userValidator).validateExistence(any(), eq(true));
        verify(resourceValidator).validateResource(any());
        verify(resourceValidator).validateExistence(any(), eq(false));
    }

    // ============================================================
    // listResourcesByRepository
    // ============================================================
    static Stream<Arguments> listResourcesCases() {
        return Stream.of(
                Arguments.of("INVALID_TOKEN", NotLoggedUserException.class),
                Arguments.of("DENIED", RepositoryException.class),
                Arguments.of("FIND_THROWS", ResourceException.class),
                Arguments.of("OK", null)
        );
    }

    @ParameterizedTest
    @MethodSource("listResourcesCases")
    void listResourcesByRepository_categoryPartition(String behavior,
                                                     Class<? extends Throwable> expectedEx) {
        RepositoryDTO repositoryDTO = repository("repo1");

        if ("INVALID_TOKEN".equals(behavior)) {
            when(jwTokenValidator.isTokenValid("bad-token")).thenReturn(null);
        } else {
            when(jwTokenValidator.isTokenValid("valid-token")).thenReturn("mario");

            if ("DENIED".equals(behavior)) {
                denyUser();
            } else {
                allowUserAsContributor();
            }

            if ("FIND_THROWS".equals(behavior)) {
                lenient().when(gremlinResourceRepository.findResourcesByRepository(any(RepositoryDTO.class)))
                        .thenThrow(new RuntimeException("boom"));
            } else if ("OK".equals(behavior)) {
                lenient().when(gremlinResourceRepository.findResourcesByRepository(any(RepositoryDTO.class)))
                        .thenReturn(List.of(
                                new ResourceDTO("repo1", "res1"),
                                new ResourceDTO("repo1", "res2")
                        ));
            }
        }

        if (expectedEx != null) {
            String token = "INVALID_TOKEN".equals(behavior) ? "bad-token" : "valid-token";
            assertThrows(expectedEx, () -> service.listResourcesByRepository(repositoryDTO, token));
            return;
        }

        ResponseEntity<Map<String, Object>> response = service.listResourcesByRepository(repositoryDTO, "valid-token");

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("Resources found successfully in repo1 repository", response.getBody().get("message"));

        Object resources = response.getBody().get("resources");
        assertTrue(resources instanceof List<?>);
        assertEquals(2, ((List<?>) resources).size());

        verify(userValidator).validateExistence(any(), eq(true));
        verify(repositoryValidator).validateRepository(any());
        verify(repositoryValidator).validateExistence(any(), eq(true));
        verify(gremlinResourceRepository).findResourcesByRepository(repositoryDTO);
    }

    // ============================================================
    // showVersionTree
    // ============================================================
    static Stream<Arguments> showVersionTreeCases() {
        return Stream.of(
                Arguments.of("INVALID_TOKEN", NotLoggedUserException.class),
                Arguments.of("DENIED", RepositoryException.class),
                Arguments.of("REPO_THROWS", ResourceException.class),
                Arguments.of("EMPTY_BRANCHES", null),
                Arguments.of("OK", null)
        );
    }

    @ParameterizedTest
    @MethodSource("showVersionTreeCases")
    void showVersionTree_categoryPartition(String behavior,
                                           Class<? extends Throwable> expectedEx) {
        ResourceDTO resourceDTO = resource("repo1", "res1");

        if ("INVALID_TOKEN".equals(behavior)) {
            when(jwTokenValidator.isTokenValid("bad-token")).thenReturn(null);
        } else {
            when(jwTokenValidator.isTokenValid("valid-token")).thenReturn("mario");

            if ("DENIED".equals(behavior)) {
                denyUser();
            } else {
                allowUserAsContributor();
            }

            if ("REPO_THROWS".equals(behavior)) {
                lenient().when(gremlinBranchRepository.findBranchesByResource(any(ResourceDTO.class)))
                        .thenThrow(new RuntimeException("boom"));
            } else if ("EMPTY_BRANCHES".equals(behavior)) {
                lenient().when(gremlinBranchRepository.findBranchesByResource(any(ResourceDTO.class)))
                        .thenReturn(List.of());
            } else if ("OK".equals(behavior)) {
                BranchDTO main = branch("repo1", "res1", "main");
                BranchDTO dev = branch("repo1", "res1", "dev");

                lenient().when(gremlinBranchRepository.findBranchesByResource(any(ResourceDTO.class)))
                        .thenReturn(List.of(main, dev));

                lenient().when(gremlinVersionRepository.findVersionsByBranch(main))
                        .thenReturn(List.of(version("v1"), version("v2")));
                lenient().when(gremlinVersionRepository.findVersionsByBranch(dev))
                        .thenReturn(List.of(version("v3")));
            }
        }

        if (expectedEx != null) {
            String token = "INVALID_TOKEN".equals(behavior) ? "bad-token" : "valid-token";
            assertThrows(expectedEx, () -> service.showVersionTree(resourceDTO, token));
            return;
        }

        ResponseEntity<Map<String, Object>> response = service.showVersionTree(resourceDTO, "valid-token");

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(
                "Version tree of res1 resource in repo1 repository retrieved successfully",
                response.getBody().get("message")
        );

        Object treeObj = response.getBody().get("versionTree");
        assertTrue(treeObj instanceof Map<?, ?>);

        @SuppressWarnings("unchecked")
        Map<String, List<String>> versionTree = (Map<String, List<String>>) treeObj;

        if ("EMPTY_BRANCHES".equals(behavior)) {
            assertTrue(versionTree.isEmpty());
        } else {
            assertEquals(List.of("v1", "v2"), versionTree.get("main"));
            assertEquals(List.of("v3"), versionTree.get("dev"));
        }

        verify(userValidator).validateExistence(any(), eq(true));
        verify(resourceValidator).validateResource(any());
        verify(resourceValidator).validateExistence(any(), eq(true));
        verify(gremlinBranchRepository).findBranchesByResource(resourceDTO);
    }
}