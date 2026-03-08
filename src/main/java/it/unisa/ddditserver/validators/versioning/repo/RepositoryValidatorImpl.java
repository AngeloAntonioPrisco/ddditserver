package it.unisa.ddditserver.validators.versioning.repo;

import it.unisa.ddditserver.db.gremlin.auth.GremlinAuthRepository;
import it.unisa.ddditserver.db.gremlin.versioning.repo.GremlinRepositoryRepository;
import it.unisa.ddditserver.subsystems.versioning.exceptions.repo.ExistingRepositoryException;
import it.unisa.ddditserver.subsystems.versioning.exceptions.repo.InvalidRepositoryNameException;
import it.unisa.ddditserver.subsystems.versioning.exceptions.repo.RepositoryNotFoundException;
import it.unisa.ddditserver.validators.ValidationResult;
import it.unisa.ddditserver.subsystems.versioning.dto.RepositoryDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.regex.Pattern;

/**
 * Component responsible for validating repository data and checking its existence.
 *
 * This validator ensures that repository names meet predefined patterns and length constraints.
 * It also checks the existence of a repository using {@link GremlinAuthRepository}.
 *
 * <ul>
 *     <li>Repository name must be 3-30 characters long, containing only letters, digits, underscores, and dots.</li>
 *     <li>Existence of the repository in the graph database.</li>
 * </ul>
 *
 * @author Angelo Antonio Prisco
 * @version 1.2
 * @since 2025-08-13
 */
@Component
public class RepositoryValidatorImpl implements RepositoryValidator {
    private final GremlinRepositoryRepository gremlinService;

    private static final int REPOSITORY_NAME_MIN_LENGTH = 3;
    private static final int REPOSITORY_NAME_MAX_LENGTH = 30;
    private static final Pattern REPOSITORY_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_.]+$");

    /*@
      @ public normal_behavior
      @   requires gremlinService != null;
      @   assignable this.gremlinService;
      @   ensures this.gremlinService == gremlinService;
      @*/
    @Autowired
    public RepositoryValidatorImpl(GremlinRepositoryRepository gremlinService) {
        this.gremlinService = gremlinService;
    }

    /*@
      @ public normal_behavior
      @   ensures \result == (repositoryName != null &&
      @                      repositoryName.length() >= REPOSITORY_NAME_MIN_LENGTH &&
      @                      repositoryName.length() <= REPOSITORY_NAME_MAX_LENGTH &&
      @                      (\exists String s; s.equals(repositoryName) && REPOSITORY_NAME_PATTERN.matcher(s).matches()));
      @   pure
      @*/
    public boolean isValidRepositoryName(String repositoryName) {
        if (repositoryName == null || repositoryName.isEmpty()) return false;
        int length = repositoryName.length();
        return length >= REPOSITORY_NAME_MIN_LENGTH &&
                length <= REPOSITORY_NAME_MAX_LENGTH &&
                REPOSITORY_NAME_PATTERN.matcher(repositoryName).matches();
    }

    /*@
      @ public normal_behavior
      @   requires repositoryValidationDTO != null && isValidRepositoryName(repositoryValidationDTO.getRepositoryName());
      @   ensures \result != null && \result.isValid();
      @
      @ also
      @ public exceptional_behavior
      @   requires repositoryValidationDTO == null || !isValidRepositoryName(repositoryValidationDTO.getRepositoryName());
      @   signals (InvalidRepositoryNameException) true;
      @*/
    @Override
    public ValidationResult validateRepository(RepositoryValidationDTO repositoryValidationDTO) {
        String repositoryName = repositoryValidationDTO.getRepositoryName();

        if (!isValidRepositoryName(repositoryName)) {
            throw new InvalidRepositoryNameException("Repository name must be 3-30 chars long and can contain letters, digits and _ only");
        }

        return ValidationResult.valid();
    }

    /*@
      @ public normal_behavior
      @   requires repositoryValidationDTO != null && isValidRepositoryName(repositoryValidationDTO.getRepositoryName());
      @   requires exists ==> gremlinService.existsByRepository(new RepositoryDTO(repositoryValidationDTO.getRepositoryName()));
      @   requires !exists ==> !gremlinService.existsByRepository(new RepositoryDTO(repositoryValidationDTO.getRepositoryName()));
      @   ensures \result != null && \result.isValid();
      @
      @ also
      @ public exceptional_behavior
      @   requires !isValidRepositoryName(repositoryValidationDTO.getRepositoryName());
      @   signals (InvalidRepositoryNameException) true;
      @
      @ also
      @ public exceptional_behavior
      @   requires isValidRepositoryName(repositoryValidationDTO.getRepositoryName()) && exists && !gremlinService.existsByRepository(new RepositoryDTO(repositoryValidationDTO.getRepositoryName()));
      @   signals (RepositoryNotFoundException) true;
      @
      @ also
      @ public exceptional_behavior
      @   requires isValidRepositoryName(repositoryValidationDTO.getRepositoryName()) && !exists && gremlinService.existsByRepository(new RepositoryDTO(repositoryValidationDTO.getRepositoryName()));
      @   signals (ExistingRepositoryException) true;
      @*/
    @Override
    public ValidationResult validateExistence(RepositoryValidationDTO repositoryValidationDTO, boolean exists) {
        String repositoryName = repositoryValidationDTO.getRepositoryName();

        RepositoryDTO repositoryDTO = new RepositoryDTO(repositoryName);

        if (!isValidRepositoryName(repositoryName)) {
            throw new InvalidRepositoryNameException(repositoryName + " is not a valid repository name to check its existence");
        }

        if (exists) {
            if (!gremlinService.existsByRepository(repositoryDTO)) {
                throw new RepositoryNotFoundException(repositoryName + " does not exist as repository name");
            }
        }
        else {
            if (gremlinService.existsByRepository(repositoryDTO)) {
                throw new ExistingRepositoryException(repositoryName + " already exists as repository name");
            }
        }
        return ValidationResult.valid();
    }

    /*@
      @ public normal_behavior
      @   requires repositoryValidationDTO != null;
      @   requires isValidRepositoryName(repositoryValidationDTO.getRepositoryName());
      @   requires gremlinService.existsByRepository(new RepositoryDTO(repositoryValidationDTO.getRepositoryName()));
      @   ensures \result != null && \result.isValid();
      @
      @ also
      @ public exceptional_behavior
      @   signals (InvalidRepositoryNameException) !isValidRepositoryName(repositoryValidationDTO.getRepositoryName());
      @   signals (RepositoryNotFoundException) isValidRepositoryName(repositoryValidationDTO.getRepositoryName()) && !gremlinService.existsByRepository(new RepositoryDTO(repositoryValidationDTO.getRepositoryName()));
      @*/
    @Override
    public ValidationResult validate(RepositoryValidationDTO repositoryValidationDTO) {
        ValidationResult repositoryValidation = validateRepository(repositoryValidationDTO);
        if (!repositoryValidation.isValid()) {
            return repositoryValidation;
        }

        repositoryValidation = validateExistence(repositoryValidationDTO, true);
        return repositoryValidation.isValid() ? ValidationResult.valid() : repositoryValidation;
    }
}
