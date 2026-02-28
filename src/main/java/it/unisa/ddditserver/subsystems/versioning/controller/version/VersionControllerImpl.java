package it.unisa.ddditserver.subsystems.versioning.controller.version;

import it.unisa.ddditserver.subsystems.auth.exceptions.NotLoggedUserException;
import it.unisa.ddditserver.subsystems.versioning.dto.version.VersionDTO;
import it.unisa.ddditserver.subsystems.versioning.exceptions.branch.BranchException;
import it.unisa.ddditserver.subsystems.versioning.exceptions.repo.RepositoryException;
import it.unisa.ddditserver.subsystems.versioning.exceptions.resource.ResourceException;
import it.unisa.ddditserver.subsystems.versioning.exceptions.version.*;
import it.unisa.ddditserver.subsystems.versioning.service.version.VersionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/versions")
public class VersionControllerImpl implements VersionController {

    private static final String DETAILS_KEY = "details";

    private static final String ERROR_KEY = "error";

    private VersionService versionService;

    public VersionControllerImpl(VersionService versionService) {
        this.versionService = versionService;
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
            return null;
        }
        return bearerToken.substring(7);
    }
    
    @Override
    @PostMapping("/push")
    public ResponseEntity<Map<String, String>> pushVersion(@ModelAttribute VersionDTO versionDTO, HttpServletRequest request) {
        String token = extractToken(request);

        try {
            return versionService.createVersion(versionDTO, token);
        } catch (RepositoryException | ResourceException |
                 BranchException | InvalidVersionNameException |
                 InvalidCommentException | InvalidMeshException |
                 InvalidMaterialException | NotLoggedUserException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (VersionException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(ERROR_KEY, "Unexpected error during mesh version push", DETAILS_KEY, e.getMessage()));
        }
    }

    @Override
    @PostMapping("/pull")
    public ResponseEntity<?> pullVersion(@RequestBody VersionDTO versionDTO, HttpServletRequest request) {
        String token = extractToken(request);

        try {
            return versionService.pullVersion(versionDTO, token);
        } catch (RepositoryException | ResourceException |
                 BranchException | InvalidVersionNameException |
                 InvalidCommentException | InvalidMeshException |
                 InvalidMaterialException | NotLoggedUserException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (VersionException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(ERROR_KEY, "Unexpected error during version pull", DETAILS_KEY, e.getMessage()));
        }
    }

    @Override
    @PostMapping("/metadata")
    public ResponseEntity<Map<String, Object>> showVersionMetadata(@RequestBody VersionDTO versionDTO, HttpServletRequest request) {
        String token = extractToken(request);

        try {
            return versionService.showVersionMetadata(versionDTO, token);
        } catch (RepositoryException | ResourceException |
                 BranchException | InvalidVersionNameException |
                 InvalidCommentException | InvalidMeshException |
                 InvalidMaterialException | NotLoggedUserException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (VersionException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(ERROR_KEY, "Unexpected error during metadata retrieve", DETAILS_KEY, e.getMessage()));
        }
    }
}
