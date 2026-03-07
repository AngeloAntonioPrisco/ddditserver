package it.unisa.ddditserver.subsystems.versioning.dto.version;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Data Transfer Object (DTO) representing a version of a resource in a repository.
 *
 * <ul>
 *     <li>{@code repositoryName} - the name of the repository containing the resource.</li>
 *     <li>{@code resourceName} - the name of the resource for which the version is created.</li>
 *     <li>{@code branchName} - the name of the branch where the version resides.</li>
 *     <li>{@code versionName} - the name of the version.</li>
 *     <li>{@code username} - the username of the user who pushed the version.</li>
 *     <li>{@code pushedAt} - the date and time when the version was pushed.</li>
 *     <li>{@code comment} - an optional comment describing the version.</li>
 *     <li>{@code tags} - a list of tags associated with the version.</li>
 *     <li>{@code mesh} - the uploaded mesh file for the version.</li>
 *     <li>{@code material} - a list of uploaded textures files for the version.</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VersionDTO {
    private String repositoryName;
    private String resourceName;
    private String branchName;
    private String versionName;
    private String username;
    private LocalDateTime pushedAt;
    private String comment;
    private List<String> tags;
    private MultipartFile mesh;
    private List<MultipartFile> material;

    public String getTagsAsString() {
        if (this.tags == null || this.tags.isEmpty()) {
            return "";
        }

        return String.join(", ", this.tags);
    }
}
