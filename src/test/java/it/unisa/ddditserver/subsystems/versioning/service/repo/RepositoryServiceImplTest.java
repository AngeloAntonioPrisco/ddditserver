package it.unisa.ddditserver.subsystems.versioning.service.repo;

import it.unisa.ddditserver.db.gremlin.versioning.repo.GremlinRepositoryRepository;
import it.unisa.ddditserver.subsystems.auth.dto.UserDTO;
import it.unisa.ddditserver.subsystems.auth.exceptions.NotLoggedUserException;
import it.unisa.ddditserver.subsystems.versioning.dto.RepositoryDTO;
import it.unisa.ddditserver.subsystems.versioning.exceptions.repo.RepositoryException;
import it.unisa.ddditserver.validators.auth.JWT.JWTokenValidator;
import it.unisa.ddditserver.validators.auth.user.UserValidationDTO;
import it.unisa.ddditserver.validators.auth.user.UserValidator;
import it.unisa.ddditserver.validators.versioning.repo.RepositoryValidationDTO;
import it.unisa.ddditserver.validators.versioning.repo.RepositoryValidator;
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
class RepositoryServiceImplTest {

    @Mock private GremlinRepositoryRepository gremlinService;
    @Mock private JWTokenValidator jwTokenValidator;
    @Mock private UserValidator userValidator;
    @Mock private RepositoryValidator repositoryValidator;

