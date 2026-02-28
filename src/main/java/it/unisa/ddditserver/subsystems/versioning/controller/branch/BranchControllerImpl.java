package it.unisa.ddditserver.subsystems.versioning.controller.branch;

import it.unisa.ddditserver.subsystems.auth.exceptions.NotLoggedUserException;
import it.unisa.ddditserver.subsystems.versioning.dto.BranchDTO;
import it.unisa.ddditserver.subsystems.versioning.dto.ResourceDTO;
import it.unisa.ddditserver.subsystems.versioning.exceptions.branch.BranchException;
import it.unisa.ddditserver.subsystems.versioning.exceptions.branch.ExistingBranchException;
import it.unisa.ddditserver.subsystems.versioning.exceptions.branch.InvalidBranchNameException;
import it.unisa.ddditserver.subsystems.versioning.exceptions.repo.RepositoryException;
import it.unisa.ddditserver.subsystems.versioning.exceptions.resource.ResourceException;
import it.unisa.ddditserver.subsystems.versioning.service.branch.BranchService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/branches")
public class BranchControllerImpl implements BranchController {

    private static final String ERROR_KEY = "error";

    private BranchService branchService;

    public BranchControllerImpl(BranchService branchService) {
        this.branchService = branchService;
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
            return null;
        }
        return bearerToken.substring(7);
    }

    @Override
    @PostMapping("/create")
    public ResponseEntity<Map<String, String>> createResource(@RequestBody BranchDTO branchDTO, HttpServletRequest request) {
        String token = extractToken(request);

        try {
            return branchService.createBranch(branchDTO, token);
        } catch (RepositoryException | ResourceException |
                 InvalidBranchNameException | ExistingBranchException |
                 NotLoggedUserException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (BranchException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(ERROR_KEY, "Unexpected error during branch creation", "details", e.getMessage()));
        }
    }

    @Override
    @PostMapping("/list")
    public ResponseEntity<Map<String, Object>> listBranchesByResource(@RequestBody ResourceDTO resourceDTO, HttpServletRequest request) {
        String token = extractToken(request);

        try {
            return branchService.listBranchesByResource(resourceDTO, token);
        } catch (RepositoryException | ResourceException | NotLoggedUserException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (BranchException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(ERROR_KEY, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(ERROR_KEY, "Unexpected error during listing branches", "details", e.getMessage()));
        }
    }
}
