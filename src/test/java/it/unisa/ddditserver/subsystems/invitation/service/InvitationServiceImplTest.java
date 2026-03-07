package it.unisa.ddditserver.subsystems.invitation.service;

import it.unisa.ddditserver.db.gremlin.invitation.GremlinInvitationRepository;
import it.unisa.ddditserver.db.gremlin.versioning.repo.GremlinRepositoryRepository;
import it.unisa.ddditserver.subsystems.auth.exceptions.NotLoggedUserException;
import it.unisa.ddditserver.subsystems.invitation.dto.InvitationDTO;
import it.unisa.ddditserver.subsystems.invitation.exceptions.InvitationException;
import it.unisa.ddditserver.subsystems.versioning.exceptions.repo.RepositoryException;
import it.unisa.ddditserver.validators.auth.JWT.JWTokenValidator;
import it.unisa.ddditserver.validators.auth.user.UserValidator;
import it.unisa.ddditserver.validators.invitation.InvitationValidator;
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

    static Stream<Arguments> sendInvitationProvider() {
        return Stream.of(
                // Test Case 1: Valid token + Contributor permissions + Success
                Arguments.of("valid_token", true, false, false, null),
                // Test Case 2: Valid token + Owner permissions + Success
                Arguments.of("valid_token", false, true, false, null),
                // Test Case 3: Invalid token (Error Partition)
                Arguments.of(null, false, false, false, NotLoggedUserException.class),
                // Test Case 4: Valid token but No Permissions (Boundary: Access Denied)
                Arguments.of("valid_token", false, false, false, RepositoryException.class),
                // Test Case 5: Valid token + Permissions but DB fails (Error Partition)
                Arguments.of("valid_token", true, false, true, InvitationException.class)
        );
    }

    @ParameterizedTest
    @MethodSource("sendInvitationProvider")
    void testSendInvitation(String token, boolean isContributor, boolean isOwner, boolean dbError, Class<? extends Throwable> expectedEx) {
        InvitationDTO dto = new InvitationDTO("bob", "repo1");
        String sender = "alice";

        // Mock Token Validation
        when(jwTokenValidator.isTokenValid(token)).thenReturn(token != null ? sender : null);

        if (token != null) {
            // Mock Permissions
            when(gremlinRepositoryRepository.isContributor(any(), any())).thenReturn(isContributor);
            if (!isContributor) {
                when(gremlinRepositoryRepository.isOwner(any(), any())).thenReturn(isOwner);
            }

            // Mock DB Behavior
            if ((isContributor || isOwner) && dbError) {
                doThrow(new RuntimeException("DB Down")).when(gremlinInvitationRepository).saveInvitation(any(), any(), any());
            }
        }

        if (expectedEx != null) {
            assertThrows(expectedEx, () -> service.sendInvitation(dto, token));
        } else {
            ResponseEntity<Map<String, String>> response = service.sendInvitation(dto, token);
            assertEquals(200, response.getStatusCode().value());
            verify(gremlinInvitationRepository, times(1)).saveInvitation(any(), any(), any());
        }
    }

    static Stream<Arguments> acceptInvitationProvider() {
        return Stream.of(
                // Test Case 1: Valid process (Invitation exists -> Accepted)
                Arguments.of("valid_token", false, null),
                // Test Case 2: Invalid token
                Arguments.of(null, false, NotLoggedUserException.class),
                // Test Case 3: Database failure during acceptance
                Arguments.of("valid_token", true, RepositoryException.class)
        );
    }

    @ParameterizedTest
    @MethodSource("acceptInvitationProvider")
    void testAcceptInvitation(String token, boolean dbError, Class<? extends Throwable> expectedEx) {
        InvitationDTO dto = new InvitationDTO("senderAlice", "repo1");
        String receiver = "bob";

        when(jwTokenValidator.isTokenValid(token)).thenReturn(token != null ? receiver : null);

        if (token != null && dbError) {
            doThrow(new RuntimeException("Accept failed")).when(gremlinInvitationRepository).acceptInvitation(any(), any(), any());
        }

        if (expectedEx != null) {
            assertThrows(expectedEx, () -> service.acceptInvitation(dto, token));
        } else {
            ResponseEntity<Map<String, String>> response = service.acceptInvitation(dto, token);
            assertEquals(200, response.getStatusCode().value());
            verify(gremlinRepositoryRepository).addContributor(any(), any());
        }
    }

    static Stream<Arguments> listInvitationsProvider() {
        return Stream.of(
                // Test Case 1: Valid token + No invitations (Empty Boundary)
                Arguments.of("token", 0, false, null),
                // Test Case 2: Valid token + Multiple invitations
                Arguments.of("token", 3, false, null),
                // Test Case 3: Invalid token
                Arguments.of(null, 0, false, NotLoggedUserException.class),
                // Test Case 4: Database Error (Failure Partition)
                Arguments.of("token", 0, true, InvitationException.class)
        );
    }

    @ParameterizedTest
    @MethodSource("listInvitationsProvider")
    @SuppressWarnings("unchecked")
    void testListPendingInvitations(String token, int listSize, boolean dbError, Class<? extends Throwable> expectedEx) {
        String user = "bob";
        when(jwTokenValidator.isTokenValid(token)).thenReturn(token != null ? user : null);

        if (token != null) {
            if (dbError) {
                when(gremlinInvitationRepository.findInvitationsByUser(any())).thenThrow(new RuntimeException("Critical failure"));
            } else {
                List<InvitationDTO> mockList = Stream.generate(() -> new InvitationDTO("u", "r"))
                        .limit(listSize)
                        .toList();
                when(gremlinInvitationRepository.findInvitationsByUser(any())).thenReturn(mockList);
            }
        }

        if (expectedEx != null) {
            assertThrows(expectedEx, () -> service.listPendingInvitations(token));
        } else {
            ResponseEntity<Map<String, Object>> response = service.listPendingInvitations(token);
            List<InvitationDTO> result = (List<InvitationDTO>) response.getBody().get("invitations");
            assertEquals(listSize, result.size());
        }
    }
}