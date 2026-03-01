package it.unisa.ddditserver.db.unit.gremlin.versioning.resource;

import it.unisa.ddditserver.db.gremlin.JanusConfig;
import it.unisa.ddditserver.db.gremlin.versioning.resource.GremlinResourceRepositoryImpl;
import it.unisa.ddditserver.subsystems.versioning.dto.RepositoryDTO;
import it.unisa.ddditserver.subsystems.versioning.dto.ResourceDTO;
import it.unisa.ddditserver.subsystems.versioning.exceptions.resource.ResourceException;
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
class GremlinResourceRepositoryImplTest {

    @Mock private JanusConfig config;
    @Mock private Client client;

    private GremlinResourceRepositoryImpl repository;

    @BeforeEach
    void setUp() throws Exception {
        repository = new GremlinResourceRepositoryImpl(config);

        // Inject mocked Gremlin client (avoid init/PostConstruct)
        Field f = GremlinResourceRepositoryImpl.class.getDeclaredField("client");
        f.setAccessible(true);
        f.set(repository, client);
    }

    // ------------------------------------------------------------
    // Helpers
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

    private static ResourceDTO resource(String repo, String res) {
        return new ResourceDTO(repo, res);
    }

    private static RepositoryDTO repo(String repoName) {
        return new RepositoryDTO(repoName);
    }

    // =========================================================
    // Category Partition: saveResource
    // Categories:
    // - submit ok
    // - submit throws -> ResourceException
    // =========================================================
    static Stream<Arguments> saveResourceCases() {
        return Stream.of(
                Arguments.of(false, false),
                Arguments.of(true,  true)
        );
    }

    @ParameterizedTest
    @MethodSource("saveResourceCases")
    void saveResource_categoryPartition(boolean submitThrows, boolean expectException) {
        ResourceDTO dto = resource("repo1", "res1");

        if (submitThrows) stubSubmitAllGetThrows();
        else stubSubmitAllGetReturns(List.of());

        if (expectException) {
            assertThrows(ResourceException.class, () -> repository.saveResource(dto));
            Thread.interrupted();
        } else {
            assertDoesNotThrow(() -> repository.saveResource(dto));
        }

        ArgumentCaptor<String> queryCap = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Map> bindingsCap = ArgumentCaptor.forClass(Map.class);

        verify(client).submit(queryCap.capture(), bindingsCap.capture());

        assertTrue(queryCap.getValue().contains("addV('resource'"));
        assertTrue(queryCap.getValue().contains("addE('CONTAINS'"));

        Map<String, Object> bindings = capturedBindings(bindingsCap);
        assertEquals("repo1", bindings.get("repoName"));
        assertEquals("res1", bindings.get("resName"));
    }

    // =========================================================
    // Category Partition: existsByRepository
    // Categories:
    // - results empty -> false
    // - count 0 -> false
    // - count > 0 -> true
    // - throws -> ResourceException
    // =========================================================
    static Stream<Arguments> existsCases() {
        return Stream.of(
                Arguments.of("EMPTY", 0L, false, null),
                Arguments.of("COUNT", 0L, false, null),
                Arguments.of("COUNT", 2L, true,  null),
                Arguments.of("THROWS",0L, false, ResourceException.class)
        );
    }

