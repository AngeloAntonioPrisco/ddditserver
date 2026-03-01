package it.unisa.ddditserver.db.unit.gremlin.versioning;

import it.unisa.ddditserver.db.gremlin.JanusConfig;
import it.unisa.ddditserver.db.gremlin.versioning.repo.GremlinRepositoryRepositoryImpl;
import it.unisa.ddditserver.subsystems.auth.dto.UserDTO;
import it.unisa.ddditserver.subsystems.versioning.dto.RepositoryDTO;
import it.unisa.ddditserver.subsystems.versioning.exceptions.repo.RepositoryException;
import org.apache.tinkerpop.gremlin.driver.Client;
import org.apache.tinkerpop.gremlin.driver.Result;
import org.apache.tinkerpop.gremlin.driver.ResultSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GremlinRepositoryRepositoryImplTest {

    @Mock private JanusConfig config;
    @Mock private Client client;

    private GremlinRepositoryRepositoryImpl repository;

    private static final String USERNAME_KEY = "username";
    private static final String REPOSITORY_NAME_KEY = "repositoryName";

    @BeforeEach
    void setUp() throws Exception {
        repository = new GremlinRepositoryRepositoryImpl(config);

        // Inject mocked Gremlin client (avoid init/PostConstruct)
        Field f = GremlinRepositoryRepositoryImpl.class.getDeclaredField("client");
        f.setAccessible(true);
        f.set(repository, client);
    }

    // ------------------------------------------------------------
    // Helpers: stub client.submit(...).all().get()
    // ------------------------------------------------------------
    private void stubSubmitAllGetReturns(List<Result> results) {
        ResultSet rs = mock(ResultSet.class);
        when(rs.all()).thenReturn(CompletableFuture.completedFuture(results));
        when(client.submit(anyString(), anyMap())).thenReturn(rs);
    }

    private void stubSubmitAllGetThrows() {
        ResultSet rs = mock(ResultSet.class);
        when(rs.all()).thenReturn(CompletableFuture.failedFuture(new RuntimeException("boom")));
        when(client.submit(anyString(), anyMap())).thenReturn(rs);
    }

    private static Map<String, Object> capturedBindings(ArgumentCaptor<Map> cap) {
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) cap.getValue();
        return m;
    }

    private static UserDTO user(String u) { return new UserDTO(u, null); }
    private static RepositoryDTO repo(String r) { return new RepositoryDTO(r); }

    // =========================================================
    // Category Partition: saveRepository
    // Categories:
    // - submit ok
    // - submit throws -> RepositoryException
    // =========================================================
    static Stream<Arguments> saveRepositoryCases() {
        return Stream.of(
                Arguments.of(false, false),
                Arguments.of(true,  true)
        );
    }

    @ParameterizedTest
    @MethodSource("saveRepositoryCases")
    void saveRepository_categoryPartition(boolean submitThrows, boolean expectException) {
        RepositoryDTO repositoryDTO = repo("repo1");
        UserDTO owner = user("alice");

        if (submitThrows) stubSubmitAllGetThrows();
        else stubSubmitAllGetReturns(List.of());

        if (expectException) {
            assertThrows(RepositoryException.class, () -> repository.saveRepository(repositoryDTO, owner));
            Thread.interrupted();
        } else {
            assertDoesNotThrow(() -> repository.saveRepository(repositoryDTO, owner));
        }

        // Capture submit args (avoid ambiguous overload)
        ArgumentCaptor<String> queryCap = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Map> bindCap = ArgumentCaptor.forClass(Map.class);

        verify(client).submit(queryCap.capture(), bindCap.capture());

        assertTrue(queryCap.getValue().contains("addV('repository'"));
        assertTrue(queryCap.getValue().contains("addE('OWNS'"));

        Map<String, Object> bindings = capturedBindings(bindCap);
        assertEquals("alice", bindings.get("uName"));
        assertEquals("repo1", bindings.get("rName"));
    }

    // =========================================================
    // Category Partition: existsByRepository
    // Categories:
    // - results empty -> false
    // - count 0 -> false
    // - count > 0 -> true
    // - throws -> RepositoryException
    // =========================================================
    static Stream<Arguments> existsRepoCases() {
        return Stream.of(
                Arguments.of("EMPTY", 0L, false, null),
                Arguments.of("COUNT", 0L, false, null),
                Arguments.of("COUNT", 2L, true,  null),
                Arguments.of("THROWS",0L, false, RepositoryException.class)
        );
    }

    @ParameterizedTest
    @MethodSource("existsRepoCases")
    void existsByRepository_categoryPartition(String behavior,
                                              long count,
                                              boolean expected,
                                              Class<? extends Throwable> expectedEx) {
        RepositoryDTO repositoryDTO = repo("repo1");

        if ("THROWS".equals(behavior)) {
            stubSubmitAllGetThrows();
        } else if ("EMPTY".equals(behavior)) {
            stubSubmitAllGetReturns(List.of());
        } else {
            Result r = mock(Result.class);
            when(r.getLong()).thenReturn(count);
            stubSubmitAllGetReturns(List.of(r));
        }

        if (expectedEx != null) {
            assertThrows(expectedEx, () -> repository.existsByRepository(repositoryDTO));
            Thread.interrupted();
        } else {
            assertEquals(expected, repository.existsByRepository(repositoryDTO));
        }

        ArgumentCaptor<String> queryCap = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Map> bindCap = ArgumentCaptor.forClass(Map.class);

        verify(client).submit(queryCap.capture(), bindCap.capture());

        assertTrue(queryCap.getValue().contains(".count()"));

        Map<String, Object> bindings = capturedBindings(bindCap);
        assertEquals("repo1", bindings.get("rName"));
    }

    // =========================================================
    // Category Partition: findContributorsByRepository
    // Categories:
    // - empty -> empty list
    // - one ok -> size 1
    // - many ok -> size N
    // - malformed props -> RepositoryException
    // - throws -> RepositoryException
    // =========================================================
    static Stream<Arguments> findContributorsCases() {
        return Stream.of(
                Arguments.of("EMPTY",     0, null),
                Arguments.of("ONE_OK",    1, null),
                Arguments.of("MANY_OK",   2, null),
                Arguments.of("MALFORMED", 0, RepositoryException.class),
                Arguments.of("THROWS",    0, RepositoryException.class)
        );
    }

    @ParameterizedTest
    @MethodSource("findContributorsCases")
    void findContributorsByRepository_categoryPartition(String behavior,
                                                        int expectedSize,
                                                        Class<? extends Throwable> expectedEx) {
        RepositoryDTO repositoryDTO = repo("repo1");

        if ("THROWS".equals(behavior)) {
            stubSubmitAllGetThrows();
        } else if ("EMPTY".equals(behavior)) {
            stubSubmitAllGetReturns(List.of());
        } else if ("ONE_OK".equals(behavior)) {
            Result r = mock(Result.class);
            when(r.getObject()).thenReturn(Map.of(USERNAME_KEY, List.of("bob")));
            stubSubmitAllGetReturns(List.of(r));
        } else if ("MANY_OK".equals(behavior)) {
            Result r1 = mock(Result.class);
            Result r2 = mock(Result.class);
            when(r1.getObject()).thenReturn(Map.of(USERNAME_KEY, List.of("bob")));
            when(r2.getObject()).thenReturn(Map.of(USERNAME_KEY, List.of("carol")));
            stubSubmitAllGetReturns(List.of(r1, r2));
        } else { // MALFORMED
            Result r = mock(Result.class);
            // missing "username" triggers NPE then caught -> RepositoryException
            when(r.getObject()).thenReturn(Map.of("x", List.of("y")));
            stubSubmitAllGetReturns(List.of(r));
        }

        if (expectedEx != null) {
            assertThrows(expectedEx, () -> repository.findContributorsByRepository(repositoryDTO));
            Thread.interrupted();
            return;
        }

        List<UserDTO> contributors = repository.findContributorsByRepository(repositoryDTO);
        assertNotNull(contributors);
        assertEquals(expectedSize, contributors.size());

        if ("ONE_OK".equals(behavior)) {
            assertEquals("bob", contributors.get(0).getUsername());
        }
        if ("MANY_OK".equals(behavior)) {
            assertEquals("bob", contributors.get(0).getUsername());
            assertEquals("carol", contributors.get(1).getUsername());
        }

        ArgumentCaptor<String> queryCap = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Map> bindCap = ArgumentCaptor.forClass(Map.class);

        verify(client).submit(queryCap.capture(), bindCap.capture());

        assertTrue(queryCap.getValue().contains("union(__.in('CONTRIBUTES_TO'), __.in('OWNS'))"));
        assertTrue(queryCap.getValue().contains("valueMap('username')"));

        Map<String, Object> bindings = capturedBindings(bindCap);
        assertEquals("repo1", bindings.get(REPOSITORY_NAME_KEY));
    }

    // =========================================================
    // Category Partition: isContributor
    // Categories:
    // - empty -> false
    // - count 0 -> false
    // - count > 0 -> true
    // - throws -> RepositoryException
    // =========================================================
    static Stream<Arguments> booleanCountCases() {
        return Stream.of(
                Arguments.of("EMPTY", 0L, false, null),
                Arguments.of("COUNT", 0L, false, null),
                Arguments.of("COUNT", 1L, true,  null),
                Arguments.of("THROWS",0L, false, RepositoryException.class)
        );
    }

    @ParameterizedTest
    @MethodSource("booleanCountCases")
    void isContributor_categoryPartition(String behavior,
                                         long count,
                                         boolean expected,
                                         Class<? extends Throwable> expectedEx) {

        RepositoryDTO repositoryDTO = repo("repo1");
        UserDTO userDTO = user("bob");

        if ("THROWS".equals(behavior)) stubSubmitAllGetThrows();
        else if ("EMPTY".equals(behavior)) stubSubmitAllGetReturns(List.of());
        else {
            Result r = mock(Result.class);
            when(r.getLong()).thenReturn(count);
            stubSubmitAllGetReturns(List.of(r));
        }

        if (expectedEx != null) {
            assertThrows(expectedEx, () -> repository.isContributor(repositoryDTO, userDTO));
            Thread.interrupted();
        } else {
            assertEquals(expected, repository.isContributor(repositoryDTO, userDTO));
        }

        ArgumentCaptor<String> queryCap = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Map> bindCap = ArgumentCaptor.forClass(Map.class);

        verify(client).submit(queryCap.capture(), bindCap.capture());

        assertTrue(queryCap.getValue().contains(".out('CONTRIBUTES_TO')"));
        assertTrue(queryCap.getValue().contains(".count()"));

        Map<String, Object> bindings = capturedBindings(bindCap);
        assertEquals("bob", bindings.get(USERNAME_KEY));
        assertEquals("repo1", bindings.get(REPOSITORY_NAME_KEY));
    }

    // =========================================================
    // Category Partition: isOwner
    // Categories:
    // - empty -> false
    // - count 0 -> false
    // - count > 0 -> true
    // - throws -> RepositoryException
    // =========================================================
    @ParameterizedTest
    @MethodSource("booleanCountCases")
    void isOwner_categoryPartition(String behavior,
                                   long count,
                                   boolean expected,
                                   Class<? extends Throwable> expectedEx) {

        RepositoryDTO repositoryDTO = repo("repo1");
        UserDTO userDTO = user("alice");

        if ("THROWS".equals(behavior)) stubSubmitAllGetThrows();
        else if ("EMPTY".equals(behavior)) stubSubmitAllGetReturns(List.of());
        else {
            Result r = mock(Result.class);
            when(r.getLong()).thenReturn(count);
            stubSubmitAllGetReturns(List.of(r));
        }

        if (expectedEx != null) {
            assertThrows(expectedEx, () -> repository.isOwner(repositoryDTO, userDTO));
            Thread.interrupted();
        } else {
            assertEquals(expected, repository.isOwner(repositoryDTO, userDTO));
        }

        ArgumentCaptor<String> queryCap = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Map> bindCap = ArgumentCaptor.forClass(Map.class);

        verify(client).submit(queryCap.capture(), bindCap.capture());

        assertTrue(queryCap.getValue().contains(".out('OWNS')"));
        assertTrue(queryCap.getValue().contains(".count()"));

        Map<String, Object> bindings = capturedBindings(bindCap);
        assertEquals("alice", bindings.get(USERNAME_KEY));
        assertEquals("repo1", bindings.get(REPOSITORY_NAME_KEY));
    }

    // =========================================================
    // Category Partition: addContributor
    // Categories:
    // - submit ok
    // - submit throws -> RepositoryException
    // =========================================================
    static Stream<Arguments> addContributorCases() {
        return Stream.of(
                Arguments.of(false, false),
                Arguments.of(true,  true)
        );
    }

    @ParameterizedTest
    @MethodSource("addContributorCases")
    void addContributor_categoryPartition(boolean submitThrows, boolean expectException) {
        RepositoryDTO repositoryDTO = repo("repo1");
        UserDTO userDTO = user("bob");

        if (submitThrows) stubSubmitAllGetThrows();
        else stubSubmitAllGetReturns(List.of());

        if (expectException) {
            assertThrows(RepositoryException.class, () -> repository.addContributor(repositoryDTO, userDTO));
            Thread.interrupted();
        } else {
            assertDoesNotThrow(() -> repository.addContributor(repositoryDTO, userDTO));
        }

        ArgumentCaptor<String> queryCap = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Map> bindCap = ArgumentCaptor.forClass(Map.class);

        verify(client).submit(queryCap.capture(), bindCap.capture());

        assertTrue(queryCap.getValue().contains("addE('CONTRIBUTES_TO')"));
        assertTrue(queryCap.getValue().contains("coalesce"));

        Map<String, Object> bindings = capturedBindings(bindCap);
        assertEquals("bob", bindings.get(USERNAME_KEY));
        assertEquals("repo1", bindings.get(REPOSITORY_NAME_KEY));
    }

    // =========================================================
    // Category Partition: findOwnedRepositoriesByUser
    // Categories:
    // - empty -> empty list
    // - one -> size 1
    // - many -> size N
    // - malformed -> RepositoryException
    // - throws -> RepositoryException
    // =========================================================
    static Stream<Arguments> findOwnedReposCases() {
        return Stream.of(
                Arguments.of("EMPTY",     0, null),
                Arguments.of("ONE_OK",    1, null),
                Arguments.of("MANY_OK",   2, null),
                Arguments.of("MALFORMED", 0, RepositoryException.class),
                Arguments.of("THROWS",    0, RepositoryException.class)
        );
    }

    @ParameterizedTest
    @MethodSource("findOwnedReposCases")
    void findOwnedRepositoriesByUser_categoryPartition(String behavior,
                                                       int expectedSize,
                                                       Class<? extends Throwable> expectedEx) {

        UserDTO userDTO = user("alice");

        if ("THROWS".equals(behavior)) {
            stubSubmitAllGetThrows();
        } else if ("EMPTY".equals(behavior)) {
            stubSubmitAllGetReturns(List.of());
        } else if ("ONE_OK".equals(behavior)) {
            Result r = mock(Result.class);
            when(r.getObject()).thenReturn(Map.of(REPOSITORY_NAME_KEY, List.of("repo1")));
            stubSubmitAllGetReturns(List.of(r));
        } else if ("MANY_OK".equals(behavior)) {
            Result r1 = mock(Result.class);
            Result r2 = mock(Result.class);
            when(r1.getObject()).thenReturn(Map.of(REPOSITORY_NAME_KEY, List.of("repo1")));
            when(r2.getObject()).thenReturn(Map.of(REPOSITORY_NAME_KEY, List.of("repo2")));
            stubSubmitAllGetReturns(List.of(r1, r2));
        } else { // MALFORMED
            Result r = mock(Result.class);
            when(r.getObject()).thenReturn(Map.of("x", List.of("y")));
            stubSubmitAllGetReturns(List.of(r));
        }

        if (expectedEx != null) {
            assertThrows(expectedEx, () -> repository.findOwnedRepositoriesByUser(userDTO));
            Thread.interrupted();
            return;
        }

        List<RepositoryDTO> repos = repository.findOwnedRepositoriesByUser(userDTO);
        assertNotNull(repos);
        assertEquals(expectedSize, repos.size());

        if ("ONE_OK".equals(behavior)) assertEquals("repo1", repos.get(0).getRepositoryName());
        if ("MANY_OK".equals(behavior)) {
            assertEquals("repo1", repos.get(0).getRepositoryName());
            assertEquals("repo2", repos.get(1).getRepositoryName());
        }

        ArgumentCaptor<String> queryCap = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Map> bindCap = ArgumentCaptor.forClass(Map.class);

        verify(client).submit(queryCap.capture(), bindCap.capture());

        assertTrue(queryCap.getValue().contains(".out('OWNS')"));
        assertTrue(queryCap.getValue().contains("valueMap('repositoryName')"));

        Map<String, Object> bindings = capturedBindings(bindCap);
        assertEquals("alice", bindings.get(USERNAME_KEY));
    }

    // =========================================================
    // Category Partition: findContributedRepositoriesByUser
    // Categories: same as owned
    // =========================================================
    @ParameterizedTest
    @MethodSource("findOwnedReposCases")
    void findContributedRepositoriesByUser_categoryPartition(String behavior,
                                                             int expectedSize,
                                                             Class<? extends Throwable> expectedEx) {

        UserDTO userDTO = user("bob");

        if ("THROWS".equals(behavior)) {
            stubSubmitAllGetThrows();
        } else if ("EMPTY".equals(behavior)) {
            stubSubmitAllGetReturns(List.of());
        } else if ("ONE_OK".equals(behavior)) {
            Result r = mock(Result.class);
            when(r.getObject()).thenReturn(Map.of(REPOSITORY_NAME_KEY, List.of("repo1")));
            stubSubmitAllGetReturns(List.of(r));
        } else if ("MANY_OK".equals(behavior)) {
            Result r1 = mock(Result.class);
            Result r2 = mock(Result.class);
            when(r1.getObject()).thenReturn(Map.of(REPOSITORY_NAME_KEY, List.of("repo1")));
            when(r2.getObject()).thenReturn(Map.of(REPOSITORY_NAME_KEY, List.of("repo2")));
            stubSubmitAllGetReturns(List.of(r1, r2));
        } else { // MALFORMED
            Result r = mock(Result.class);
            when(r.getObject()).thenReturn(Map.of("x", List.of("y")));
            stubSubmitAllGetReturns(List.of(r));
        }

        if (expectedEx != null) {
            assertThrows(expectedEx, () -> repository.findContributedRepositoriesByUser(userDTO));
            Thread.interrupted();
            return;
        }

        List<RepositoryDTO> repos = repository.findContributedRepositoriesByUser(userDTO);
        assertNotNull(repos);
        assertEquals(expectedSize, repos.size());

        if ("ONE_OK".equals(behavior)) assertEquals("repo1", repos.get(0).getRepositoryName());
        if ("MANY_OK".equals(behavior)) {
            assertEquals("repo1", repos.get(0).getRepositoryName());
            assertEquals("repo2", repos.get(1).getRepositoryName());
        }

        ArgumentCaptor<String> queryCap = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Map> bindCap = ArgumentCaptor.forClass(Map.class);

        verify(client).submit(queryCap.capture(), bindCap.capture());

        assertTrue(queryCap.getValue().contains(".out('CONTRIBUTES_TO')"));
        assertTrue(queryCap.getValue().contains("valueMap('repositoryName')"));

        Map<String, Object> bindings = capturedBindings(bindCap);
        assertEquals("bob", bindings.get(USERNAME_KEY));
    }
}