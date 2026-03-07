package it.unisa.ddditserver.subsystems.versioning.service.repo;

import it.unisa.ddditserver.db.gremlin.versioning.repo.GremlinRepositoryRepository;
import it.unisa.ddditserver.subsystems.auth.exceptions.NotLoggedUserException;
import it.unisa.ddditserver.subsystems.versioning.dto.RepositoryDTO;
import it.unisa.ddditserver.subsystems.versioning.exceptions.repo.RepositoryException;
import it.unisa.ddditserver.validators.auth.JWT.JWTokenValidator;
import it.unisa.ddditserver.validators.auth.user.UserValidator;
import it.unisa.ddditserver.validators.versioning.repo.RepositoryValidator;
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
class RepositoryServiceImplTest {

    @Mock private GremlinRepositoryRepository gremlinService;
    @Mock private JWTokenValidator jwTokenValidator;
    @Mock private UserValidator userValidator;
    @Mock private RepositoryValidator repositoryValidator;

    private RepositoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RepositoryServiceImpl(gremlinService, jwTokenValidator, userValidator, repositoryValidator);
    }

    static Stream<Arguments> createRepositoryProvider() {
        return Stream.of(
                // Case 1: Standard Success path
                Arguments.of("valid_token", false, false, false, null),
                // Case 2: Authentication failure
                Arguments.of(null, false, false, false, NotLoggedUserException.class),
                // Case 3: Malformed Repository Name
                Arguments.of("valid_token", true, false, false, RuntimeException.class),
                // Case 4: Repository already exists (Boundary check)
                Arguments.of("valid_token", false, true, false, RuntimeException.class),
                // Case 5: Database error during save
                Arguments.of("valid_token", false, false, true, RepositoryException.class)
        );
    }

    @ParameterizedTest(name = "CreateRepo - Token: {0}, Malformed: {1}, Exists: {2}, DBError: {3}")
    @MethodSource("createRepositoryProvider")
    void testCreateRepository(String token, boolean malformed, boolean exists, boolean dbError, Class<? extends Throwable> expectedEx) {
        RepositoryDTO dto = new RepositoryDTO("new-repo");
        String user = "mario";

        when(jwTokenValidator.isTokenValid(token)).thenReturn(token != null ? user : null);

        if (token != null) {
            if (malformed) {
                doThrow(new RuntimeException()).when(repositoryValidator).validateRepository(any());
            } else if (exists) {
                doThrow(new RuntimeException()).when(repositoryValidator).validateExistence(any(), eq(false));
            } else if (dbError) {
                doThrow(new RuntimeException("Gremlin timeout")).when(gremlinService).saveRepository(any(), any());
            }
        }

        if (expectedEx != null) {
            assertThrows(expectedEx, () -> service.createRepository(dto, token));
        } else {
            ResponseEntity<Map<String, String>> response = service.createRepository(dto, token);
            assertEquals(200, response.getStatusCode().value());
            verify(gremlinService).saveRepository(any(), any());
        }
    }

    static Stream<Arguments> listRepositoriesProvider() {
        return Stream.of(
                // Case 1: Success with multiple repositories
                Arguments.of(true, 2, false, null),
                // Case 2: Success with empty list (Boundary)
                Arguments.of(true, 0, false, null),
                // Case 3: Authentication missing
                Arguments.of(false, 0, false, NotLoggedUserException.class),
                // Case 4: Database failure
                Arguments.of(true, 0, true, RepositoryException.class)
        );
    }

    @ParameterizedTest(name = "ListOwned - Auth: {0}, Count: {1}, DBError: {2}")
    @MethodSource("listRepositoriesProvider")
    @SuppressWarnings("unchecked")
    void testListRepositoriesOwned(boolean authorized, int count, boolean dbError, Class<? extends Throwable> expectedEx) {
        String token = "token";
        when(jwTokenValidator.isTokenValid(token)).thenReturn(authorized ? "mario" : null);

        if (authorized) {
            if (dbError) {
                when(gremlinService.findOwnedRepositoriesByUser(any())).thenThrow(new RuntimeException());
            } else {
                List<RepositoryDTO> repos = Stream.generate(() -> new RepositoryDTO("r")).limit(count).toList();
                when(gremlinService.findOwnedRepositoriesByUser(any())).thenReturn(repos);
            }
        }

        if (expectedEx != null) {
            assertThrows(expectedEx, () -> service.listRepositoriesOwned(token));
        } else {
            ResponseEntity<Map<String, Object>> response = service.listRepositoriesOwned(token);
            List<RepositoryDTO> result = (List<RepositoryDTO>) response.getBody().get("ownedRepositories");
            assertEquals(count, result.size());
        }
    }

    @ParameterizedTest(name = "ListContributed - Auth: {0}, Count: {1}, DBError: {2}")
    @MethodSource("listRepositoriesProvider")
    @SuppressWarnings("unchecked")
    void testListRepositoriesContributed(boolean authorized, int count, boolean dbError, Class<? extends Throwable> expectedEx) {
        String token = "token";
        when(jwTokenValidator.isTokenValid(token)).thenReturn(authorized ? "mario" : null);

        if (authorized) {
            if (dbError) {
                when(gremlinService.findContributedRepositoriesByUser(any())).thenThrow(new RuntimeException());
            } else {
                List<RepositoryDTO> repos = Stream.generate(() -> new RepositoryDTO("c")).limit(count).toList();
                when(gremlinService.findContributedRepositoriesByUser(any())).thenReturn(repos);
            }
        }

        if (expectedEx != null) {
            assertThrows(expectedEx, () -> service.listRepositoriesContributed(token));
        } else {
            ResponseEntity<Map<String, Object>> response = service.listRepositoriesContributed(token);
            List<RepositoryDTO> result = (List<RepositoryDTO>) response.getBody().get("contributedRepositories");
            assertEquals(count, result.size());
        }
    }
}