    @ParameterizedTest
    @MethodSource("existsCases")
    void existsByRepository_categoryPartition(String behavior,
                                              long count,
                                              boolean expected,
                                              Class<? extends Throwable> expectedEx) {
        ResourceDTO dto = resource("repo1", "res1");

        if ("THROWS".equals(behavior)) {
            stubSubmitAllGetThrows();
        } else if ("EMPTY".equals(behavior)) {
            stubSubmitAllGetReturns(List.of());
        } else { // COUNT
            Result r = mock(Result.class);
            when(r.getLong()).thenReturn(count);
            stubSubmitAllGetReturns(List.of(r));
        }

        if (expectedEx != null) {
            assertThrows(expectedEx, () -> repository.existsByRepository(dto));
            Thread.interrupted();
        } else {
            assertEquals(expected, repository.existsByRepository(dto));
        }

        ArgumentCaptor<String> queryCap = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Map> bindingsCap = ArgumentCaptor.forClass(Map.class);

        verify(client).submit(queryCap.capture(), bindingsCap.capture());

        assertTrue(queryCap.getValue().contains(".out('CONTAINS')"));
        assertTrue(queryCap.getValue().contains(".count()"));

        Map<String, Object> bindings = capturedBindings(bindingsCap);
        assertEquals("repo1", bindings.get("repoName"));
        assertEquals("res1", bindings.get("resName"));
    }

    // =========================================================
    // Category Partition: findResourcesByRepository
    // Categories:
    // - empty -> empty list
    // - one ok -> size 1
    // - many ok -> size N
    // - malformed props -> ResourceException
    // - submit throws -> ResourceException
    // =========================================================
    static Stream<Arguments> findResourcesCases() {
        return Stream.of(
                Arguments.of("EMPTY",     0, null),
                Arguments.of("ONE_OK",    1, null),
                Arguments.of("MANY_OK",   2, null),
                Arguments.of("MALFORMED", 0, ResourceException.class),
                Arguments.of("THROWS",    0, ResourceException.class)
        );
    }

    @ParameterizedTest
    @MethodSource("findResourcesCases")
    void findResourcesByRepository_categoryPartition(String behavior,
                                                     int expectedSize,
                                                     Class<? extends Throwable> expectedEx) {

        RepositoryDTO repositoryDTO = repo("repo1");

        if ("THROWS".equals(behavior)) {
            stubSubmitAllGetThrows();
        } else if ("EMPTY".equals(behavior)) {
            stubSubmitAllGetReturns(List.of());
        } else if ("ONE_OK".equals(behavior)) {
            Result r = mock(Result.class);
            when(r.getObject()).thenReturn(Map.of("resourceName", List.of("res1")));
            stubSubmitAllGetReturns(List.of(r));
        } else if ("MANY_OK".equals(behavior)) {
            Result r1 = mock(Result.class);
            Result r2 = mock(Result.class);
            when(r1.getObject()).thenReturn(Map.of("resourceName", List.of("res1")));
            when(r2.getObject()).thenReturn(Map.of("resourceName", List.of("res2")));
            stubSubmitAllGetReturns(List.of(r1, r2));
        } else { // MALFORMED
            Result r = mock(Result.class);
            // missing "resourceName" triggers NPE inside method, then caught -> ResourceException
            when(r.getObject()).thenReturn(Map.of("x", List.of("y")));
            stubSubmitAllGetReturns(List.of(r));
        }

        if (expectedEx != null) {
            assertThrows(expectedEx, () -> repository.findResourcesByRepository(repositoryDTO));
            Thread.interrupted();
            return;
        }

        List<ResourceDTO> resources = repository.findResourcesByRepository(repositoryDTO);
        assertNotNull(resources);
        assertEquals(expectedSize, resources.size());

        if ("ONE_OK".equals(behavior)) {
            assertEquals("repo1", resources.get(0).getRepositoryName());
            assertEquals("res1", resources.get(0).getResourceName());
        }
        if ("MANY_OK".equals(behavior)) {
            assertEquals("res1", resources.get(0).getResourceName());
            assertEquals("res2", resources.get(1).getResourceName());
        }

        ArgumentCaptor<String> queryCap = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Map> bindingsCap = ArgumentCaptor.forClass(Map.class);

        verify(client).submit(queryCap.capture(), bindingsCap.capture());

        assertTrue(queryCap.getValue().contains("valueMap('resourceName')"));

        Map<String, Object> bindings = capturedBindings(bindingsCap);
        assertEquals("repo1", bindings.get("repositoryName"));
    }
}