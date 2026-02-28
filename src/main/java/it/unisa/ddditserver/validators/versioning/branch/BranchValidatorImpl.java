package it.unisa.ddditserver.validators.versioning.branch;

import it.unisa.ddditserver.db.gremlin.versioning.branch.GremlinBranchRepository;
import it.unisa.ddditserver.db.gremlin.versioning.version.GremlinVersionRepository;
import it.unisa.ddditserver.subsystems.versioning.exceptions.branch.BranchNotFoundException;
import it.unisa.ddditserver.subsystems.versioning.exceptions.branch.ExistingBranchException;
import it.unisa.ddditserver.subsystems.versioning.exceptions.branch.InvalidBranchNameException;
import it.unisa.ddditserver.validators.ValidationResult;
import it.unisa.ddditserver.validators.versioning.repo.RepositoryValidationDTO;
import it.unisa.ddditserver.validators.versioning.repo.RepositoryValidator;
import it.unisa.ddditserver.validators.versioning.resource.ResourceValidationDTO;
import it.unisa.ddditserver.validators.versioning.resource.ResourceValidator;
import it.unisa.ddditserver.subsystems.versioning.dto.BranchDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.regex.Pattern;

/**
 * Component responsible for validating branch data and checking its existence.
 *
 * This validator ensures that:
 * <ul>
 *     <li>The branch name is 3-30 characters long and contains only letters, digits, and underscores.</li>
 *     <li>The parent resource and repository exist and are valid before validating the branch.</li>
 *     <li>The branch existence matches the expected condition in the graph database.</li>
 * </ul>
 *
 * It uses:
 * <ul>
 *     <li>{@link RepositoryValidator} to validate repository data</li>
 *     <li>{@link ResourceValidator} to validate resource data</li>
 *     <li>{@link GremlinVersionRepository} to check existence in the graph database</li>
 * </ul>
 *
 * @author Angelo Antonio Prisco
 * @version 1.1
 * @since 2025-08-14
 */
@Component
public class BranchValidatorImpl implements BranchValidator {
    private final RepositoryValidator repositoryValidator;
    private final ResourceValidator resourceValidator;
    private final GremlinBranchRepository gremlinService;

    private static final int BRANCH_NAME_MIN_LENGTH = 3;
    private static final int BRANCH_NAME_MAX_LENGTH = 30;
    private static final Pattern BRANCH_NAME_PATTERN = Pattern.compile("^\\w+$");

    @Autowired
    public BranchValidatorImpl(RepositoryValidator repositoryValidator,
                               ResourceValidator resourceValidator,
                               GremlinBranchRepository gremlinService) {
        this.repositoryValidator = repositoryValidator;
        this.resourceValidator = resourceValidator;
        this.gremlinService = gremlinService;
    }

    private boolean isValidBranchName(String branchName) {
        if (branchName == null || branchName.isEmpty()) return false;
        int length = branchName.length();
        return length >= BRANCH_NAME_MIN_LENGTH &&
                length <= BRANCH_NAME_MAX_LENGTH &&
                BRANCH_NAME_PATTERN.matcher(branchName).matches();
    }

    @Override
    public ValidationResult validateBranch(BranchValidationDTO branchValidationDTO) {
        String repositoryName = branchValidationDTO.getRepositoryName();
        String resourceName = branchValidationDTO.getResourceName();
        String branchName = branchValidationDTO.getBranchName();

        // Check if the repository is valid before check for the resource because a resource depends on a repository
        RepositoryValidationDTO repositoryValidationDTO = new RepositoryValidationDTO(repositoryName);
        ValidationResult repositoryValidationResult = repositoryValidator.validate(repositoryValidationDTO);
        if (!repositoryValidationResult.isValid()) {
            return repositoryValidationResult;
        }

        // Check if the resources is valid before check for the branch because a branch depends on a resource
        ResourceValidationDTO resourceValidationDTO = new ResourceValidationDTO(repositoryName, resourceName);
        ValidationResult resourceValidationResult = resourceValidator.validate(resourceValidationDTO);
        if (!resourceValidationResult.isValid()) {
            return resourceValidationResult;
        }

        // Check if the branch is valid
        if (!isValidBranchName(branchName)) {
            throw new InvalidBranchNameException("Branch name must be 3-30 chars long and can contain letters, digits and _ only");
        }

        return ValidationResult.valid();
    }

    @Override
    public ValidationResult validateExistence(BranchValidationDTO branchValidationDTO, boolean exists) {
        String repositoryName = branchValidationDTO.getRepositoryName();
        String resourceName = branchValidationDTO.getResourceName();
        String branchName = branchValidationDTO.getBranchName();

        BranchDTO branchDTO = new BranchDTO(repositoryName, resourceName, branchName);

        // Check if the repository is valid before check for the resource because a resource depends on a repository
        RepositoryValidationDTO repositoryValidationDTO = new RepositoryValidationDTO(repositoryName);
        ValidationResult repositoryValidationResult = repositoryValidator.validate(repositoryValidationDTO);
        if (!repositoryValidationResult.isValid()) {
            return repositoryValidationResult;
        }

        // Check if the resources is valid before check for the branch because a branch depends on a resource
        ResourceValidationDTO resourceValidationDTO = new ResourceValidationDTO(repositoryName, resourceName);
        ValidationResult resourceValidationResult = resourceValidator.validate(resourceValidationDTO);
        if (!resourceValidationResult.isValid()) {
            return resourceValidationResult;
        }

        if (exists) {
            if (!gremlinService.existsByResource(branchDTO)) {
                throw new BranchNotFoundException(branchName + " does not exist as branch name");
            }
        } else {
            if (gremlinService.existsByResource(branchDTO)) {
                throw new ExistingBranchException(branchName + " already exists as branch name");
            }
        }

        return ValidationResult.valid();
    }

    @Override
    public ValidationResult validate(BranchValidationDTO branchValidationDTO) {
        ValidationResult branchValidation = validateBranch(branchValidationDTO);
        if (!branchValidation.isValid()) {
            return branchValidation;
        }

        branchValidation = validateExistence(branchValidationDTO, true);
        return branchValidation.isValid() ? ValidationResult.valid() : branchValidation;
    }
}