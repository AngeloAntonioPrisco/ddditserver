package it.unisa.ddditserver.validators.versioning.resource;

import it.unisa.ddditserver.db.gremlin.versioning.resource.GremlinResourceRepository;
import it.unisa.ddditserver.subsystems.versioning.exceptions.resource.ExistingResourceException;
import it.unisa.ddditserver.subsystems.versioning.exceptions.resource.InvalidResourceNameException;
import it.unisa.ddditserver.subsystems.versioning.exceptions.resource.ResourceNotFoundException;
import it.unisa.ddditserver.validators.ValidationResult;
import it.unisa.ddditserver.validators.versioning.repo.RepositoryValidationDTO;
import it.unisa.ddditserver.validators.versioning.repo.RepositoryValidator;
import it.unisa.ddditserver.subsystems.versioning.dto.ResourceDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.regex.Pattern;

/**
 * Component responsible for validating resource data and checking its existence.
 *
 * This validator verifies that the resource name respects predefined pattern and length constraints.
 * It also checks if a resource already exists using the {@link GremlinResourceRepository}.
 * Validations include:
 * <ul>
 *     <li>Resource name must be 8-30 characters long, containing only letters, digits, and underscores.</li>
 *     <li>Existence of the resource in the graph database.</li>
 * </ul>
 * It uses:
 * <ul>
 *     <li>{@link RepositoryValidator} to validate repository data</li>
 * </ul>
 *
 * @author Angelo Antonio Prisco
 * @version 1.0
 * @since 2025-08-14
 */
@Component
public class ResourceValidatorImpl implements ResourceValidator {
    private final RepositoryValidator repositoryValidator;
    private final GremlinResourceRepository gremlinService;

    private static final int RESOURCE_NAME_MIN_LENGTH = 3;
    private static final int RESOURCE_NAME_MAX_LENGTH = 30;
    private static final Pattern RESOURCE_NAME_PATTERN = Pattern.compile("^\\w+$");

    @Autowired
    public ResourceValidatorImpl(RepositoryValidator repositoryValidator, GremlinResourceRepository gremlinService) {
        this.repositoryValidator = repositoryValidator;
        this.gremlinService = gremlinService;
    }

    public boolean isValidResourceName(String resourceName) {
        if (resourceName == null || resourceName.isEmpty()) return false;
        int length = resourceName.length();
        return length >= RESOURCE_NAME_MIN_LENGTH &&
                length <= RESOURCE_NAME_MAX_LENGTH &&
                RESOURCE_NAME_PATTERN.matcher(resourceName).matches();
    }

    @Override
    public ValidationResult validateResource(ResourceValidationDTO resourceValidationDTO) {
        String repositoryName = resourceValidationDTO.getRepositoryName();
        String resourceName = resourceValidationDTO.getResourceName();

        // Check if the repository is valid before check for the resource because a resource depends on a repository
        RepositoryValidationDTO repositoryValidationDTO = new RepositoryValidationDTO(repositoryName);
        ValidationResult repositoryValidationResult = repositoryValidator.validate(repositoryValidationDTO);
        if (!repositoryValidationResult.isValid()) {
            return repositoryValidationResult;
        }

        if (!isValidResourceName(resourceName)) {
            throw new InvalidResourceNameException("Resource name must be 3-30 chars long and can contain letters, digits and _ only");
        }

        return ValidationResult.valid();
    }

    @Override
    public ValidationResult validateExistence(ResourceValidationDTO resourceValidationDTO, boolean exists) {
        String repositoryName = resourceValidationDTO.getRepositoryName();
        String resourceName = resourceValidationDTO.getResourceName();

        ResourceDTO resourceDTO = new ResourceDTO(repositoryName, resourceName);

        // Check if the repository is valid before check for the resource because a resource depends on a repository
        RepositoryValidationDTO repositoryValidationDTO = new RepositoryValidationDTO(repositoryName);
        ValidationResult repositoryValidationResult = repositoryValidator.validate(repositoryValidationDTO);
        if (!repositoryValidationResult.isValid()) {
            return repositoryValidationResult;
        }

        // Check if the resource is valid
        ValidationResult resourceValidationResult = validateResource(resourceValidationDTO);
        if (!resourceValidationResult.isValid()) {
            return resourceValidationResult;
        }

        if (exists) {
            if (!gremlinService.existsByRepository(resourceDTO)) {
                throw new ResourceNotFoundException(resourceName + " does not exist as resource name");
            }
        } else {
            if (gremlinService.existsByRepository(resourceDTO)) {
                throw new ExistingResourceException(resourceName + " already exists as resource name");
            }
        }

        return ValidationResult.valid();
    }

    @Override
    public ValidationResult validate(ResourceValidationDTO resourceValidationDTO) {
        ValidationResult resourceValidation = validateResource(resourceValidationDTO);
        if (!resourceValidation.isValid()) {
            return resourceValidation;
        }

        resourceValidation = validateExistence(resourceValidationDTO, true);
        return resourceValidation.isValid() ? ValidationResult.valid() : resourceValidation;
    }
}
