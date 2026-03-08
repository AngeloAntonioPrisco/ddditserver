package it.unisa.ddditserver.validators.versioning.repo;

import it.unisa.ddditserver.db.gremlin.versioning.repo.GremlinRepositoryRepository;
import it.unisa.ddditserver.subsystems.versioning.exceptions.repo.ExistingRepositoryException;
import it.unisa.ddditserver.subsystems.versioning.exceptions.repo.InvalidRepositoryNameException;
import it.unisa.ddditserver.subsystems.versioning.exceptions.repo.RepositoryNotFoundException;
import it.unisa.ddditserver.validators.ValidationResult;
import it.unisa.ddditserver.subsystems.versioning.dto.RepositoryDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.regex.Pattern;

@Component
public class RepositoryValidatorImpl implements RepositoryValidator {

    private final GremlinRepositoryRepository gremlinService;

    private static final int REPOSITORY_NAME_MIN_LENGTH = 3;
    private static final int REPOSITORY_NAME_MAX_LENGTH = 30;
    private static final Pattern REPOSITORY_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_.]+$");

    @Autowired
    public RepositoryValidatorImpl(GremlinRepositoryRepository gremlinService) {
        this.gremlinService = gremlinService;
    }

    public boolean isValidRepositoryName(String repositoryName) {
        if (repositoryName == null || repositoryName.isEmpty()) return false;
        int length = repositoryName.length();
        return length >= REPOSITORY_NAME_MIN_LENGTH &&
                length <= REPOSITORY_NAME_MAX_LENGTH &&
                REPOSITORY_NAME_PATTERN.matcher(repositoryName).matches();
    }

    @Override
    public ValidationResult validateRepository(RepositoryValidationDTO repositoryValidationDTO) {
        String repositoryName = repositoryValidationDTO.getRepositoryName();

        if (!isValidRepositoryName(repositoryName)) {
            throw new InvalidRepositoryNameException("Repository name must be 3-30 chars long");
        }

        return ValidationResult.valid();
    }

    @Override
    public ValidationResult validateExistence(RepositoryValidationDTO repositoryValidationDTO, boolean exists) {
        String repositoryName = repositoryValidationDTO.getRepositoryName();
        RepositoryDTO repositoryDTO = new RepositoryDTO(repositoryName);

        if (!isValidRepositoryName(repositoryName)) {
            throw new InvalidRepositoryNameException(repositoryName + " is invalid");
        }

        if (exists) {
            if (!gremlinService.existsByRepository(repositoryDTO)) {
                throw new RepositoryNotFoundException(repositoryName + " not found");
            }
        }
        else {
            if (gremlinService.existsByRepository(repositoryDTO)) {
                throw new ExistingRepositoryException(repositoryName + " already exists");
            }
        }
        return ValidationResult.valid();
    }

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