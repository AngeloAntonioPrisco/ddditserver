package it.unisa.ddditserver.subsystems.versioning.controller.repo;

import it.unisa.ddditserver.subsystems.versioning.dto.RepositoryDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * Controller interface for repository endpoints.
 *
 * Provides operations for creating new repositories
 * and listing repositories owned or contributed to
 * by the authenticated user within the versioning subsystem.
 *
 * @author Angelo Antonio Prisco
 * @version 1.0
 * @since 2025-08-12
 */
public interface RepositoryController {

    /**
     * Handles the request to create a new repository.
     *
     * @param repositoryDTO the repository data transfer object containing repository details
     * @param request the HTTP servlet request object
     * @return a ResponseEntity indicating the result of the repository creation operation
     */
    ResponseEntity<Map<String, String>> createRepository(@RequestBody RepositoryDTO repositoryDTO, HttpServletRequest request);

    /**
     * Handles the request to list all repositories owned by the authenticated user.
     *
     * @param request the HTTP servlet request object
     * @return a ResponseEntity containing the list of repositories owned by the user
     */
    ResponseEntity<Map<String, Object>> listOwnedRepositories(HttpServletRequest request);

    /**
     * Handles the request to list all repositories where the authenticated user is a contributor.
     *
     * @param request the HTTP servlet request object
     * @return a ResponseEntity containing the list of repositories the user contributes to
     */
    ResponseEntity<Map<String, Object>> listContributedRepositories(HttpServletRequest request);
}
