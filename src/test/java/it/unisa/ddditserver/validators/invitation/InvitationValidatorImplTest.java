package it.unisa.ddditserver.validators.invitation;

import it.unisa.ddditserver.db.gremlin.invitation.GremlinInvitationRepository;
import it.unisa.ddditserver.db.gremlin.versioning.repo.GremlinRepositoryRepository;
import it.unisa.ddditserver.subsystems.invitation.exceptions.AlreadyInvitedException;
import it.unisa.ddditserver.subsystems.invitation.exceptions.InvitationException;
import it.unisa.ddditserver.validators.ValidationResult;
import it.unisa.ddditserver.validators.versioning.repo.RepositoryValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvitationValidatorImplTest {

    @Mock private RepositoryValidator repositoryValidator;
    @Mock private GremlinInvitationRepository gremlinService;
    @Mock private GremlinRepositoryRepository gremlinRepositoryRepository;

    private InvitationValidatorImpl validator;

    @BeforeEach
    void setUp() {
        validator = new InvitationValidatorImpl(repositoryValidator, gremlinService, gremlinRepositoryRepository);
    }

    static Stream<Arguments> validateInvitationProvider() {
        return Stream.of(
                // Case: Alice invites Alice (Self-invitation error)
                Arguments.of("alice", "alice", true, true, InvitationException.class),
                // Case: Bob is already Owner
                Arguments.of("alice", "bob", true, false, InvitationException.class),
                // Case: Bob is already Contributor
                Arguments.of("alice", "bob", false, true, InvitationException.class),
                // Case: Repository structure invalid
                Arguments.of("alice", "bob", false, false, null),
                // Case: Success
                Arguments.of("alice", "bob", false, false, null)
        );
    }

    @ParameterizedTest(name = "Invite {0} to {1} - Owner:{2}, Contrib:{3}")
    @MethodSource("validateInvitationProvider")
    void testValidateInvitation(String from, String to, boolean isOwner, boolean isContributor, Class<? extends Throwable> expectedEx) {
        InvitationValidationDTO dto = new InvitationValidationDTO(from, to, "repo1");

        if (!from.equals(to)) {

            when(gremlinRepositoryRepository.isContributor(any(), any())).thenReturn(isContributor);

            if (!isContributor) {
                when(gremlinRepositoryRepository.isOwner(any(), any())).thenReturn(isOwner);
            }

            if (!isContributor && !isOwner) {
                when(repositoryValidator.validate(any())).thenReturn(ValidationResult.valid());
            }
        }

        if (expectedEx != null) {
            assertThrows(expectedEx, () -> validator.validateInvitation(dto));
        } else {
            ValidationResult result = validator.validateInvitation(dto);
            assertTrue(result.isValid());
        }
    }

    static Stream<Arguments> pendingInvitationProvider() {
        return Stream.of(
                // accepted, existsFlag, existsCallInDB, expectedEx
                Arguments.of(true,  false, false, AlreadyInvitedException.class),
                Arguments.of(false, false, true,  AlreadyInvitedException.class),
                Arguments.of(false, true,  false, AlreadyInvitedException.class),
                Arguments.of(false, false, false, null)
        );
    }

    @ParameterizedTest(name = "Pending - Accepted:{0}, Flag:{1}, DBExists:{2}")
    @MethodSource("pendingInvitationProvider")
    void testValidatePendingInvitation(boolean isAccepted, boolean existsFlag, boolean dbExists, Class<? extends Throwable> expectedEx) {
        InvitationValidationDTO dto = new InvitationValidationDTO("alice", "bob", "repo1");

        when(gremlinService.isAcceptedInvitation(any(), any(), any())).thenReturn(isAccepted);
        if (!isAccepted) {
            when(gremlinService.existsByUserAndRepository(any(), any(), any())).thenReturn(dbExists);
        }

        if (expectedEx != null) {
            assertThrows(expectedEx, () -> validator.validatePendingInvitation(dto, existsFlag));
        } else {
            assertDoesNotThrow(() -> validator.validatePendingInvitation(dto, existsFlag));
        }
    }
}