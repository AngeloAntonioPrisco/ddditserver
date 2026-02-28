package it.unisa.ddditserver.subsystems.ai.service;

import it.unisa.ddditserver.subsystems.versioning.dto.version.VersionDTO;
import java.util.List;

/**
 * Service interface for classification-related operations in the ai subsystem.
 * Provides methods to associate tags to a mesh.
 *
 * @author Angelo Antonio Prisco
 * @version 1.0
 * @since 2025-08-30
 */
public interface TagClassificationService {
    /**
     * Associate a list of the to a version.
     *
     * @param versionDTO the data transfer object containing the mesh version information
     * @return a list of tags representing te result of classification
     */
    List<String> classify(VersionDTO versionDTO);

    void getOnnxModelsInFolder();
}
