package it.unisa.ddditserver.subsystems.versioning.service.repo;

import it.unisa.ddditserver.subsystems.auth.dto.UserDTO;
import it.unisa.ddditserver.subsystems.auth.exceptions.NotLoggedUserException;
import it.unisa.ddditserver.db.gremlin.versioning.repo.GremlinRepositoryRepository;
import it.unisa.ddditserver.validators.auth.JWT.JWTokenValidator;
import it.unisa.ddditserver.validators.auth.user.UserValidationDTO;
import it.unisa.ddditserver.validators.auth.user.UserValidator;
import it.unisa.ddditserver.validators.versioning.repo.RepositoryValidationDTO;
import it.unisa.ddditserver.validators.versioning.repo.RepositoryValidator;
import it.unisa.ddditserver.subsystems.versioning.dto.RepositoryDTO;
import it.unisa.ddditserver.subsystems.versioning.exceptions.repo.RepositoryException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RepositoryServiceImpl implements  RepositoryService {
    private final GremlinRepositoryRepository gremlinService;
    private final JWTokenValidator jwTokenValidator;
    private final UserValidator userValidator;
    private final RepositoryValidator repositoryValidator;

    public RepositoryServiceImpl(GremlinRepositoryRepository gremlinService,
                                 JWTokenValidator jwTokenValidator,
                                 UserValidator userValidator,
                                 RepositoryValidator repositoryValidator) {
        this.gremlinService = gremlinService;
        this.jwTokenValidator = jwTokenValidator;
        this.userValidator = userValidator;
        this.repositoryValidator = repositoryValidator;
    }

    @Override
    public ResponseEntity<Map<String, String>> createRepository(RepositoryDTO repositoryDTO, String token) {
        String retrievedUsername = jwTokenValidator.isTokenValid(token);
        String repositoryName = repositoryDTO.getRepositoryName();

        if (retrievedUsername == null) {
            throw new NotLoggedUserException("Missing, invalid, or expired Authorization token");
        }

        UserValidationDTO userValidationDTO = new UserValidationDTO(retrievedUsername, null);

        // Check if user exists in graph database
        userValidator.validateExistence(userValidationDTO, true);

        RepositoryValidationDTO repositoryValidationDTO = new RepositoryValidationDTO(repositoryName);

        // Check if repository's data are well-formed
        repositoryValidator.validateRepository(repositoryValidationDTO);

        // Check if the repository already exists in graph database
        // Check RepositoryValidator interface for more information about the exists flag
        repositoryValidator.validateExistence(repositoryValidationDTO, false);

        try {
            gremlinService.saveRepository(repositoryDTO, new UserDTO(retrievedUsername, null));
        } catch (Exception e) {
            throw new RepositoryException(e.getMessage());
        }

        Map<String, String> response = new HashMap<>();
        response.put("message", "Repository " + repositoryName + " created successfully");

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Map<String, Object>> listRepositoriesOwned(String token) {
        String retrievedUsername = jwTokenValidator.isTokenValid(token);

        if (retrievedUsername == null) {
            throw new NotLoggedUserException("Missing, invalid, or expired Authorization token");
        }

        UserValidationDTO userValidationDTO = new UserValidationDTO(retrievedUsername, null);

        // Check if user exists in graph database
        userValidator.validateExistence(userValidationDTO, true);

        List<RepositoryDTO> ownedRepositories;

        try {
            ownedRepositories = gremlinService.findOwnedRepositoriesByUser(new UserDTO(retrievedUsername, null));
        } catch (Exception e) {
            throw new RepositoryException(e.getMessage());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Owned repositories found successfully");
        response.put("ownedRepositories", ownedRepositories);

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<Map<String, Object>> listRepositoriesContributed(String token) {
        String retrievedUsername = jwTokenValidator.isTokenValid(token);

        if (retrievedUsername == null) {
            throw new NotLoggedUserException("Missing, invalid, or expired Authorization token");
        }

        UserValidationDTO userValidationDTO = new UserValidationDTO(retrievedUsername, null);

        // Check if user exists in graph database
        userValidator.validateExistence(userValidationDTO, true);

        List<RepositoryDTO> contributedRepositories;

        try {
            contributedRepositories = gremlinService.findContributedRepositoriesByUser(new UserDTO(retrievedUsername, null));
        } catch (Exception e) {
            throw new RepositoryException(e.getMessage());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Contributed repositories found successfully");
        response.put("contributedRepositories", contributedRepositories);

        return ResponseEntity.ok(response);
    }
}
