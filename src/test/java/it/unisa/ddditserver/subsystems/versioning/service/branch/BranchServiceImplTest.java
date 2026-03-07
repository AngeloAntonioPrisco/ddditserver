package it.unisa.ddditserver.subsystems.versioning.service.branch;

import it.unisa.ddditserver.db.gremlin.versioning.branch.GremlinBranchRepository;
import it.unisa.ddditserver.db.gremlin.versioning.repo.GremlinRepositoryRepository;
import it.unisa.ddditserver.subsystems.auth.exceptions.NotLoggedUserException;
import it.unisa.ddditserver.subsystems.versioning.dto.BranchDTO;
import it.unisa.ddditserver.subsystems.versioning.dto.ResourceDTO;
import it.unisa.ddditserver.subsystems.versioning.exceptions.branch.BranchException;
import it.unisa.ddditserver.subsystems.versioning.exceptions.repo.RepositoryException;
import it.unisa.ddditserver.validators.auth.JWT.JWTokenValidator;
import it.unisa.ddditserver.validators.auth.user.UserValidator;
import it.unisa.ddditserver.validators.versioning.branch.BranchValidator;
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
class BranchServiceImplTest {

    @Mock private GremlinBranchRepository gremlinBranchRepository;
    @Mock private GremlinRepositoryRepository gremlinRepositoryRepository;
    @Mock private JWTokenValidator jwTokenValidator;
    @Mock private UserValidator userValidator;
    @Mock private ResourceValidator resourceValidator;
    @Mock private BranchValidator branchValidator;

    private BranchServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BranchServiceImpl(
                gremlinBranchRepository,
                gremlinRepositoryRepository,
                jwTokenValidator,
                userValidator,
                resourceValidator,
                branchValidator
        );
    }

    static Stream<Arguments> createBranchProvider() {
        return Stream.of(
                // Case 1: Success path
                Arguments.of("token", true, false, false, null),
                // Case 2: Authentication failure (Error Partition)
                Arguments.of(null, false, false, false, NotLoggedUserException.class),
                // Case 3: Authorization failure (Permission Denied)
                Arguments.of("token", false, false, false, RepositoryException.class),
                // Case 4: Branch already exists (Boundary: Existence check fails)
                Arguments.of("token", true, true, false, RuntimeException.class),
                // Case 5: DB Runtime Error
                Arguments.of("token", true, false, true, BranchException.class)
        );
    }

    @ParameterizedTest(name = "CreateBranch - Token: {0}, Auth: {1}, Exists: {2}, DBError: {3}")
    @MethodSource("createBranchProvider")
    void testCreateBranch(String token, boolean isAuthorized, boolean alreadyExists, boolean dbError, Class<? extends Throwable> expectedEx) {
        BranchDTO dto = new BranchDTO("repo1", "res1", "feature-x");
        String username = "mario";

        // Mock Authentication
        when(jwTokenValidator.isTokenValid(token)).thenReturn(token != null ? username : null);

        if (token != null) {
            // Mock Authorization
            when(gremlinRepositoryRepository.isContributor(any(), any())).thenReturn(isAuthorized);
            if (!isAuthorized) {
                when(gremlinRepositoryRepository.isOwner(any(), any())).thenReturn(false);
            } else {
                // Mock Validation & Existence
                if (alreadyExists) {
                    doThrow(new RuntimeException()).when(branchValidator).validateExistence(any(), eq(false));
                }
                // Mock DB
                if (dbError) {
                    doThrow(new RuntimeException()).when(gremlinBranchRepository).saveBranch(any());
                }
            }
        }

        if (expectedEx != null) {
            assertThrows(expectedEx, () -> service.createBranch(dto, token));
        } else {
            ResponseEntity<Map<String, String>> response = service.createBranch(dto, token);
            assertEquals(200, response.getStatusCode().value());
            verify(gremlinBranchRepository).saveBranch(dto);
        }
    }

    static Stream<Arguments> listBranchesProvider() {
        return Stream.of(
                // Case 1: Success with multiple results
                Arguments.of(true, true, 2, null),
                // Case 2: Success with empty list (Boundary)
                Arguments.of(true, true, 0, null),
                // Case 3: Access denied
                Arguments.of(false, true, 0, RepositoryException.class),
                // Case 4: Target Resource not found
                Arguments.of(true, false, 0, RuntimeException.class)
        );
    }

    @ParameterizedTest(name = "ListBranches - Authorized: {0}, ResourceFound: {1}, Count: {2}")
    @MethodSource("listBranchesProvider")
    void testListBranchesByResource(boolean authorized, boolean resourceFound, int branchCount, Class<? extends Throwable> expectedEx) {
        ResourceDTO dto = new ResourceDTO("repo1", "res1");
        String token = "valid_token";

        when(jwTokenValidator.isTokenValid(token)).thenReturn("mario");
        when(gremlinRepositoryRepository.isContributor(any(), any())).thenReturn(authorized);
        if (!authorized) when(gremlinRepositoryRepository.isOwner(any(), any())).thenReturn(false);

        if (authorized) {
            if (!resourceFound) {
                doThrow(new RuntimeException()).when(resourceValidator).validateExistence(any(), eq(true));
            } else {
                List<BranchDTO> branches = Stream.generate(() -> new BranchDTO("repo1", "res1", "b"))
                        .limit(branchCount).toList();
                when(gremlinBranchRepository.findBranchesByResource(any())).thenReturn(branches);
            }
        }

        if (expectedEx != null) {
            assertThrows(expectedEx, () -> service.listBranchesByResource(dto, token));
        } else {
            ResponseEntity<Map<String, Object>> response = service.listBranchesByResource(dto, token);
            List<?> result = (List<?>) response.getBody().get("branches");
            assertEquals(branchCount, result.size());
        }
    }
}