package it.unisa.ddditserver.db.unit.gremlin.auth;

import it.unisa.ddditserver.db.gremlin.JanusConfig;
import it.unisa.ddditserver.db.gremlin.auth.GremlinAuthRepositoryImpl;
import it.unisa.ddditserver.subsystems.auth.dto.UserDTO;
import it.unisa.ddditserver.subsystems.auth.exceptions.AuthException;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GremlinAuthRepositoryImplTest {

    @Mock private JanusConfig config;
    @Mock private Client client;

    private GremlinAuthRepositoryImpl repository;

    @BeforeEach
    void setUp() throws Exception {
        repository = new GremlinAuthRepositoryImpl(config);

        // Inject mocked Gremlin client (avoid init/PostConstruct)
        Field f = GremlinAuthRepositoryImpl.class.getDeclaredField("client");
        f.setAccessible(true);
        f.set(repository, client);
    }

    // ------------------------------------------------------------
    // Helper to stub client.submit(...).all().get() chain
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

    // =========================================================
    // Category Partition: saveUser
    // Categories:
    // - submit chain: ok | throws
    // =========================================================
    static Stream<Arguments> saveUserCases() {
        return Stream.of(
                Arguments.of(false, false),
                Arguments.of(true,  true)
        );
    }

    @ParameterizedTest
    @MethodSource("saveUserCases")
    void saveUser_categoryPartition(boolean submitThrows, boolean expectException) {
        UserDTO user = new UserDTO("mario", "pwd");

        if (submitThrows) {
            stubSubmitAllGetThrows();
        } else {
            stubSubmitAllGetReturns(List.of()); // saveUser just waits for completion
        }

        if (expectException) {
            assertThrows(AuthException.class, () -> repository.saveUser(user));
            // clear interrupt flag set by repository in catch
            Thread.interrupted();
        } else {
            assertDoesNotThrow(() -> repository.saveUser(user));
        }

        // Avoid "ambiguous submit" by capturing args
        ArgumentCaptor<String> queryCap = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Map> bindingsCap = ArgumentCaptor.forClass(Map.class);

        verify(client).submit(queryCap.capture(), bindingsCap.capture());

        assertTrue(queryCap.getValue().contains("g.addV('user')"));

        Map<String, Object> bindings = capturedBindings(bindingsCap);
        assertTrue(bindings.containsKey("repoId"));
        assertTrue(bindings.containsKey("username"));
        assertTrue(bindings.containsKey("password"));
    }

    // =========================================================
    // Category Partition: findByUser
    // Categories:
    // - results: empty | valid map | throws
    // =========================================================
    static Stream<Arguments> findByUserCases() {
        return Stream.of(
                Arguments.of("EMPTY", null,   null,   AuthException.class),
                Arguments.of("OK",    "mario","pwd",  null),
                Arguments.of("THROWS",null,   null,   AuthException.class)
        );
    }

    @ParameterizedTest
    @MethodSource("findByUserCases")
    void findByUser_categoryPartition(String behavior,
                                      String fetchedUsername,
                                      String fetchedPassword,
                                      Class<? extends Throwable> expectedEx) {

        UserDTO input = new UserDTO("mario", null);

        if ("THROWS".equals(behavior)) {
            stubSubmitAllGetThrows();
        } else if ("EMPTY".equals(behavior)) {
            stubSubmitAllGetReturns(List.of());
        } else { // OK
            @SuppressWarnings("unchecked")
            Map<Object, List<Object>> props = Map.of(
                    "username", List.of(fetchedUsername),
                    "password", List.of(fetchedPassword)
            );

            Result r = mock(Result.class);
            when(r.get(Map.class)).thenReturn(props);

            stubSubmitAllGetReturns(List.of(r));
        }

        if (expectedEx != null) {
            assertThrows(expectedEx, () -> repository.findByUser(input));
            Thread.interrupted(); // clear interrupt flag if set
        } else {
            UserDTO out = repository.findByUser(input);
            assertNotNull(out);
            assertEquals(fetchedUsername, out.getUsername());
            assertEquals(fetchedPassword, out.getPassword());
        }

        // Capture submit args (avoid ambiguous overload)
        ArgumentCaptor<String> queryCap = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Map> bindingsCap = ArgumentCaptor.forClass(Map.class);

        verify(client).submit(queryCap.capture(), bindingsCap.capture());

        assertTrue(queryCap.getValue().contains("valueMap"));

        Map<String, Object> bindings = capturedBindings(bindingsCap);
        assertTrue(bindings.containsKey("username"));
    }

    // =========================================================
    // Category Partition: existsByUser
    // Categories:
    // - results empty -> false
    // - count = 0 -> false
    // - count > 0 -> true
    // - throws -> AuthException
    // =========================================================
    static Stream<Arguments> existsByUserCases() {
        return Stream.of(
                Arguments.of("EMPTY", 0L, false, null),
                Arguments.of("COUNT", 0L, false, null),
                Arguments.of("COUNT", 2L, true,  null),
                Arguments.of("THROWS",0L, false, AuthException.class)
        );
    }

    @ParameterizedTest
    @MethodSource("existsByUserCases")
    void existsByUser_categoryPartition(String behavior,
                                        long count,
                                        boolean expected,
                                        Class<? extends Throwable> expectedEx) {

        UserDTO input = new UserDTO("mario", null);

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
            assertThrows(expectedEx, () -> repository.existsByUser(input));
            Thread.interrupted(); // clear interrupt flag if set
        } else {
            assertEquals(expected, repository.existsByUser(input));
        }

        // Capture submit args (avoid ambiguous overload)
        ArgumentCaptor<String> queryCap = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Map> bindingsCap = ArgumentCaptor.forClass(Map.class);

        verify(client).submit(queryCap.capture(), bindingsCap.capture());

        assertTrue(queryCap.getValue().contains(".count()"));

        Map<String, Object> bindings = capturedBindings(bindingsCap);
        assertTrue(bindings.containsKey("username"));
    }
}