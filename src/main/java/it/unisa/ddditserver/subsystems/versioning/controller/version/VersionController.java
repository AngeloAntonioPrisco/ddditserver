package it.unisa.ddditserver.subsystems.versioning.controller.version;

import it.unisa.ddditserver.subsystems.versioning.dto.version.VersionDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Map;

/**
 * Controller interface for version endpoints.
 *
 * Provides operations for pushing mesh and material versions,
 * retrieving specific versions, and displaying version metadata
 * within the versioning subsystem.
 *
 * @author Angelo Antonio Prisco
 * @version 1.0
 * @since 2025-08-14
 */
public interface VersionController {

    /**
     * Handles the request to create a new version of a mesh or of a material.
     *
     * @param versionDTO the version data transfer object containing version details and mesh file information or material information
     * @param request the HTTP servlet request object
     * @return a ResponseEntity indicating the result of the version creation operation
     */
    ResponseEntity<Map<String, String>> pushVersion(@ModelAttribute VersionDTO versionDTO, HttpServletRequest request);

    /**
     * Handles the request to retrieve a specific version resource.
     *
     * @param versionDTO the version data transfer object identifying the version to retrieve
     * @param request the HTTP servlet request object
     * @return a ResponseEntity containing the requested resource or an error status
     */
    ResponseEntity<?> pullVersion(@ModelAttribute VersionDTO versionDTO, HttpServletRequest request);

    /**
     * Handles the request to display metadata for a specific version.
     *
     * @param versionDTO the version data transfer object identifying the version
     * @param request the HTTP servlet request object
     * @return a ResponseEntity containing the version metadata or an error status
     */
    ResponseEntity<Map<String, Object>> showVersionMetadata(@ModelAttribute VersionDTO versionDTO, HttpServletRequest request);
}