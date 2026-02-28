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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class InvitationServiceImpl implements  InvitationService {

    private GremlinInvitationRepository gremlinInvitationRepository;
    private GremlinRepositoryRepository gremlinRepositoryRepository;
    private JWTokenValidator jwTokenValidator;
    private UserValidator userValidator;
    private InvitationValidator invitationValidator;

    public InvitationServiceImpl(GremlinInvitationRepository gremlinInvitationRepository, GremlinRepositoryRepository gremlinRepositoryRepository, InvitationValidator invitationValidator, JWTokenValidator jwTokenValidator, UserValidator userValidator) {
        this.gremlinInvitationRepository = gremlinInvitationRepository;
        this.gremlinRepositoryRepository = gremlinRepositoryRepository;
        this.invitationValidator = invitationValidator;
        this.jwTokenValidator = jwTokenValidator;
        this.userValidator = userValidator;
    }

    private void checkUserStatus(String repositoryName, String username) {
        RepositoryDTO repositoryDTO = new RepositoryDTO(repositoryName);
        UserDTO userDTO = new UserDTO(username, null);

        if (!gremlinRepositoryRepository.isContributor(repositoryDTO, userDTO) && !gremlinRepositoryRepository.isOwner(repositoryDTO, userDTO)) {
            throw new RepositoryException("Permission denied because " + username + " is not a contributor or the owner of " + repositoryName + " repository");
        }
    }

    @Override
    public ResponseEntity<Map<String, String>> sendInvitation(InvitationDTO invitationDTO, String token) {
        String retrievedUsername = jwTokenValidator.isTokenValid(token);
        String repositoryName = invitationDTO.getRepositoryName();
        String toUsername = invitationDTO.getToUsername();

        if (retrievedUsername == null) {
            throw new NotLoggedUserException("Missing, invalid, or expired Authorization token");
        }

        checkUserStatus(repositoryName, retrievedUsername);

        UserValidationDTO fromUserValidationDTO = new UserValidationDTO(retrievedUsername, null);
        UserValidationDTO toUserValidationDTO = new UserValidationDTO(toUsername, null);

        // Check if users exist in graph database
        userValidator.validateExistence(fromUserValidationDTO, true);
        userValidator.validateExistence(toUserValidationDTO, true);

        InvitationValidationDTO invitationValidationDTO = new InvitationValidationDTO(retrievedUsername, toUsername, repositoryName);

        // Check if invitation's data are well-formed
        invitationValidator.validateInvitation(invitationValidationDTO);

        // Check if invitation is already been sent
        invitationValidator.validatePendingInvitation(invitationValidationDTO, false);

        try {
            gremlinInvitationRepository.saveInvitation(
                    new UserDTO(retrievedUsername, null),
                    new UserDTO(toUsername, null),
                    new RepositoryDTO(repositoryName));
        } catch (Exception e) {
            throw new InvitationException(e.getMessage());
        }

        Map<String, String> response = new HashMap<>();
        response.put("message", "Invitation send successfully to " + toUsername + " for " + repositoryName +  " repository");

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Map<String, String>> acceptInvitation(InvitationDTO invitationDTO, String token) {
        String retrievedUsername = jwTokenValidator.isTokenValid(token);
        String repositoryName = invitationDTO.getRepositoryName();
        String fromUsername = invitationDTO.getToUsername(); // Also if it is called toUsername it refers to the original user who sent the invitation

        if (retrievedUsername == null) {
            throw new NotLoggedUserException("Missing, invalid, or expired Authorization token");
        }

        UserValidationDTO toUserValidationDTO = new UserValidationDTO(retrievedUsername, null);
        UserValidationDTO fromUserValidationDTO = new UserValidationDTO(fromUsername, null);

        // Check if users exist in graph database
        userValidator.validateExistence(toUserValidationDTO, true);
        userValidator.validateExistence(fromUserValidationDTO, true);

        InvitationValidationDTO invitationValidationDTO = new InvitationValidationDTO(fromUsername, retrievedUsername, repositoryName);

        // Check if invitation's data are well-formed
        invitationValidator.validateInvitation(invitationValidationDTO);

        // Check if invitation is already been sent
        invitationValidator.validatePendingInvitation(invitationValidationDTO, true);

        try {
            gremlinInvitationRepository.acceptInvitation(
                    new UserDTO(fromUsername, null),
                    new UserDTO(retrievedUsername, null),
                    new RepositoryDTO(repositoryName));
            gremlinRepositoryRepository.addContributor(new RepositoryDTO(repositoryName), new UserDTO(retrievedUsername, null));
        } catch (Exception e) {
            throw new RepositoryException(e.getMessage());
        }

        Map<String, String> response = new HashMap<>();
        response.put("message", "Invitation to " + repositoryName + " repository accepted successfully");

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Map<String, Object>> listPendingInvitations(String token) {
        String retrievedUsername = jwTokenValidator.isTokenValid(token);

        if (retrievedUsername == null) {
            throw new NotLoggedUserException("Missing, invalid, or expired Authorization token");
        }

        List<InvitationDTO> pendingInvitations;

        try {
            pendingInvitations = gremlinInvitationRepository.findInvitationsByUser(new UserDTO(retrievedUsername, null));
        } catch (Exception e) {
            throw new InvitationException(e.getMessage());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Pending invitations found successfully");
        response.put("invitations", pendingInvitations);

        return ResponseEntity.ok(response);
    }
}
