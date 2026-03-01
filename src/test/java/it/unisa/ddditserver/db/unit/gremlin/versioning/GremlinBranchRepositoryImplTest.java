package it.unisa.ddditserver.db.unit.gremlin.versioning;

import it.unisa.ddditserver.db.gremlin.JanusConfig;
import it.unisa.ddditserver.db.gremlin.versioning.branch.GremlinBranchRepositoryImpl;
import it.unisa.ddditserver.subsystems.versioning.dto.BranchDTO;
import it.unisa.ddditserver.subsystems.versioning.dto.ResourceDTO;
import it.unisa.ddditserver.subsystems.versioning.exceptions.branch.BranchException;
import it.unisa.ddditserver.subsystems.versioning.exceptions.version.VersionException;
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
class GremlinBranchRepositoryImplTest {

    @Mock private JanusConfig config;
    @Mock private Client client;

    private GremlinBranchRepositoryImpl repository;

    @BeforeEach
    void setUp() throws Exception {
        repository = new GremlinBranchRepositoryImpl(config);

        // Inject mocked Gremlin client (avoid init/PostConstruct)
        Field f = GremlinBranchRepositoryImpl.class.getDeclaredField("client");
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

    private static Map<String, Object> capturedBindings(ArgumentCaptor<Map> captor) {
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) captor.getValue();
        return m;
    }

    private static BranchDTO branch(String repo, String res, String b) {
        return new BranchDTO(repo, res, b);
    }

    private static ResourceDTO resource(String repo, String res) {
        return new ResourceDTO(repo, res);
    }

    // =========================================================
    // Category Partition: saveBranch
    // Categories:
    // - submit ok | throws -> BranchException
    // =========================================================
    static Stream<Arguments> saveBranchCases() {
        return Stream.of(
                Arguments.of(false, false),
                Arguments.of(true,  true)
        );
    }

    @ParameterizedTest
    @MethodSource("saveBranchCases")
    void saveBranch_categoryPartition(boolean submitThrows, boolean expectException) {
        BranchDTO dto = branch("repo1", "res1", "main");

        if (submitThrows) stubSubmitAllGetThrows();
        else stubSubmitAllGetReturns(List.of());

        if (expectException) {
            assertThrows(BranchException.class, () -> repository.saveBranch(dto));
            Thread.interrupted();
        } else {
            assertDoesNotThrow(() -> repository.saveBranch(dto));
        }

        // Capture submit args (avoid ambiguous overload)
        ArgumentCaptor<String> queryCap = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Map> bindingsCap = ArgumentCaptor.forClass(Map.class);

        verify(client).submit(queryCap.capture(), bindingsCap.capture());

        assertTrue(queryCap.getValue().contains("addV('branch')"));
        assertTrue(queryCap.getValue().contains("HAS_BRANCH"));

        Map<String, Object> bindings = capturedBindings(bindingsCap);
        assertEquals("repo1", bindings.get("repoName"));
        assertEquals("res1", bindings.get("resName"));
        assertEquals("main", bindings.get("bName"));
    }

    // =========================================================
    // Category Partition: existsByResource
    // Categories:
    // - results empty -> false
    // - count 0 -> false
    // - count > 0 -> true
    // - throws -> BranchException
    // =========================================================
    static Stream<Arguments> existsCases() {
        return Stream.of(
                Arguments.of("EMPTY", 0L, false, null),
                Arguments.of("COUNT", 0L, false, null),
                Arguments.of("COUNT", 3L, true,  null),
                Arguments.of("THROWS",0L, false, BranchException.class)
        );
    }

    @ParameterizedTest
    @MethodSource("existsCases")
    void existsByResource_categoryPartition(String behavior,
                                            long count,
                                            boolean expected,
                                            Class<? extends Throwable> expectedEx) {

        BranchDTO dto = branch("repo1", "res1", "dev");

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
            assertThrows(expectedEx, () -> repository.existsByResource(dto));
            Thread.interrupted();
        } else {
            assertEquals(expected, repository.existsByResource(dto));
        }

