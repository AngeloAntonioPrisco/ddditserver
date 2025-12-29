package it.unisa.ddditserver.subsystems.versioning.service.version;

import it.unisa.ddditserver.db.gremlin.versioning.repo.GremlinRepositoryRepository;
import it.unisa.ddditserver.db.gremlin.versioning.version.GremlinVersionRepository;
import it.unisa.ddditserver.subsystems.ai.service.TagClassificationService;
import it.unisa.ddditserver.subsystems.auth.dto.UserDTO;
import it.unisa.ddditserver.subsystems.auth.exceptions.NotLoggedUserException;
import it.unisa.ddditserver.subsystems.versioning.dto.RepositoryDTO;
import it.unisa.ddditserver.subsystems.versioning.dto.version.VersionDTO;
import it.unisa.ddditserver.subsystems.versioning.exceptions.repo.RepositoryException;
import it.unisa.ddditserver.subsystems.versioning.exceptions.version.VersionException;
import it.unisa.ddditserver.validators.auth.JWT.JWTokenValidator;
import it.unisa.ddditserver.validators.auth.user.UserValidationDTO;
import it.unisa.ddditserver.validators.auth.user.UserValidator;
import it.unisa.ddditserver.validators.versioning.version.VersionValidationDTO;
import it.unisa.ddditserver.validators.versioning.version.VersionValidator;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class VersionServiceImpl implements VersionService {
    @Autowired
    private GremlinVersionRepository gremlinVersionRepository;
    @Autowired
    private GremlinRepositoryRepository gremlinRepositoryRepository;
    @Autowired
    private JWTokenValidator jwTokenValidator;
    @Autowired
    private UserValidator userValidator;
    @Autowired
    private VersionValidator versionValidator;
    @Autowired
    private TagClassificationService tagClassificationService;

    private void checkUserStatus(String repositoryName, String username) {
        RepositoryDTO repositoryDTO = new RepositoryDTO(repositoryName);
        UserDTO userDTO = new UserDTO(username, null);

        if (!gremlinRepositoryRepository.isContributor(repositoryDTO, userDTO) && !gremlinRepositoryRepository.isOwner(repositoryDTO, userDTO)) {
            throw new RepositoryException("Permission denied because " + username + " is not a contributor or the owner of " + repositoryName + " repository");
        }
    }

    private String generateVersionName(VersionDTO versionDTO) {
        String base = versionDTO.getVersionName()
                .replaceAll("[^a-zA-Z0-9]", "")
                .toLowerCase();
        if (base.length() > 3) base = base.substring(0, 3);

        String uuidPart = UUID.randomUUID().toString().replaceAll("-", "");
        uuidPart = uuidPart.substring(0, 4);

        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yy-MM-HH:mm");
        String timestamp = java.time.LocalDateTime.now().format(formatter);

        return timestamp + "-" + base + uuidPart;
    }

    @Override
    public ResponseEntity<Map<String, String>> createVersion(VersionDTO versionDTO, String token) {
        String retrievedUsername = jwTokenValidator.isTokenValid(token);
        String repositoryName = versionDTO.getRepositoryName();
        String resourceName = versionDTO.getResourceName();
        String branchName = versionDTO.getBranchName();
        String versionName = versionDTO.getVersionName();
        LocalDateTime pushedAt = LocalDateTime.now();
        String comment = versionDTO.getComment();
        List<String> tags = new ArrayList<>();
        MultipartFile mesh = null;
        List<MultipartFile> material = null;

        boolean resourceType;

        if (versionDTO.getMesh() == null) {
            resourceType = false;
            material = versionDTO.getMaterial();
        }
        else {
            resourceType = true;
            mesh =  versionDTO.getMesh();
            // ATTENTION: At the moment this is a placeholder, no classification is performed
            tags = new ArrayList<>();
        }

        if (retrievedUsername == null) {
            throw new NotLoggedUserException("Missing, invalid, or expired Authorization token");
        }

        checkUserStatus(repositoryName, retrievedUsername);

        UserValidationDTO userValidationDTO = new UserValidationDTO(retrievedUsername, null);

        // Check if user exists in graph database
        userValidator.validateExistence(userValidationDTO, true);

        VersionValidationDTO versionValidationDTO;

        if (mesh != null && material != null) {
            throw new VersionException("Can't push both a mesh and a material");
        }

        if (resourceType) {
            versionValidationDTO = new VersionValidationDTO(repositoryName, resourceName, branchName, versionName, comment, mesh, null);
            // Check if version's data are well-formed
            versionValidator.validateVersion(versionValidationDTO, true);
        } else {
            versionValidationDTO = new VersionValidationDTO(repositoryName, resourceName, branchName, versionName, comment, null, material);
            // Check if version's data are well-formed
            versionValidator.validateVersion(versionValidationDTO, false);
        }

        String generatedVersionName = generateVersionName(versionDTO);
        versionDTO.setVersionName(generatedVersionName);

        // Check if the resource already exists in graph database
        // Check ResourceValidator interface for more information about the exists flag
        versionValidator.validateExistence(versionValidationDTO, false);

        VersionDTO enrichedVersionDTO;

        if (resourceType) {
            enrichedVersionDTO = new VersionDTO(
                    repositoryName, resourceName,
                    branchName, generatedVersionName ,
                    retrievedUsername, pushedAt, comment,
                    tags, mesh, null
            );
        } else {
            enrichedVersionDTO = new VersionDTO(
                    repositoryName, resourceName,
                    branchName, generatedVersionName ,
                    retrievedUsername, pushedAt, comment,
                    tags, null, material
            );
        }

        try {
            gremlinVersionRepository.saveVersion(enrichedVersionDTO, resourceType);
        } catch (Exception e) {
            throw new VersionException(e.getMessage());
        }

        Map<String, String> response = new HashMap<>();
        if (resourceType) {
            response.put("message", "Version of " + mesh.getOriginalFilename() +
                    " pushed successfully as " + generatedVersionName + " in " +
                    branchName + " branch for " + resourceName + " resource in " +
                    repositoryName + " repository");
        } else {
            response.put("message", "Version of " + versionName +
                    " pushed successfully as " + generatedVersionName + " in " +
                    branchName + " branch for " + resourceName + " resource in " +
                    repositoryName + " repository");
        }

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<MultiValueMap<String, Object>> pullVersion(VersionDTO versionDTO, String token) {
        String retrievedUsername = jwTokenValidator.isTokenValid(token);
        String repositoryName = versionDTO.getRepositoryName();
        String resourceName = versionDTO.getResourceName();
        String branchName = versionDTO.getBranchName();
        String versionName = versionDTO.getVersionName();

        if (retrievedUsername == null) {
            throw new NotLoggedUserException("Missing, invalid, or expired Authorization token");
        }

        checkUserStatus(repositoryName, retrievedUsername);

        UserValidationDTO userValidationDTO = new UserValidationDTO(retrievedUsername, null);

        // Check if user exists in graph database
        userValidator.validateExistence(userValidationDTO, true);

        VersionValidationDTO versionValidationDTO = new VersionValidationDTO(
                repositoryName, resourceName,
                branchName, versionName,
                null, null,
                null
        );

        // Check if the version already exists in graph database
        // Check VersionValidator interface for more information about the exists flag
        versionValidator.validateExistence(versionValidationDTO, true);

        List<Pair<NonClosingInputStreamResource, String>> resources;
        try {
            resources = gremlinVersionRepository.getFile(versionDTO);
        } catch (Exception e) {
            throw new VersionException(e.getMessage());
        }

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("message", new HttpEntity<>(
                "Version " + versionName + " pulled successfully from " + branchName +
                        " branch for " + resourceName + " resource in " + repositoryName + " repository",
                new HttpHeaders()
        ));

        for (Pair<NonClosingInputStreamResource, String> pair : resources) {
            NonClosingInputStreamResource resource = pair.getLeft();
            String contentType = pair.getRight();

            HttpHeaders fileHeaders = new HttpHeaders();
            fileHeaders.setContentType(MediaType.parseMediaType(contentType));
            fileHeaders.setContentDisposition(ContentDisposition.builder("form-data")
                    .name("file")
                    .filename(resource.getFilename())
                    .build());

            body.add("file", new HttpEntity<>(resource, fileHeaders));
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<Map<String, Object>> showVersionMetadata(VersionDTO versionDTO, String token) {
        String retrievedUsername = jwTokenValidator.isTokenValid(token);
        String repositoryName = versionDTO.getRepositoryName();
        String resourceName = versionDTO.getResourceName();
        String branchName = versionDTO.getBranchName();
        String versionName = versionDTO.getVersionName();

        if (retrievedUsername == null) {
            throw new NotLoggedUserException("Missing, invalid, or expired Authorization token");
        }

        checkUserStatus(repositoryName, retrievedUsername);

        UserValidationDTO userValidationDTO = new UserValidationDTO(retrievedUsername, null);

        // Check if user exists in graph database
        userValidator.validateExistence(userValidationDTO, true);

        VersionValidationDTO versionValidationDTO = new VersionValidationDTO(
                repositoryName, resourceName, branchName, versionName,
                null, null, null
        );

        // Check if the version already exists in graph database
        // Check VersionValidator interface for more information about the exists flag
        versionValidator.validateExistence(versionValidationDTO, true);

        try {
            versionDTO = gremlinVersionRepository.findVersionByBranch(versionDTO);
        } catch (Exception e) {

            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Metadata not found");
            return new ResponseEntity(errorResponse, HttpStatus.NOT_FOUND);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Version information retrieved successfully");
        response.put("versionName", versionDTO.getVersionName());
        response.put("username", versionDTO.getUsername() != null ? versionDTO.getUsername() : "anonymous");

        // To enure ISO date
        String isoDate;
        if (versionDTO.getPushedAt() != null) {
            isoDate = versionDTO.getPushedAt().toString();
        } else {
            isoDate = java.time.LocalDateTime.now().toString();
        }
        response.put("pushedAt", isoDate);

        response.put("comment", versionDTO.getComment() != null ? versionDTO.getComment() : "");
        response.put("tags", versionDTO.getTagsAsString() != null ? versionDTO.getTagsAsString() : "");

        return ResponseEntity.ok(response);
    }
}