    private RepositoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RepositoryServiceImpl(
                gremlinService,
                jwTokenValidator,
                userValidator,
                repositoryValidator
        );
    }

    // =========================================================
    // Category Partition: createRepository
    //
    // Categories:
    // - token valid | invalid
    // - saveRepository ok | throws
    // - validators ok | throws (not explicitly partitioned here because
    //   if they throw, the service should just propagate)
    // =========================================================
    static Stream<Arguments> createRepositoryCases() {
        return Stream.of(
                Arguments.of("VALID", false, null),
                Arguments.of("VALID", true,  RepositoryException.class),
                Arguments.of("INVALID", false, NotLoggedUserException.class)
        );
    }

    @ParameterizedTest
    @MethodSource("createRepositoryCases")
    void createRepository_categoryPartition(String tokenKind,
                                            boolean saveThrows,
                                            Class<? extends Throwable> expectedEx) {
        String token = "token";
        RepositoryDTO repositoryDTO = new RepositoryDTO("repo1");

        if ("VALID".equals(tokenKind)) {
            when(jwTokenValidator.isTokenValid(token)).thenReturn("mario");
        } else {
            when(jwTokenValidator.isTokenValid(token)).thenReturn(null);
        }

        if (saveThrows) {
            doThrow(new RuntimeException("save failed"))
                    .when(gremlinService)
                    .saveRepository(any(RepositoryDTO.class), any(UserDTO.class));
        }

        if (expectedEx != null) {
            assertThrows(expectedEx, () -> service.createRepository(repositoryDTO, token));
            return;
        }

        ResponseEntity<Map<String, String>> response = service.createRepository(repositoryDTO, token);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("Repository repo1 created successfully", response.getBody().get("message"));

        verify(userValidator).validateExistence(any(UserValidationDTO.class), eq(true));
        verify(repositoryValidator).validateRepository(any(RepositoryValidationDTO.class));
        verify(repositoryValidator).validateExistence(any(RepositoryValidationDTO.class), eq(false));

        ArgumentCaptor<RepositoryDTO> repoCaptor = ArgumentCaptor.forClass(RepositoryDTO.class);
        ArgumentCaptor<UserDTO> userCaptor = ArgumentCaptor.forClass(UserDTO.class);

        verify(gremlinService).saveRepository(repoCaptor.capture(), userCaptor.capture());

        assertEquals("repo1", repoCaptor.getValue().getRepositoryName());
        assertEquals("mario", userCaptor.getValue().getUsername());
    }

    // =========================================================
    // Category Partition: listRepositoriesOwned
    //
    // Categories:
    // - token valid | invalid
    // - gremlin find ok | throws
    // - result list empty | non-empty
    // =========================================================
    static Stream<Arguments> listOwnedCases() {
        return Stream.of(
                Arguments.of("VALID_EMPTY", false, 0, null),
                Arguments.of("VALID_NONEMPTY", false, 2, null),
                Arguments.of("VALID_THROW", true, 0, RepositoryException.class),
                Arguments.of("INVALID", false, 0, NotLoggedUserException.class)
        );
    }

    @ParameterizedTest
    @MethodSource("listOwnedCases")
    void listRepositoriesOwned_categoryPartition(String scenario,
                                                 boolean gremlinThrows,
                                                 int repoCount,
                                                 Class<? extends Throwable> expectedEx) {
        String token = "token";

        if ("INVALID".equals(scenario)) {
            when(jwTokenValidator.isTokenValid(token)).thenReturn(null);
        } else {
            when(jwTokenValidator.isTokenValid(token)).thenReturn("mario");
        }

        if (gremlinThrows) {
            when(gremlinService.findOwnedRepositoriesByUser(any(UserDTO.class)))
                    .thenThrow(new RuntimeException("find failed"));
        } else if ("VALID_EMPTY".equals(scenario)) {
            when(gremlinService.findOwnedRepositoriesByUser(any(UserDTO.class)))
                    .thenReturn(List.of());
        } else if ("VALID_NONEMPTY".equals(scenario)) {
            when(gremlinService.findOwnedRepositoriesByUser(any(UserDTO.class)))
                    .thenReturn(List.of(
                            new RepositoryDTO("repo1"),
                            new RepositoryDTO("repo2")
                    ));
        }

        if (expectedEx != null) {
            assertThrows(expectedEx, () -> service.listRepositoriesOwned(token));
            return;
        }

        ResponseEntity<Map<String, Object>> response = service.listRepositoriesOwned(token);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("Owned repositories found successfully", response.getBody().get("message"));

        @SuppressWarnings("unchecked")
        List<RepositoryDTO> owned = (List<RepositoryDTO>) response.getBody().get("ownedRepositories");
        assertNotNull(owned);
        assertEquals(repoCount, owned.size());

        verify(userValidator).validateExistence(any(UserValidationDTO.class), eq(true));

        ArgumentCaptor<UserDTO> userCaptor = ArgumentCaptor.forClass(UserDTO.class);
        verify(gremlinService).findOwnedRepositoriesByUser(userCaptor.capture());
        assertEquals("mario", userCaptor.getValue().getUsername());
    }

    // =========================================================
    // Category Partition: listRepositoriesContributed
    //
    // Categories:
    // - token valid | invalid
    // - gremlin find ok | throws
    // - result list empty | non-empty
    // =========================================================
    static Stream<Arguments> listContributedCases() {
        return Stream.of(
                Arguments.of("VALID_EMPTY", false, 0, null),
                Arguments.of("VALID_NONEMPTY", false, 2, null),
                Arguments.of("VALID_THROW", true, 0, RepositoryException.class),
                Arguments.of("INVALID", false, 0, NotLoggedUserException.class)
        );
    }

    @ParameterizedTest
    @MethodSource("listContributedCases")
    void listRepositoriesContributed_categoryPartition(String scenario,
                                                       boolean gremlinThrows,
                                                       int repoCount,
                                                       Class<? extends Throwable> expectedEx) {
        String token = "token";

        if ("INVALID".equals(scenario)) {
            when(jwTokenValidator.isTokenValid(token)).thenReturn(null);
        } else {
            when(jwTokenValidator.isTokenValid(token)).thenReturn("mario");
        }

        if (gremlinThrows) {
            when(gremlinService.findContributedRepositoriesByUser(any(UserDTO.class)))
                    .thenThrow(new RuntimeException("find failed"));
        } else if ("VALID_EMPTY".equals(scenario)) {
            when(gremlinService.findContributedRepositoriesByUser(any(UserDTO.class)))
                    .thenReturn(List.of());
        } else if ("VALID_NONEMPTY".equals(scenario)) {
            when(gremlinService.findContributedRepositoriesByUser(any(UserDTO.class)))
                    .thenReturn(List.of(
                            new RepositoryDTO("repoA"),
                            new RepositoryDTO("repoB")
                    ));
        }

        if (expectedEx != null) {
            assertThrows(expectedEx, () -> service.listRepositoriesContributed(token));
            return;
        }

        ResponseEntity<Map<String, Object>> response = service.listRepositoriesContributed(token);

        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("Contributed repositories found successfully", response.getBody().get("message"));

        @SuppressWarnings("unchecked")
        List<RepositoryDTO> contributed = (List<RepositoryDTO>) response.getBody().get("contributedRepositories");
        assertNotNull(contributed);
        assertEquals(repoCount, contributed.size());

        verify(userValidator).validateExistence(any(UserValidationDTO.class), eq(true));

        ArgumentCaptor<UserDTO> userCaptor = ArgumentCaptor.forClass(UserDTO.class);
        verify(gremlinService).findContributedRepositoriesByUser(userCaptor.capture());
        assertEquals("mario", userCaptor.getValue().getUsername());
    }
}