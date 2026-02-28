package it.unisa.ddditserver.subsystems.versioning.controller.repo;

import it.unisa.ddditserver.subsystems.auth.exceptions.NotLoggedUserException;
import it.unisa.ddditserver.subsystems.versioning.dto.RepositoryDTO;
import it.unisa.ddditserver.subsystems.versioning.exceptions.repo.ExistingRepositoryException;
import it.unisa.ddditserver.subsystems.versioning.exceptions.repo.InvalidRepositoryNameException;
import it.unisa.ddditserver.subsystems.versioning.exceptions.repo.RepositoryException;
import it.unisa.ddditserver.subsystems.versioning.service.repo.RepositoryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/repositories")
public class RepositoryControllerImpl implements RepositoryController {

    private static final String DETAILS_KEY = "details";

    private static final String ERROR_KEY = "error";

    private RepositoryService repositoryService;

    public RepositoryControllerImpl(RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
            return null;
        }
        return bearerToken.substring(7);
    }

    @PostMapping("/create")
    public ResponseEntity<Map<String, String>> createRepository(@RequestBody RepositoryDTO repositoryDTO, HttpServletRequest request) {
        String token = extractToken(request);

        try {
            return repositoryService.createRepository(repositoryDTO, token);
        } catch (InvalidRepositoryNameException | ExistingRepositoryException | NotLoggedUserException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (RepositoryException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(ERROR_KEY, "Unexpected error during repository creation", DETAILS_KEY, e.getMessage()));
        }
    }

    @GetMapping("/owned")
    public ResponseEntity<Map<String, Object>> listOwnedRepositories(HttpServletRequest request) {
        String token = extractToken(request);

        try {
            return repositoryService.listRepositoriesOwned(token);
        } catch (NotLoggedUserException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (RepositoryException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(ERROR_KEY, "Unexpected error during listing owned repositories", DETAILS_KEY, e.getMessage()));
        }
    }

    @GetMapping("/contributed")
    public ResponseEntity<Map<String, Object>> listContributedRepositories(HttpServletRequest request) {
        String token = extractToken(request);

        try {
            return repositoryService.listRepositoriesContributed(token);
        } catch (NotLoggedUserException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (RepositoryException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(ERROR_KEY, "Unexpected error during listing contributed repositories", DETAILS_KEY, e.getMessage()));
        }
    }
}

