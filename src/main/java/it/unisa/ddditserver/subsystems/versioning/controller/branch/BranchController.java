package it.unisa.ddditserver.subsystems.versioning.controller.branch;

import it.unisa.ddditserver.subsystems.versioning.dto.BranchDTO;
import it.unisa.ddditserver.subsystems.versioning.dto.ResourceDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * Controller interface for branch endpoints.
 *
 * Provides operations for creating new branches and
 * listing branches associated with a specific resource
 * within the versioning subsystem.
 *
 * @author Angelo Antonio Prisco
 * @version 1.0
 * @since 2025-08-11
 */
public interface BranchController {

    /**
     * Handles the request to create a new branch.
     *
     * @param branchDTO the branch data transfer object containing branch details
     * @param request the HTTP servlet request object
     * @return a ResponseEntity indicating the result of the branch creation operation
     */
    ResponseEntity<Map<String, String>> createResource(@RequestBody BranchDTO branchDTO, HttpServletRequest request);

    /**
     * Handles the request to list all branches of a given resource.
     *
     * @param resourceDTO the resource data transfer object identifying the resource
     * @param request the HTTP servlet request object
     * @return a ResponseEntity containing the list of branches for the specified resource
     */
    ResponseEntity<Map<String, Object>> listBranchesByResource(@RequestBody ResourceDTO resourceDTO, HttpServletRequest request);
}
