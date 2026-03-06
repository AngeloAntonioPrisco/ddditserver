package it.unisa.ddditserver.db.unit.gremlin.invitation;

import it.unisa.ddditserver.db.gremlin.JanusConfig;
import it.unisa.ddditserver.db.gremlin.invitation.GremlinInvitationRepositoryImpl;
import it.unisa.ddditserver.subsystems.auth.dto.UserDTO;
import it.unisa.ddditserver.subsystems.invitation.dto.InvitationDTO;
import it.unisa.ddditserver.subsystems.invitation.exceptions.InvitationException;
import it.unisa.ddditserver.subsystems.versioning.dto.RepositoryDTO;
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
class GremlinInvitationRepositoryImplTest {

    @Mock private JanusConfig config;
    @Mock private Client client;

    private GremlinInvitationRepositoryImpl repository;

    @BeforeEach
    void setUp() throws Exception {
        repository = new GremlinInvitationRepositoryImpl(config);

        // Inject mocked Gremlin client (avoid init/PostConstruct)
        Field f = GremlinInvitationRepositoryImpl.class.getDeclaredField("client");
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

    private static UserDTO user(String u) {
        return new UserDTO(u, null);
    }

    private static RepositoryDTO repo(String name) {
        return new RepositoryDTO(name);
    }

    // Option B: reflection field reader (no getters needed)
    private static Object readField(Object obj, String... candidates) {
        for (String name : candidates) {
            try {
                Field f = obj.getClass().getDeclaredField(name);
                f.setAccessible(true);
                return f.get(obj);
            } catch (NoSuchFieldException ignored) {
                // for empty Objects
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
        throw new AssertionError("None of the candidate fields exist: " + Arrays.toString(candidates));
    }

    // =========================================================
    // saveInvitation
    // =========================================================
    static Stream<Arguments> saveInvitationCases() {
        return Stream.of(
                Arguments.of(false, false),
                Arguments.of(true,  true)
        );
    }

    @ParameterizedTest
    @MethodSource("saveInvitationCases")
    void saveInvitation_categoryPartition(boolean submitThrows, boolean expectException) {
        UserDTO from = user("alice");
        UserDTO to = user("bob");
        RepositoryDTO repositoryDTO = repo("repo1");

        if (submitThrows) stubSubmitAllGetThrows();
        else stubSubmitAllGetReturns(List.of());

        if (expectException) {
            assertThrows(InvitationException.class, () -> repository.saveInvitation(from, to, repositoryDTO));
            Thread.interrupted();
        } else {
            assertDoesNotThrow(() -> repository.saveInvitation(from, to, repositoryDTO));
        }

        ArgumentCaptor<String> queryCap = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Map> bindingsCap = ArgumentCaptor.forClass(Map.class);

        verify(client).submit(queryCap.capture(), bindingsCap.capture());

        assertTrue(queryCap.getValue().contains("HAS_INVITED"));
        Map<String, Object> bindings = capturedBindings(bindingsCap);
        assertEquals("alice", bindings.get("fromUsername"));
        assertEquals("bob", bindings.get("toUsername"));
        assertEquals("repo1", bindings.get("repoName"));
    }

    // =========================================================
    // existsByUserAndRepository
    // =========================================================
    static Stream<Arguments> existsCases() {
        return Stream.of(
                Arguments.of("EMPTY", 0L, false, null),
                Arguments.of("COUNT", 0L, false, null),
                Arguments.of("COUNT", 2L, true,  null),
                Arguments.of("THROWS",0L, false, InvitationException.class)
        );
    }

    @ParameterizedTest
    @MethodSource("existsCases")
    void existsByUserAndRepository_categoryPartition(String behavior,
                                                     long count,
                                                     boolean expected,
                                                     Class<? extends Throwable> expectedEx) {
        UserDTO from = user("alice");
        UserDTO to = user("bob");
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
            assertThrows(expectedEx, () -> repository.existsByUserAndRepository(from, to, repositoryDTO));
            Thread.interrupted();
        } else {
            assertEquals(expected, repository.existsByUserAndRepository(from, to, repositoryDTO));
        }

        ArgumentCaptor<String> queryCap = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Map> bindingsCap = ArgumentCaptor.forClass(Map.class);

        verify(client).submit(queryCap.capture(), bindingsCap.capture());

        assertTrue(queryCap.getValue().contains(".outE('HAS_INVITED'"));
        assertTrue(queryCap.getValue().contains(".count()"));

        Map<String, Object> bindings = capturedBindings(bindingsCap);
        assertEquals("alice", bindings.get("fromUsername"));
        assertEquals("bob", bindings.get("toUsername"));
        assertEquals("repo1", bindings.get("repoName"));
    }

    // =========================================================
    // acceptInvitation
    // =========================================================
    static Stream<Arguments> acceptCases() {
        return Stream.of(
                Arguments.of(false, false),
                Arguments.of(true,  true)
        );
    }

    @ParameterizedTest
    @MethodSource("acceptCases")
    void acceptInvitation_categoryPartition(boolean submitThrows, boolean expectException) {
        UserDTO from = user("alice");
        UserDTO to = user("bob");
        RepositoryDTO repositoryDTO = repo("repo1");

        if (submitThrows) stubSubmitAllGetThrows();
        else stubSubmitAllGetReturns(List.of());

        if (expectException) {
            assertThrows(InvitationException.class, () -> repository.acceptInvitation(from, to, repositoryDTO));
            Thread.interrupted();
        } else {
            assertDoesNotThrow(() -> repository.acceptInvitation(from, to, repositoryDTO));
        }

        ArgumentCaptor<String> queryCap = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Map> bindingsCap = ArgumentCaptor.forClass(Map.class);

        verify(client).submit(queryCap.capture(), bindingsCap.capture());

        assertTrue(queryCap.getValue().contains("property('status', 'accepted')"));

        Map<String, Object> bindings = capturedBindings(bindingsCap);
        assertEquals("alice", bindings.get("fromUsername"));
        assertEquals("bob", bindings.get("toUsername"));
        assertEquals("repo1", bindings.get("repositoryName"));
    }

    // =========================================================
    // isAcceptedInvitation
    // =========================================================
    static Stream<Arguments> acceptedCases() {
        return Stream.of(
                Arguments.of("EMPTY", 0L, false, null),
                Arguments.of("COUNT", 0L, false, null),
                Arguments.of("COUNT", 1L, true,  null),
                Arguments.of("THROWS",0L, false, InvitationException.class)
        );
    }

    @ParameterizedTest
    @MethodSource("acceptedCases")
    void isAcceptedInvitation_categoryPartition(String behavior,
                                                long count,
                                                boolean expected,
                                                Class<? extends Throwable> expectedEx) {
        UserDTO from = user("alice");
        UserDTO to = user("bob");
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
            assertThrows(expectedEx, () -> repository.isAcceptedInvitation(from, to, repositoryDTO));
            Thread.interrupted();
        } else {
            assertEquals(expected, repository.isAcceptedInvitation(from, to, repositoryDTO));
        }

        ArgumentCaptor<String> queryCap = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Map> bindingsCap = ArgumentCaptor.forClass(Map.class);

        verify(client).submit(queryCap.capture(), bindingsCap.capture());

        assertTrue(queryCap.getValue().contains("has('status', 'accepted')"));
        assertTrue(queryCap.getValue().contains(".count()"));

        Map<String, Object> bindings = capturedBindings(bindingsCap);
        assertEquals("alice", bindings.get("fromUsername"));
        assertEquals("bob", bindings.get("toUsername"));
        assertEquals("repo1", bindings.get("repositoryName"));
    }

    // =========================================================
    // findInvitationsByUser
    //
    // IMPORTANT: repository does invitations.add(new InvitationDTO(fromUsername, repositoryName))
    // but InvitationDTO has fields:
    //   - toUsername
    //   - repositoryName
    // so fromUsername ends up in InvitationDTO.toUsername
    // =========================================================
    static Stream<Arguments> findInvitationsCases() {
        return Stream.of(
                Arguments.of("EMPTY",   0, null),
                Arguments.of("OK_OBJ",  1, null),
                Arguments.of("OK_LIST", 1, null),
                Arguments.of("MISSING", 0, null),
                Arguments.of("NOTMAP",  0, null),
                Arguments.of("THROWS",  0, InvitationException.class)
        );
    }

    @ParameterizedTest
    @MethodSource("findInvitationsCases")
    void findInvitationsByUser_categoryPartition(String behavior,
                                                 int expectedSize,
                                                 Class<? extends Throwable> expectedEx) {
        UserDTO to = user("bob");

        if ("THROWS".equals(behavior)) {
            stubSubmitAllGetThrows();
        } else if ("EMPTY".equals(behavior)) {
            stubSubmitAllGetReturns(List.of());
        } else if ("OK_OBJ".equals(behavior)) {
            Map<String, Object> map = new HashMap<>();
            map.put("fromUsername", "alice");
            map.put("repositoryName", "repo1");

            Result r = mock(Result.class);
            when(r.getObject()).thenReturn(map);

            stubSubmitAllGetReturns(List.of(r));
        } else if ("OK_LIST".equals(behavior)) {
            Map<String, Object> map = new HashMap<>();
            map.put("fromUsername", List.of("alice"));
            map.put("repositoryName", List.of("repo1"));

            Result r = mock(Result.class);
            when(r.getObject()).thenReturn(map);

            stubSubmitAllGetReturns(List.of(r));
        } else if ("MISSING".equals(behavior)) {
            Map<String, Object> map = new HashMap<>();
            map.put("fromUsername", "alice"); // missing repositoryName => filtered out

            Result r = mock(Result.class);
            when(r.getObject()).thenReturn(map);

            stubSubmitAllGetReturns(List.of(r));
        } else { // NOTMAP
            Result r = mock(Result.class);
            when(r.getObject()).thenReturn("not-a-map");
            stubSubmitAllGetReturns(List.of(r));
        }

        if (expectedEx != null) {
            assertThrows(expectedEx, () -> repository.findInvitationsByUser(to));
            Thread.interrupted();
            return;
        }

        List<InvitationDTO> invitations = repository.findInvitationsByUser(to);
        assertNotNull(invitations);
        assertEquals(expectedSize, invitations.size());

        if (expectedSize == 1) {
            InvitationDTO inv = invitations.get(0);
            assertNotNull(inv);

            // Reflection: InvitationDTO fields are exactly "toUsername" and "repositoryName"
            assertEquals("alice", readField(inv, "toUsername"));
            assertEquals("repo1", readField(inv, "repositoryName"));
        }

        ArgumentCaptor<String> queryCap = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Map> bindingsCap = ArgumentCaptor.forClass(Map.class);

        verify(client).submit(queryCap.capture(), bindingsCap.capture());

        assertTrue(queryCap.getValue().contains("inE('HAS_INVITED')"));
        Map<String, Object> bindings = capturedBindings(bindingsCap);
        assertEquals("bob", bindings.get("toUsername"));
    }
}