package it.unisa.ddditserver.validators.versioning.version;

import it.unisa.ddditserver.db.gremlin.versioning.version.GremlinVersionRepository;
import it.unisa.ddditserver.subsystems.versioning.exceptions.version.*;
import it.unisa.ddditserver.validators.ValidationResult;
import it.unisa.ddditserver.validators.versioning.branch.BranchValidationDTO;
import it.unisa.ddditserver.validators.versioning.branch.BranchValidator;
import it.unisa.ddditserver.validators.versioning.repo.RepositoryValidationDTO;
import it.unisa.ddditserver.validators.versioning.repo.RepositoryValidator;
import it.unisa.ddditserver.validators.versioning.resource.ResourceValidationDTO;
import it.unisa.ddditserver.validators.versioning.resource.ResourceValidator;
import it.unisa.ddditserver.subsystems.versioning.dto.version.VersionDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Component responsible for validating version data and checking its existence.
 *
 * This validator ensures that:
 * <ul>
 *     <li>Version file name (.fbx or .png) and comment follow defined length and character constraints.</li>
 *     <li>Associated branch, resource, and repository are valid and exist before validating the version.</li>
 *     <li>Version existence matches the expected condition in the graph database.</li>
 * </ul>
 *
 * Validation rules include:
 * <ul>
 *     <li>Version name: 3-30 characters, letters, digits, underscores only.</li>
 *     <li>Comment: 0-200 characters, letters, digits, spaces, and special characters (!, ?, -, ., ,).</li>
 *     <li>Mesh file: 3-30 characters, letters, digits, underscores, must end with .fbx.</li>
 *     <li>Material files: 3-30 characters, letters, digits, underscores, must end with .png.</li>
 *     <li>Repository, resource, and branch must be valid and exist in the graph database.</li>
 * </ul>
 *
 * It uses:
 * <ul>
 *     <li>{@link RepositoryValidator} to validate repository data</li>
 *     <li>{@link ResourceValidator} to validate resource data</li>
 *     <li>{@link BranchValidator} to validate branch data</li>
 *     <li>{@link GremlinVersionRepository} to check existence in the graph database</li>
 * </ul>
 *
 * @author Angelo Antonio Prisco
 * @version 1.1
 * @since 2025-08-14
 */
@Component
public class VersionValidatorImpl implements VersionValidator {
    private final RepositoryValidator repositoryValidator;
    private final ResourceValidator resourceValidator;
    private final BranchValidator branchValidator;
    private final GremlinVersionRepository gremlinService;

    private static final int FILE_NAME_MIN_LENGTH = 3;
    private static final int FILE_NAME_MAX_LENGTH = 30;
    private static final Pattern FILE_NAME_PATTERN = Pattern.compile("^\\w+$");

    private static final int COMMENT_MIN_LENGTH = 0;
    private static final int COMMENT_MAX_LENGTH = 200;
    private static final Pattern COMMENT_PATTERN = Pattern.compile("^[\\p{L}0-9_\\s.,!?-]+$");

    @Autowired
    public VersionValidatorImpl(GremlinVersionRepository gremlinService,
                                BranchValidator branchValidator,
                                ResourceValidator resourceValidator,
                                RepositoryValidator repositoryValidator) {
        this.gremlinService = gremlinService;
        this.branchValidator = branchValidator;
        this.resourceValidator = resourceValidator;
        this.repositoryValidator = repositoryValidator;
    }

    public boolean isValidComment(String comment) {
        return comment != null &&
                comment.length() >= COMMENT_MIN_LENGTH &&
                comment.length() <= COMMENT_MAX_LENGTH &&
                COMMENT_PATTERN.matcher(comment).matches();
    }

    public boolean isValidVersionName(String versionName) {
        if (versionName == null || versionName.isEmpty()) return false;
        return versionName.length() >= FILE_NAME_MIN_LENGTH &&
                versionName.length() <= FILE_NAME_MAX_LENGTH &&
                FILE_NAME_PATTERN.matcher(versionName).matches();
    }

    public boolean isValidMesh(MultipartFile mesh) {
        if (mesh == null || mesh.isEmpty()) return false;

        long maxSize = 1024L * 1024L * 1024L; // 1 GB in byte
        if (mesh.getSize() > maxSize) return false;

        String name = mesh.getOriginalFilename();
        if (name == null) return false;
        return name.length() >= FILE_NAME_MIN_LENGTH &&
                name.length() <= FILE_NAME_MAX_LENGTH &&
                name.toLowerCase().endsWith(".fbx") &&
                FILE_NAME_PATTERN.matcher(name.substring(0, name.lastIndexOf('.'))).matches();
    }

