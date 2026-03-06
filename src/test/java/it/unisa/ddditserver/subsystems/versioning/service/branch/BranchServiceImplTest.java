package it.unisa.ddditserver.subsystems.versioning.service.branch;

import it.unisa.ddditserver.db.gremlin.versioning.branch.GremlinBranchRepository;
import it.unisa.ddditserver.db.gremlin.versioning.repo.GremlinRepositoryRepository;
import it.unisa.ddditserver.subsystems.auth.dto.UserDTO;
import it.unisa.ddditserver.subsystems.auth.exceptions.NotLoggedUserException;
import it.unisa.ddditserver.subsystems.versioning.dto.BranchDTO;
import it.unisa.ddditserver.subsystems.versioning.dto.RepositoryDTO;
import it.unisa.ddditserver.subsystems.versioning.dto.ResourceDTO;
import it.unisa.ddditserver.subsystems.versioning.exceptions.branch.BranchException;
import it.unisa.ddditserver.subsystems.versioning.exceptions.repo.RepositoryException;
import it.unisa.ddditserver.validators.auth.JWT.JWTokenValidator;
import it.unisa.ddditserver.validators.auth.user.UserValidationDTO;
import it.unisa.ddditserver.validators.auth.user.UserValidator;
import it.unisa.ddditserver.validators.versioning.branch.BranchValidationDTO;
import it.unisa.ddditserver.validators.versioning.branch.BranchValidator;
import it.unisa.ddditserver.validators.versioning.resource.ResourceValidationDTO;
import it.unisa.ddditserver.validators.versioning.resource.ResourceValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.ArgumentCaptor;
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

    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------
    private static BranchDTO branch(String repo, String res, String branch) {
        return new BranchDTO(repo, res, branch);
    }

    private static ResourceDTO resource(String repo, String res) {
        return new ResourceDTO(repo, res);
    }

    private void stubAuthorizedUser(String token, String username) {
        when(jwTokenValidator.isTokenValid(token)).thenReturn(username);
        when(gremlinRepositoryRepository.isContributor(
                any(RepositoryDTO.class),
                any(UserDTO.class)
        )).thenReturn(true);
    }

    private void stubUnauthorizedUser(String token, String username) {
        when(jwTokenValidator.isTokenValid(token)).thenReturn(username);
        when(gremlinRepositoryRepository.isContributor(
                any(RepositoryDTO.class),
                any(UserDTO.class)
        )).thenReturn(false);
        when(gremlinRepositoryRepository.isOwner(
                any(RepositoryDTO.class),
                any(UserDTO.class)
        )).thenReturn(false);
    }

    // =========================================================
    // Category Partition: createBranch
    //
    // Categories:
    // - token: valid | invalid
    // - authorization: allowed | denied
    // - repository save: ok | throws
    // =========================================================
    static Stream<Arguments> createBranchCases() {
        return Stream.of(
                Arguments.of("VALID_OK", false, false, null),
                Arguments.of("INVALID_TOKEN", false, false, NotLoggedUserException.class),
                Arguments.of("UNAUTHORIZED", false, false, RepositoryException.class),
                Arguments.of("VALID_OK", true, false, BranchException.class)
        );
    }

    @ParameterizedTest
    @MethodSource("createBranchCases")
    void createBranch_categoryPartition(String scenario,
                                        boolean saveThrows,
                                        boolean unused,
                                        Class<? extends Throwable> expectedEx) {

        BranchDTO branchDTO = branch("repo1", "res1", "main");
        String token = "token";
        String username = "mario";

        switch (scenario) {
            case "INVALID_TOKEN" -> when(jwTokenValidator.isTokenValid(token)).thenReturn(null);
            case "UNAUTHORIZED" -> stubUnauthorizedUser(token, username);
            default -> stubAuthorizedUser(token, username);
        }

        if (saveThrows) {
            doThrow(new RuntimeException("boom")).when(gremlinBranchRepository).saveBranch(any(BranchDTO.class));
        }

        if (expectedEx != null) {
            assertThrows(expectedEx, () -> service.createBranch(branchDTO, token));
            return;
        }

        ResponseEntity<Map<String, String>> response = service.createBranch(branchDTO, token);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(
                "Branch main created successfully for res1 resource in repo1 repository",
                response.getBody().get("message")
        );

        verify(userValidator).validateExistence(any(UserValidationDTO.class), eq(true));
        verify(branchValidator).validateBranch(any(BranchValidationDTO.class));
        verify(branchValidator).validateExistence(any(BranchValidationDTO.class), eq(false));
        verify(gremlinBranchRepository).saveBranch(branchDTO);

        ArgumentCaptor<BranchValidationDTO> captor = ArgumentCaptor.forClass(BranchValidationDTO.class);
        verify(branchValidator).validateBranch(captor.capture());
        BranchValidationDTO captured = captor.getValue();
        assertNotNull(captured);
    }

    // =========================================================
    // Category Partition: listBranchesByResource
    //
    // Categories:
    // - token: valid | invalid
    // - authorization: allowed | denied
    // - service repository: returns empty | returns list | throws
    // =========================================================
    static Stream<Arguments> listBranchesCases() {
        return Stream.of(
                Arguments.of("VALID_EMPTY", false, 0, null),
                Arguments.of("VALID_LIST", false, 2, null),
                Arguments.of("INVALID_TOKEN", false, 0, NotLoggedUserException.class),
                Arguments.of("UNAUTHORIZED", false, 0, RepositoryException.class),
                Arguments.of("VALID_LIST", true, 0, BranchException.class)
        );
    }

    @ParameterizedTest
    @MethodSource("listBranchesCases")
    void listBranchesByResource_categoryPartition(String scenario,
                                                  boolean findThrows,
                                                  int branchCount,
                                                  Class<? extends Throwable> expectedEx) {

        ResourceDTO resourceDTO = resource("repo1", "res1");
        String token = "token";
        String username = "mario";

        switch (scenario) {
            case "INVALID_TOKEN" -> when(jwTokenValidator.isTokenValid(token)).thenReturn(null);
            case "UNAUTHORIZED" -> stubUnauthorizedUser(token, username);
            default -> stubAuthorizedUser(token, username);
        }

        if (findThrows) {
            when(gremlinBranchRepository.findBranchesByResource(any(ResourceDTO.class)))
                    .thenThrow(new RuntimeException("boom"));
        } else if ("VALID_EMPTY".equals(scenario)) {
            when(gremlinBranchRepository.findBranchesByResource(any(ResourceDTO.class)))
                    .thenReturn(List.of());
        } else if ("VALID_LIST".equals(scenario)) {
            when(gremlinBranchRepository.findBranchesByResource(any(ResourceDTO.class)))
                    .thenReturn(List.of(
                            new BranchDTO("repo1", "res1", "main"),
                            new BranchDTO("repo1", "res1", "dev")
                    ));
        }

        if (expectedEx != null) {
            assertThrows(expectedEx, () -> service.listBranchesByResource(resourceDTO, token));
            return;
        }

        ResponseEntity<Map<String, Object>> response = service.listBranchesByResource(resourceDTO, token);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());

        assertEquals(
                "Branches found successfully for res1 resource in repo1 repository",
                response.getBody().get("message")
        );

        Object branchesObj = response.getBody().get("branches");
        assertNotNull(branchesObj);
        assertTrue(branchesObj instanceof List<?>);

        @SuppressWarnings("unchecked")
        List<BranchDTO> branches = (List<BranchDTO>) branchesObj;
        assertEquals(branchCount, branches.size());

        if (branchCount == 2) {
            assertEquals("main", branches.get(0).getBranchName());
            assertEquals("dev", branches.get(1).getBranchName());
        }

        verify(userValidator).validateExistence(any(UserValidationDTO.class), eq(true));
        verify(resourceValidator).validateResource(any(ResourceValidationDTO.class));
        verify(resourceValidator).validateExistence(any(ResourceValidationDTO.class), eq(true));
        verify(gremlinBranchRepository).findBranchesByResource(resourceDTO);
    }
}