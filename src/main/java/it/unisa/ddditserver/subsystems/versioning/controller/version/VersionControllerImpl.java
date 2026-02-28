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
                    .body(Map.of("error", e.getMessage()));
        } catch (VersionException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Unexpected error during mesh version push", "details", e.getMessage()));
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
                    .body(Map.of("error", e.getMessage()));
        } catch (VersionException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Unexpected error during version pull", "details", e.getMessage()));
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
                    .body(Map.of("error", e.getMessage()));
        } catch (VersionException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Unexpected error during metadata retrieve", "details", e.getMessage()));
        }
    }
}
