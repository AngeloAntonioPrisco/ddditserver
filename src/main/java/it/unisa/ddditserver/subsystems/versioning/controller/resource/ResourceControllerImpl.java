package it.unisa.ddditserver.subsystems.versioning.controller.resource;

import it.unisa.ddditserver.subsystems.auth.exceptions.NotLoggedUserException;
import it.unisa.ddditserver.subsystems.versioning.dto.RepositoryDTO;
import it.unisa.ddditserver.subsystems.versioning.dto.ResourceDTO;
import it.unisa.ddditserver.subsystems.versioning.exceptions.repo.RepositoryException;
import it.unisa.ddditserver.subsystems.versioning.exceptions.resource.ExistingResourceException;
import it.unisa.ddditserver.subsystems.versioning.exceptions.resource.InvalidResourceNameException;
import it.unisa.ddditserver.subsystems.versioning.exceptions.resource.ResourceException;
import it.unisa.ddditserver.subsystems.versioning.service.resource.ResourceService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/resources")
public class ResourceControllerImpl implements ResourceController {

    private static final String DETAILS_KEY = "details";

    private static final String ERROR_KEY = "error";

    @Autowired
    private ResourceService resourceService;

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
            return null;
        }
        return bearerToken.substring(7);
    }

    @Override
    @PostMapping("/create")
    public ResponseEntity<Map<String, String>> createResource(@RequestBody ResourceDTO resourceDTO, HttpServletRequest request) {
        String token = extractToken(request);

        try {
            return resourceService.createResource(resourceDTO, token);
        } catch (RepositoryException | InvalidResourceNameException |
                 ExistingResourceException | NotLoggedUserException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (ResourceException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(ERROR_KEY, "Unexpected error during resource creation", DETAILS_KEY, e.getMessage()));
        }
    }

    @Override
    @PostMapping("/list")
    public ResponseEntity<Map<String, Object>> listResourcesByRepository(@RequestBody RepositoryDTO repositoryDTO, HttpServletRequest request) {
        String token = extractToken(request);

        try {
            return resourceService.listResourcesByRepository(repositoryDTO, token);
        } catch (RepositoryException | NotLoggedUserException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (ResourceException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(ERROR_KEY, "Unexpected error during listing resources", DETAILS_KEY, e.getMessage()));
        }
    }

    @Override
    @PostMapping("/tree")
    public ResponseEntity<Map<String, Object>> showVersionTree(@RequestBody ResourceDTO resourceDTO, HttpServletRequest request) {
        String token = extractToken(request);

        try {
            return resourceService.showVersionTree(resourceDTO, token);
        } catch (RepositoryException | InvalidResourceNameException |
                 ExistingResourceException | NotLoggedUserException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (ResourceException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(ERROR_KEY, "Unexpected error during showing version tree", DETAILS_KEY, e.getMessage()));
        }
    }
}