    public boolean isValidTexture(MultipartFile texture) {
        if (texture == null || texture.isEmpty()) return false;

        long maxSize = 1024L * 1024L * 1024L; // 1 GB in byte
        if (texture.getSize() > maxSize) return false;

        String name = texture.getOriginalFilename();
        if (name == null) return false;
        return name.length() >= FILE_NAME_MIN_LENGTH &&
                name.length() <= FILE_NAME_MAX_LENGTH &&
                name.toLowerCase().endsWith(".png") &&
                FILE_NAME_PATTERN.matcher(name.substring(0, name.lastIndexOf('.'))).matches();
    }

    @Override
    public ValidationResult validateVersion(VersionValidationDTO versionValidationDTO, boolean resourceType) {
        String repositoryName = versionValidationDTO.getRepositoryName();
        String resourceName = versionValidationDTO.getResourceName();
        String branchName = versionValidationDTO.getBranchName();
        String versionName = versionValidationDTO.getVersionName();
        String comment = versionValidationDTO.getComment();
        MultipartFile mesh = versionValidationDTO.getMesh();
        List<MultipartFile> material =  versionValidationDTO.getMaterial();

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

        // Check if the resources is valid before check for the branch because a branch depends on a resource
        BranchValidationDTO branchValidationDTO = new BranchValidationDTO(repositoryName, resourceName,  branchName);
        ValidationResult branchValidationResult = branchValidator.validate(branchValidationDTO);
        if (!branchValidationResult.isValid()) {
            return branchValidationResult;
        }

        if (!isValidComment(comment)) {
            throw new InvalidCommentException("Comment must be 0-200 chars long and contain only !, ? and - as special characters");
        }

        if (!isValidVersionName(versionName)) {
            throw new InvalidVersionNameException("Version name must be 3-30 chars long and can contain letters, digits and _ only");
        }

        if (resourceType) {
            boolean ok = isValidMesh(mesh) || isValidTexture(mesh);
            if (!ok) {
                throw new InvalidMeshException("Mesh name must be 3-30 chars long and can contain letters, digits and _ only, and must end with .fbx, other than being less than 1GB");
            }
        } else {
            for (MultipartFile texture : material) {
                if (!isValidTexture(texture)) {
                    throw new InvalidMaterialException("Texture name must be 3-30 chars long and can contain letters, digits and _ only, and must end with .png, other than being less than 1GB");
                }
            }
        }

        return ValidationResult.valid();
    }

    @Override
    public ValidationResult validateExistence(VersionValidationDTO versionValidationDTO, boolean exists) {
        String repositoryName = versionValidationDTO.getRepositoryName();
        String resourceName = versionValidationDTO.getResourceName();
        String branchName = versionValidationDTO.getBranchName();
        String versionName = versionValidationDTO.getVersionName();

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

        // Check if the resources is valid before check for the branch because a branch depends on a resource
        BranchValidationDTO branchValidationDTO = new BranchValidationDTO(repositoryName, resourceName,  branchName);
        ValidationResult branchValidationResult = branchValidator.validate(branchValidationDTO);
        if (!branchValidationResult.isValid()) {
            return branchValidationResult;
        }

        VersionDTO versionDTO = new VersionDTO(repositoryName, resourceName, branchName, versionName,
                null,null,null,null,null,null
        );

        if (exists) {
            if (!gremlinService.existsByVersion(versionDTO)) {
                throw new VersionNotFoundException(versionName + " does not exist as version name");
            }
        } else {
            if (gremlinService.existsByVersion(versionDTO)) {
                throw new ExistingVersionException(versionName + " already exists as version name." +
                        " This can happen due to a collision when generating a version name for a resource");
            }
        }

        return ValidationResult.valid();
    }

    @Override
    public ValidationResult validate(VersionValidationDTO versionValidationDTO, boolean resourceType) {
        ValidationResult versionValidation = validateVersion(versionValidationDTO, resourceType);
        if (!versionValidation.isValid()) return versionValidation;

        return validateExistence(versionValidationDTO, true);
    }
}
