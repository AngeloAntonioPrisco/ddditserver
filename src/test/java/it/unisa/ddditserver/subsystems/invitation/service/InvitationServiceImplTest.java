package it.unisa.ddditserver.subsystems.invitation.service;

import it.unisa.ddditserver.db.gremlin.invitation.GremlinInvitationRepository;
import it.unisa.ddditserver.db.gremlin.versioning.repo.GremlinRepositoryRepository;
import it.unisa.ddditserver.subsystems.auth.dto.UserDTO;
import it.unisa.ddditserver.subsystems.auth.exceptions.NotLoggedUserException;
import it.unisa.ddditserver.subsystems.invitation.dto.InvitationDTO;
import it.unisa.ddditserver.subsystems.invitation.exceptions.InvitationException;
import it.unisa.ddditserver.subsystems.versioning.dto.RepositoryDTO;
import it.unisa.ddditserver.subsystems.versioning.exceptions.repo.RepositoryException;
import it.unisa.ddditserver.validators.auth.JWT.JWTokenValidator;
import it.unisa.ddditserver.validators.auth.user.UserValidationDTO;
import it.unisa.ddditserver.validators.auth.user.UserValidator;
import it.unisa.ddditserver.validators.invitation.InvitationValidationDTO;
import it.unisa.ddditserver.validators.invitation.InvitationValidator;
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
class InvitationServiceImplTest {

    @Mock private GremlinInvitationRepository gremlinInvitationRepository;
    @Mock private GremlinRepositoryRepository gremlinRepositoryRepository;
    @Mock private JWTokenValidator jwTokenValidator;
    @Mock private UserValidator userValidator;
    @Mock private InvitationValidator invitationValidator;

