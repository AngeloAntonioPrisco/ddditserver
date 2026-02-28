package it.unisa.ddditserver.subsystems.invitation.controller;

import it.unisa.ddditserver.subsystems.auth.exceptions.*;
import it.unisa.ddditserver.subsystems.invitation.dto.InvitationDTO;
import it.unisa.ddditserver.subsystems.invitation.exceptions.AlreadyInvitedException;
import it.unisa.ddditserver.subsystems.invitation.exceptions.InvitationException;
import it.unisa.ddditserver.subsystems.invitation.exceptions.InvitationNotFoundException;
import it.unisa.ddditserver.subsystems.invitation.service.InvitationService;
import it.unisa.ddditserver.subsystems.versioning.exceptions.repo.RepositoryException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/invitations")
public class InvitationControllerImpl implements  InvitationController {

    private static final String ERROR_KEY = "error";

    private static final String DETAILS_KEY = "details";

    private InvitationService invitationService;

    public InvitationControllerImpl(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
            return null;
        }
        return bearerToken.substring(7);
    }

    @Override
    @PostMapping("/invite")
    public ResponseEntity<Map<String, String>> sendInvitation(@RequestBody InvitationDTO invitationDTO, HttpServletRequest request) {
        String token = extractToken(request);

        try {
            return invitationService.sendInvitation(invitationDTO, token);
        } catch (AuthException | RepositoryException |
                 AlreadyInvitedException  e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (InvitationException e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(ERROR_KEY, "Unexpected error during sending invitation", DETAILS_KEY, e.getMessage()));
        }
    }

    @Override
    @PostMapping("/accept")
    public ResponseEntity<Map<String, String>> acceptInvitation(@RequestBody InvitationDTO invitationDTO, HttpServletRequest request) {
        String token = extractToken(request);

        try {
            return invitationService.acceptInvitation(invitationDTO, token);
        } catch (AuthException | RepositoryException |
                 InvitationNotFoundException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (InvitationException e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(ERROR_KEY, "Unexpected error during accepting invitation", DETAILS_KEY, e.getMessage()));
        }
    }

    @Override
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> listPendingInvitations(HttpServletRequest request) {
        String token = extractToken(request);

        try {
            return invitationService.listPendingInvitations(token);
        } catch (NotLoggedUserException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (InvitationException e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(ERROR_KEY, "Unexpected error during listing pending invitations", DETAILS_KEY, e.getMessage()));
        }
    }
}
