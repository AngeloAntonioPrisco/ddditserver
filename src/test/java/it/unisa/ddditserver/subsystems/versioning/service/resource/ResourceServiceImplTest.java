package it.unisa.ddditserver.subsystems.versioning.service.resource;

import it.unisa.ddditserver.db.gremlin.versioning.branch.GremlinBranchRepository;
import it.unisa.ddditserver.db.gremlin.versioning.repo.GremlinRepositoryRepository;
import it.unisa.ddditserver.db.gremlin.versioning.resource.GremlinResourceRepository;
import it.unisa.ddditserver.db.gremlin.versioning.version.GremlinVersionRepository;
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

    @Mock private GremlinBranchRepository gremlinBranchRepository;
    @Mock private GremlinRepositoryRepository gremlinRepositoryRepository;
    @Mock private GremlinResourceRepository gremlinResourceRepository;
    @Mock private GremlinVersionRepository gremlinVersionRepository;
    @Mock private JWTokenValidator jwTokenValidator;
    @Mock private RepositoryValidator repositoryValidator;
    @Mock private ResourceValidator resourceValidator;
    @Mock private UserValidator userValidator;

    private ResourceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ResourceServiceImpl(
                gremlinBranchRepository, gremlinRepositoryRepository, gremlinResourceRepository,
                gremlinVersionRepository, jwTokenValidator, repositoryValidator,
                resourceValidator, userValidator
        );
    }

    static Stream<Arguments> createResourceProvider() {
        return Stream.of(
                // Case 1: Success
                Arguments.of("token", true, false, false, null),
                // Case 2: No session
                Arguments.of(null, false, false, false, NotLoggedUserException.class),
                // Case 3: Permission denied
                Arguments.of("token", false, false, false, RepositoryException.class),
                // Case 4: Resource collision (Boundary)
                Arguments.of("token", true, true, false, RuntimeException.class),
                // Case 5: DB Error
                Arguments.of("token", true, false, true, ResourceException.class)
        );
    }

    @ParameterizedTest(name = "CreateResource - Auth: {1}, Exists: {2}, Error: {3}")
    @MethodSource("createResourceProvider")
    void testCreateResource(String token, boolean authorized, boolean exists, boolean dbError, Class<? extends Throwable> expectedEx) {
        ResourceDTO dto = new ResourceDTO("repo1", "res1");
        when(jwTokenValidator.isTokenValid(token)).thenReturn(token != null ? "mario" : null);

        if (token != null) {
            when(gremlinRepositoryRepository.isContributor(any(), any())).thenReturn(authorized);
            if (!authorized) when(gremlinRepositoryRepository.isOwner(any(), any())).thenReturn(false);

            if (authorized) {
                if (exists) doThrow(new RuntimeException()).when(resourceValidator).validateExistence(any(), eq(false));
                if (dbError) doThrow(new RuntimeException()).when(gremlinResourceRepository).saveResource(any());
            }
        }

        if (expectedEx != null) {
            assertThrows(expectedEx, () -> service.createResource(dto, token));
        } else {
            ResponseEntity<Map<String, String>> response = service.createResource(dto, token);
            assertTrue(response.getBody().get("message").contains("successfully"));
        }
    }

    static Stream<Arguments> versionTreeProvider() {
        return Stream.of(
                // Case 1: Success with data
                Arguments.of(2, 2, false, null),
                // Case 2: Success but resource is empty (Boundary)
                Arguments.of(0, 0, false, null),
                // Case 3: Error during branch retrieval
                Arguments.of(0, 0, true, ResourceException.class)
        );
    }

    @ParameterizedTest(name = "VersionTree - Branches: {0}, Versions: {1}, DBError: {2}")
    @MethodSource("versionTreeProvider")
    @SuppressWarnings("unchecked")
    void testShowVersionTree(int branchCount, int versionCount, boolean dbError, Class<? extends Throwable> expectedEx) {
        ResourceDTO dto = new ResourceDTO("repo1", "res1");
        String token = "valid_token";

        when(jwTokenValidator.isTokenValid(token)).thenReturn("mario");
        when(gremlinRepositoryRepository.isContributor(any(), any())).thenReturn(true);

        if (dbError) {
            when(gremlinBranchRepository.findBranchesByResource(any())).thenThrow(new RuntimeException("Link broken"));
        } else {
            List<BranchDTO> branches = new java.util.ArrayList<>();
            for (int i = 0; i < branchCount; i++) {
                branches.add(new BranchDTO("repo1", "res1", "branch-" + i));
            }

            when(gremlinBranchRepository.findBranchesByResource(any())).thenReturn(branches);

            if (branchCount > 0) {
                VersionDTO v = new VersionDTO();
                v.setVersionName("v1");
                // Usiamo leniency qui perché findVersionsByBranch verrà chiamato più volte nel loop
                lenient().when(gremlinVersionRepository.findVersionsByBranch(any())).thenReturn(List.of(v));
            }
        }

        if (expectedEx != null) {
            assertThrows(expectedEx, () -> service.showVersionTree(dto, token));
        } else {
            ResponseEntity<Map<String, Object>> response = service.showVersionTree(dto, token);
            Map<String, List<String>> tree = (Map<String, List<String>>) response.getBody().get("versionTree");
            assertEquals(branchCount, tree.size(), "La dimensione del tree deve corrispondere al numero di branch unici");
        }
    }

    static Stream<Arguments> listResourcesProvider() {
        return Stream.of(
                Arguments.of(true, null),
                Arguments.of(false, RuntimeException.class)
        );
    }

    @ParameterizedTest(name = "ListResources - RepoExists: {0}")
    @MethodSource("listResourcesProvider")
    void testListResourcesByRepository(boolean repoExists, Class<? extends Throwable> expectedEx) {
        RepositoryDTO dto = new RepositoryDTO("repo1");
        String token = "token";

        when(jwTokenValidator.isTokenValid(token)).thenReturn("mario");
        when(gremlinRepositoryRepository.isContributor(any(), any())).thenReturn(true);

        if (!repoExists) {
            doThrow(new RuntimeException()).when(repositoryValidator).validateExistence(any(), eq(true));
        }

        if (expectedEx != null) {
            assertThrows(expectedEx, () -> service.listResourcesByRepository(dto, token));
        } else {
            ResponseEntity<Map<String, Object>> response = service.listResourcesByRepository(dto, token);
            assertEquals(200, response.getStatusCode().value());
        }
    }
}