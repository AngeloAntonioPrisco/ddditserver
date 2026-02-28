package it.unisa.ddditserver.subsystems.versioning.service.resource;

import it.unisa.ddditserver.db.gremlin.versioning.branch.GremlinBranchRepository;
import it.unisa.ddditserver.db.gremlin.versioning.repo.GremlinRepositoryRepository;
import it.unisa.ddditserver.db.gremlin.versioning.resource.GremlinResourceRepository;
import it.unisa.ddditserver.db.gremlin.versioning.version.GremlinVersionRepository;
import it.unisa.ddditserver.subsystems.auth.dto.UserDTO;
import it.unisa.ddditserver.subsystems.auth.exceptions.NotLoggedUserException;
import it.unisa.ddditserver.subsystems.versioning.dto.BranchDTO;
import it.unisa.ddditserver.subsystems.versioning.dto.RepositoryDTO;
import it.unisa.ddditserver.subsystems.versioning.dto.ResourceDTO;
import it.unisa.ddditserver.subsystems.versioning.dto.version.VersionDTO;
import it.unisa.ddditserver.subsystems.versioning.exceptions.repo.RepositoryException;
import it.unisa.ddditserver.subsystems.versioning.exceptions.resource.ResourceException;
import it.unisa.ddditserver.validators.auth.JWT.JWTokenValidator;
import it.unisa.ddditserver.validators.auth.user.UserValidationDTO;
import it.unisa.ddditserver.validators.auth.user.UserValidator;
import it.unisa.ddditserver.validators.versioning.repo.RepositoryValidationDTO;
import it.unisa.ddditserver.validators.versioning.repo.RepositoryValidator;
import it.unisa.ddditserver.validators.versioning.resource.ResourceValidationDTO;
import it.unisa.ddditserver.validators.versioning.resource.ResourceValidator;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ResourceServiceImpl implements  ResourceService {

    private static final String MESSAGE_KEY = "message";

    private GremlinResourceRepository gremlinResourceRepository;
    private GremlinBranchRepository gremlinBranchRepository;
    private GremlinVersionRepository gremlinVersionRepository;
    private JWTokenValidator jwTokenValidator;
    private UserValidator userValidator;
    private RepositoryValidator repositoryValidator;
    private ResourceValidator resourceValidator;
    private GremlinRepositoryRepository gremlinRepositoryRepository;

    public ResourceServiceImpl(GremlinBranchRepository gremlinBranchRepository, GremlinRepositoryRepository gremlinRepositoryRepository, GremlinResourceRepository gremlinResourceRepository, GremlinVersionRepository gremlinVersionRepository, JWTokenValidator jwTokenValidator, RepositoryValidator repositoryValidator, ResourceValidator resourceValidator, UserValidator userValidator) {
        this.gremlinBranchRepository = gremlinBranchRepository;
        this.gremlinRepositoryRepository = gremlinRepositoryRepository;
        this.gremlinResourceRepository = gremlinResourceRepository;
        this.gremlinVersionRepository = gremlinVersionRepository;
        this.jwTokenValidator = jwTokenValidator;
        this.repositoryValidator = repositoryValidator;
        this.resourceValidator = resourceValidator;
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
    public ResponseEntity<Map<String, String>> createResource(ResourceDTO resourceDTO, String token) {
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
        // Check ResourceValidator interface for more information about the exists flag
        resourceValidator.validateExistence(resourceValidationDTO, false);

        try {
            gremlinResourceRepository.saveResource(resourceDTO);
        } catch (Exception e) {
            throw new ResourceException(e.getMessage());
        }

        Map<String, String> response = new HashMap<>();
        response.put(MESSAGE_KEY, "Resource " + resourceName + " created successfully in " + repositoryName + " repository");

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Map<String, Object>> listResourcesByRepository(RepositoryDTO repositoryDTO, String token) {
        String retrievedUsername = jwTokenValidator.isTokenValid(token);
        String repositoryName = repositoryDTO.getRepositoryName();

        if (retrievedUsername == null) {
            throw new NotLoggedUserException("Missing, invalid, or expired Authorization token");
        }

        checkUserStatus(repositoryName, retrievedUsername);

        UserValidationDTO userValidationDTO = new UserValidationDTO(retrievedUsername, null);

        // Check if user exists in graph database
        userValidator.validateExistence(userValidationDTO, true);

        RepositoryValidationDTO repositoryValidationDTO = new RepositoryValidationDTO(repositoryName);

        // Check if repository's data are well-formed
        repositoryValidator.validateRepository(repositoryValidationDTO);

        // Check if the repository already exists in graph database
        // Check RepositoryValidator interface for more information about the exists flag
        repositoryValidator.validateExistence(repositoryValidationDTO, true);

        List<ResourceDTO> resources;

        try {
            resources = gremlinResourceRepository.findResourcesByRepository(repositoryDTO);
        } catch (Exception e) {
            throw new ResourceException(e.getMessage());
        }

        Map<String, Object> response = new HashMap<>();
        response.put(MESSAGE_KEY, "Resources found successfully in " + repositoryName + " repository");
        response.put("resources", resources);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Map<String, Object>> showVersionTree(ResourceDTO resourceDTO, String token) {
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
        // Check ResourceValidator interface for more information about the exists flag
        resourceValidator.validateExistence(resourceValidationDTO, true);

        HashMap<String, List<String>> versionTree = new HashMap<>();

        try {
            List<BranchDTO> branches = gremlinBranchRepository.findBranchesByResource(resourceDTO);

            for (BranchDTO branch : branches) {

                List<VersionDTO> versions = gremlinVersionRepository.findVersionsByBranch(branch);
                List<String> versionNames =  new ArrayList<>();

                for (VersionDTO version : versions) {
                    versionNames.add(version.getVersionName());
                }

                versionTree.put(branch.getBranchName(), versionNames);
            }
        } catch (Exception e) {
            throw new ResourceException(e.getMessage());
        }

        Map<String, Object> response = new HashMap<>();
        response.put(MESSAGE_KEY, "Version tree of " + resourceName + " resource in " + repositoryName + " repository retrieved successfully");
        response.put("versionTree", versionTree);

        return ResponseEntity.ok(response);
    }
}