    private InvitationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InvitationServiceImpl(
                gremlinInvitationRepository,
                gremlinRepositoryRepository,
                invitationValidator,
                jwTokenValidator,
                userValidator
        );
    }

    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------
    private static InvitationDTO invitation(String toUsername, String repositoryName) {
        return new InvitationDTO(toUsername, repositoryName);
    }

    // =========================================================
    // Category Partition: sendInvitation
    //
    // Categories:
    // - token valid | invalid
    // - permission: contributor/owner | denied
    // - repository save: ok | throws
    // =========================================================
    static Stream<Arguments> sendInvitationCases() {
        return Stream.of(
                Arguments.of("VALID_OK",        null),
                Arguments.of("INVALID_TOKEN",   NotLoggedUserException.class),
                Arguments.of("NO_PERMISSION",   RepositoryException.class),
                Arguments.of("REPO_THROWS",     InvitationException.class)
        );
    }

    @ParameterizedTest
    @MethodSource("sendInvitationCases")
    void sendInvitation_categoryPartition(String behavior,
                                          Class<? extends Throwable> expectedEx) {

        InvitationDTO dto = invitation("bob", "repo1");
        String token = "token";
        String retrievedUsername = "alice";

        if (!"INVALID_TOKEN".equals(behavior)) {
            when(jwTokenValidator.isTokenValid(token)).thenReturn(retrievedUsername);
        } else {
            when(jwTokenValidator.isTokenValid(token)).thenReturn(null);
        }

        if ("VALID_OK".equals(behavior) || "REPO_THROWS".equals(behavior)) {
            when(gremlinRepositoryRepository.isContributor(any(RepositoryDTO.class), any(UserDTO.class))).thenReturn(true);
        } else if ("NO_PERMISSION".equals(behavior)) {
            when(gremlinRepositoryRepository.isContributor(any(RepositoryDTO.class), any(UserDTO.class))).thenReturn(false);
            when(gremlinRepositoryRepository.isOwner(any(RepositoryDTO.class), any(UserDTO.class))).thenReturn(false);
        }

        if ("REPO_THROWS".equals(behavior)) {
            doThrow(new RuntimeException("boom"))
                    .when(gremlinInvitationRepository)
                    .saveInvitation(any(UserDTO.class), any(UserDTO.class), any(RepositoryDTO.class));
        }

        if (expectedEx != null) {
            assertThrows(expectedEx, () -> service.sendInvitation(dto, token));
            return;
        }

        ResponseEntity<Map<String, String>> response = service.sendInvitation(dto, token);

        assertNotNull(response);
        assertNotNull(response.getBody());
        assertEquals("Invitation send successfully to bob for repo1 repository", response.getBody().get("message"));

        verify(userValidator).validateExistence(new UserValidationDTO("alice", null), true);
        verify(userValidator).validateExistence(new UserValidationDTO("bob", null), true);
        verify(invitationValidator).validateInvitation(new InvitationValidationDTO("alice", "bob", "repo1"));
        verify(invitationValidator).validatePendingInvitation(new InvitationValidationDTO("alice", "bob", "repo1"), false);

        ArgumentCaptor<UserDTO> fromCap = ArgumentCaptor.forClass(UserDTO.class);
        ArgumentCaptor<UserDTO> toCap = ArgumentCaptor.forClass(UserDTO.class);
        ArgumentCaptor<RepositoryDTO> repoCap = ArgumentCaptor.forClass(RepositoryDTO.class);

        verify(gremlinInvitationRepository).saveInvitation(fromCap.capture(), toCap.capture(), repoCap.capture());

        assertEquals("alice", fromCap.getValue().getUsername());
        assertEquals("bob", toCap.getValue().getUsername());
        assertEquals("repo1", repoCap.getValue().getRepositoryName());
    }

    // =========================================================
    // Category Partition: acceptInvitation
    //
    // Categories:
    // - token valid | invalid
    // - pending invitation present | repository operation throws
    // =========================================================
    static Stream<Arguments> acceptInvitationCases() {
        return Stream.of(
                Arguments.of("VALID_OK",      null),
                Arguments.of("INVALID_TOKEN", NotLoggedUserException.class),
                Arguments.of("REPO_THROWS",   RepositoryException.class)
        );
    }

    @ParameterizedTest
    @MethodSource("acceptInvitationCases")
    void acceptInvitation_categoryPartition(String behavior,
                                            Class<? extends Throwable> expectedEx) {

        // In acceptInvitation, dto.toUsername is the original sender
        InvitationDTO dto = invitation("alice", "repo1");
        String token = "token";
        String retrievedUsername = "bob";

        if (!"INVALID_TOKEN".equals(behavior)) {
            when(jwTokenValidator.isTokenValid(token)).thenReturn(retrievedUsername);
        } else {
            when(jwTokenValidator.isTokenValid(token)).thenReturn(null);
        }

        if ("REPO_THROWS".equals(behavior)) {
            doThrow(new RuntimeException("boom"))
                    .when(gremlinInvitationRepository)
                    .acceptInvitation(any(UserDTO.class), any(UserDTO.class), any(RepositoryDTO.class));
        }

        if (expectedEx != null) {
            assertThrows(expectedEx, () -> service.acceptInvitation(dto, token));
            return;
        }

        ResponseEntity<Map<String, String>> response = service.acceptInvitation(dto, token);

        assertNotNull(response);
        assertNotNull(response.getBody());
        assertEquals("Invitation to repo1 repository accepted successfully", response.getBody().get("message"));

        verify(userValidator).validateExistence(new UserValidationDTO("bob", null), true);
        verify(userValidator).validateExistence(new UserValidationDTO("alice", null), true);

        InvitationValidationDTO expectedValidation = new InvitationValidationDTO("alice", "bob", "repo1");
        verify(invitationValidator).validateInvitation(expectedValidation);
        verify(invitationValidator).validatePendingInvitation(expectedValidation, true);

        ArgumentCaptor<UserDTO> fromCap = ArgumentCaptor.forClass(UserDTO.class);
        ArgumentCaptor<UserDTO> toCap = ArgumentCaptor.forClass(UserDTO.class);
        ArgumentCaptor<RepositoryDTO> repoCap = ArgumentCaptor.forClass(RepositoryDTO.class);

        verify(gremlinInvitationRepository).acceptInvitation(fromCap.capture(), toCap.capture(), repoCap.capture());

        assertEquals("alice", fromCap.getValue().getUsername());
        assertEquals("bob", toCap.getValue().getUsername());
        assertEquals("repo1", repoCap.getValue().getRepositoryName());

        ArgumentCaptor<RepositoryDTO> addRepoCap = ArgumentCaptor.forClass(RepositoryDTO.class);
        ArgumentCaptor<UserDTO> addUserCap = ArgumentCaptor.forClass(UserDTO.class);

        verify(gremlinRepositoryRepository).addContributor(addRepoCap.capture(), addUserCap.capture());

        assertEquals("repo1", addRepoCap.getValue().getRepositoryName());
        assertEquals("bob", addUserCap.getValue().getUsername());
    }

    // =========================================================
    // Category Partition: listPendingInvitations
    //
    // Categories:
    // - token valid | invalid
    // - repository returns empty | some invitations | throws
    // =========================================================
    static Stream<Arguments> listPendingInvitationsCases() {
        return Stream.of(
                Arguments.of("EMPTY",   0, null),
                Arguments.of("SOME",    2, null),
                Arguments.of("INVALID", 0, NotLoggedUserException.class),
                Arguments.of("THROWS",  0, InvitationException.class)
        );
    }

    @ParameterizedTest
    @MethodSource("listPendingInvitationsCases")
    void listPendingInvitations_categoryPartition(String behavior,
                                                  int expectedSize,
                                                  Class<? extends Throwable> expectedEx) {

        String token = "token";
        String retrievedUsername = "bob";

        if (!"INVALID".equals(behavior)) {
            when(jwTokenValidator.isTokenValid(token)).thenReturn(retrievedUsername);
        } else {
            when(jwTokenValidator.isTokenValid(token)).thenReturn(null);
        }

        if ("EMPTY".equals(behavior)) {
            when(gremlinInvitationRepository.findInvitationsByUser(any(UserDTO.class))).thenReturn(List.of());
        } else if ("SOME".equals(behavior)) {
            when(gremlinInvitationRepository.findInvitationsByUser(any(UserDTO.class)))
                    .thenReturn(List.of(
                            new InvitationDTO("alice", "repo1"),
                            new InvitationDTO("charlie", "repo2")
                    ));
        } else if ("THROWS".equals(behavior)) {
            when(gremlinInvitationRepository.findInvitationsByUser(any(UserDTO.class)))
                    .thenThrow(new RuntimeException("boom"));
        }

        if (expectedEx != null) {
            assertThrows(expectedEx, () -> service.listPendingInvitations(token));
            return;
        }

        ResponseEntity<Map<String, Object>> response = service.listPendingInvitations(token);

        assertNotNull(response);
        assertNotNull(response.getBody());
        assertEquals("Pending invitations found successfully", response.getBody().get("message"));

        @SuppressWarnings("unchecked")
        List<InvitationDTO> invitations = (List<InvitationDTO>) response.getBody().get("invitations");

        assertNotNull(invitations);
        assertEquals(expectedSize, invitations.size());

        ArgumentCaptor<UserDTO> userCap = ArgumentCaptor.forClass(UserDTO.class);
        verify(gremlinInvitationRepository).findInvitationsByUser(userCap.capture());
        assertEquals("bob", userCap.getValue().getUsername());
    }
}