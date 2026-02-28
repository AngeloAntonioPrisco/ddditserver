package it.unisa.ddditserver.subsystems.versioning.service.branch;

import it.unisa.ddditserver.db.gremlin.versioning.branch.GremlinBranchRepository;
import it.unisa.ddditserver.db.gremlin.versioning.repo.GremlinRepositoryRepository;
import it.unisa.ddditserver.subsystems.auth.dto.UserDTO;
import it.unisa.ddditserver.subsystems.auth.exceptions.NotLoggedUserException;
import it.unisa.ddditserver.subsystems.versioning.dto.BranchDTO;
import it.unisa.ddditserver.subsystems.versioning.dto.RepositoryDTO;
import it.unisa.ddditserver.subsystems.versioning.dto.ResourceDTO;
import it.unisa.ddditserver.subsystems.versioning.exceptions.branch.BranchException;
import it.unisa.ddditserver.subsystems.versioning.exceptions.repo.RepositoryException;
import it.unisa.ddditserver.validators.auth.JWT.JWTokenValidator;
import it.unisa.ddditserver.validators.auth.user.UserValidationDTO;
import it.unisa.ddditserver.validators.auth.user.UserValidator;
import it.unisa.ddditserver.validators.versioning.branch.BranchValidationDTO;
import it.unisa.ddditserver.validators.versioning.branch.BranchValidator;
import it.unisa.ddditserver.validators.versioning.resource.ResourceValidationDTO;
import it.unisa.ddditserver.validators.versioning.resource.ResourceValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BranchServiceImpl implements BranchService {
    private GremlinBranchRepository gremlinService;
    private GremlinRepositoryRepository gremlinRepositoryRepository;
    private JWTokenValidator jwTokenValidator;
    private UserValidator userValidator;
    private ResourceValidator resourceValidator;
    private BranchValidator branchValidator;

    public BranchServiceImpl(GremlinBranchRepository gremlinService,
                             GremlinRepositoryRepository gremlinRepositoryRepository,
                             JWTokenValidator jwTokenValidator,
                             UserValidator userValidator,
                             ResourceValidator resourceValidator,
                             BranchValidator branchValidator) {
        this.gremlinService = gremlinService;
        this.gremlinRepositoryRepository = gremlinRepositoryRepository;
        this.jwTokenValidator = jwTokenValidator;
        this.userValidator = userValidator;
        this.resourceValidator = resourceValidator;
        this.branchValidator = branchValidator;
    }

    private void checkUserStatus(String repositoryName, String username) {
        RepositoryDTO repositoryDTO = new RepositoryDTO(repositoryName);
        UserDTO userDTO = new UserDTO(username, null);

        if (!gremlinRepositoryRepository.isContributor(repositoryDTO, userDTO) && !gremlinRepositoryRepository.isOwner(repositoryDTO, userDTO)) {
            throw new RepositoryException("Permission denied because " + username + " is not a contributor or the owner of " + repositoryName + " repository");
        }
    }

    @Override
    public ResponseEntity<Map<String, String>> createBranch(BranchDTO branchDTO, String token) {
        String retrievedUsername = jwTokenValidator.isTokenValid(token);
        String repositoryName = branchDTO.getRepositoryName();
        String resourceName = branchDTO.getResourceName();
        String branchName = branchDTO.getBranchName();

        if (retrievedUsername == null) {
            throw new NotLoggedUserException("Missing, invalid, or expired Authorization token");
        }

        checkUserStatus(repositoryName, retrievedUsername);

        UserValidationDTO userValidationDTO = new UserValidationDTO(retrievedUsername, null);

        // Check if user exists in graph database
        userValidator.validateExistence(userValidationDTO, true);

        BranchValidationDTO branchValidationDTO = new BranchValidationDTO(repositoryName, resourceName, branchName);

        // Check if branch's data are well-formed
        branchValidator.validateBranch(branchValidationDTO);

        // Check if the branch already exists in graph database
        // Check BranchValidator interface for more information about the exists flag
        branchValidator.validateExistence(branchValidationDTO, false);

        try {
            gremlinService.saveBranch(branchDTO);
        } catch (Exception e) {
            throw new BranchException(e.getMessage());
        }

        Map<String, String> response = new HashMap<>();
        response.put("message", "Branch " + branchName + " created successfully for " + resourceName + " resource in " + repositoryName + " repository");

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Map<String, Object>> listBranchesByResource(ResourceDTO resourceDTO, String token) {
        String retrievedUsername = jwTokenValidator.isTokenValid(token);
        String repositoryName = resourceDTO.getRepositoryName();
        String resourceName = resourceDTO.getResourceName();

        if (retrievedUsername == null) {
            throw new NotLoggedUserException("Missing, invalid, or expired Authorization token");
        }

        checkUserStatus(repositoryName, retrievedUsername);

        UserValidationDTO userValidationDTO = new UserValidationDTO(retrievedUsername, null);

        // Check if user exists in graph database
        userValidator.validateExistence(userValidationDTO, true);

        ResourceValidationDTO resourceValidationDTO = new ResourceValidationDTO(repositoryName, resourceName);

        // Check if resource's data are well-formed
        resourceValidator.validateResource(resourceValidationDTO);

        // Check if the resource already exists in graph database
        // Check BranchValidator interface for more information about the exists flag
        resourceValidator.validateExistence(resourceValidationDTO, true);

        List<BranchDTO> branches;

        try {
            branches = gremlinService.findBranchesByResource(resourceDTO);
        } catch (Exception e) {
            throw new BranchException(e.getMessage());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Branches found successfully for " + resourceName + " resource in " + repositoryName + " repository");
        response.put("branches", branches);

        return ResponseEntity.ok(response);
    }
}
