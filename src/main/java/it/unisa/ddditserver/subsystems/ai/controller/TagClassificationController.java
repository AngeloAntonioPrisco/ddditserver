package it.unisa.ddditserver.subsystems.ai.controller;

import org.springframework.http.ResponseEntity;
import java.util.Map;

/**
 * Controller interface for ai endpoints.
 *
 * Provides operations for reloading the classification model.
 *
 * @author Angelo Antonio Prisco
 * @version 1.0
 * @since 2025-08-30
 */
public interface TagClassificationController {
    /**
     * Handles the request to reload the classification model.
     *
     * @return a ResponseEntity indicating the result of the branch creation operation
     */
    ResponseEntity<Map<String, Object>> reloadModel();
}