        ArgumentCaptor<String> queryCap = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Map> bindingsCap = ArgumentCaptor.forClass(Map.class);

        verify(client).submit(queryCap.capture(), bindingsCap.capture());

        assertTrue(queryCap.getValue().contains(".count()"));

        Map<String, Object> bindings = capturedBindings(bindingsCap);
        assertEquals("repo1", bindings.get("repoName"));
        assertEquals("res1", bindings.get("resName"));
        assertEquals("dev", bindings.get("bName"));
    }

    // =========================================================
    // Category Partition: findBranchesByResource
    // Categories:
    // - results empty -> empty list
    // - one result valid -> list size 1
    // - multiple results -> list size N
    // - malformed props (missing branchName) -> VersionException (caught in broad catch)
    // - submit throws -> VersionException
    // =========================================================
    static Stream<Arguments> findBranchesCases() {
        return Stream.of(
                Arguments.of("EMPTY",     0, null),
                Arguments.of("ONE_OK",    1, null),
                Arguments.of("MANY_OK",   2, null),
                Arguments.of("MALFORMED", 0, VersionException.class),
                Arguments.of("THROWS",    0, VersionException.class)
        );
    }

    @ParameterizedTest
    @MethodSource("findBranchesCases")
    void findBranchesByResource_categoryPartition(String behavior,
                                                  int expectedSize,
                                                  Class<? extends Throwable> expectedEx) {

        ResourceDTO resourceDTO = resource("repo1", "res1");

        if ("THROWS".equals(behavior)) {
            stubSubmitAllGetThrows();
        } else if ("EMPTY".equals(behavior)) {
            stubSubmitAllGetReturns(List.of());
        } else if ("ONE_OK".equals(behavior)) {
            Result r = mock(Result.class);

            Map<String, List<Object>> props = Map.of(
                    "branchName", List.of("main")
            );
            when(r.getObject()).thenReturn(props);

            stubSubmitAllGetReturns(List.of(r));
        } else if ("MANY_OK".equals(behavior)) {
            Result r1 = mock(Result.class);
            Result r2 = mock(Result.class);

            when(r1.getObject()).thenReturn(Map.of("branchName", List.of("main")));
            when(r2.getObject()).thenReturn(Map.of("branchName", List.of("dev")));

            stubSubmitAllGetReturns(List.of(r1, r2));
        } else { // MALFORMED
            Result r = mock(Result.class);
            // missing "branchName" key triggers NPE inside method, then caught -> VersionException
            when(r.getObject()).thenReturn(Map.of("x", List.of("y")));
            stubSubmitAllGetReturns(List.of(r));
        }

        if (expectedEx != null) {
            assertThrows(expectedEx, () -> repository.findBranchesByResource(resourceDTO));
            Thread.interrupted();
            return;
        }

        List<BranchDTO> branches = repository.findBranchesByResource(resourceDTO);
        assertNotNull(branches);
        assertEquals(expectedSize, branches.size());

        if ("ONE_OK".equals(behavior)) {
            assertEquals("repo1", branches.get(0).getRepositoryName());
            assertEquals("res1", branches.get(0).getResourceName());
            assertEquals("main", branches.get(0).getBranchName());
        }
        if ("MANY_OK".equals(behavior)) {
            assertEquals("main", branches.get(0).getBranchName());
            assertEquals("dev", branches.get(1).getBranchName());
        }

        // Capture submit args
        ArgumentCaptor<String> queryCap = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Map> bindingsCap = ArgumentCaptor.forClass(Map.class);

        verify(client).submit(queryCap.capture(), bindingsCap.capture());

        assertTrue(queryCap.getValue().contains("valueMap('branchName')"));

        Map<String, Object> bindings = capturedBindings(bindingsCap);
        assertEquals("repo1", bindings.get("repositoryName"));
        assertEquals("res1", bindings.get("resourceName"));
    }
}