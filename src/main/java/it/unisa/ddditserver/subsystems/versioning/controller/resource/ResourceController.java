package it.unisa.ddditserver.subsystems.versioning.controller.resource;

import it.unisa.ddditserver.subsystems.versioning.dto.RepositoryDTO;
import it.unisa.ddditserver.subsystems.versioning.dto.ResourceDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * Controller interface for resource endpoints.
 *
 * Provides operations for creating resources, listing resources
 * within a repository, and displaying the version tree of a resource
 * in the versioning subsystem.
 *
 * @author Angelo Antonio Prisco
 * @version 1.0
 * @since 2025-08-14
 */
public interface ResourceController {

    /**
     * Handles the request to create a new resource.
     *
     * @param resourceDTO the resource data transfer object containing resource details
     * @param request the HTTP servlet request object
     * @return a ResponseEntity indicating the result of the resource creation operation
     */
    ResponseEntity<Map<String, String>> createResource(@RequestBody ResourceDTO resourceDTO, HttpServletRequest request);

    /**
     * Handles the request to list all resources contained in a repository.
     *
     * @param repositoryDTO the repository data transfer object identifying the repository
     * @param request the HTTP servlet request object
     * @return a ResponseEntity containing the list of resources in the specified repository
     */
    ResponseEntity<Map<String, Object>> listResourcesByRepository(@RequestBody RepositoryDTO repositoryDTO, HttpServletRequest request);

    /**
     * Handles the request to display the version tree of a resource.
     *
     * @param resourceDTO the resource data transfer object identifying the resource
     * @param request the HTTP servlet request object
     * @return a ResponseEntity containing the version tree of the specified resource
     */
    ResponseEntity<Map<String, Object>> showVersionTree(@RequestBody ResourceDTO resourceDTO, HttpServletRequest request);
}
