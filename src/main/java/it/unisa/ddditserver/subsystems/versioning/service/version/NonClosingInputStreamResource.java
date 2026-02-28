package it.unisa.ddditserver.subsystems.versioning.service.version;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.core.io.InputStreamResource;
import java.io.InputStream;

/**
 * A specialized {@link InputStreamResource} that represents an input stream
 * resource with a specific filename and content type, where the underlying
 * input stream is not automatically closed by the resource.
 *
 * @author Angelo Antonio Prisco
 * @version 1.0
 * @since 2025-08-23
 */
@Getter
@EqualsAndHashCode(callSuper = false)
public class NonClosingInputStreamResource extends InputStreamResource {
    private final String filename;
    @Getter
    private final String contentType;

    public NonClosingInputStreamResource(InputStream inputStream, String filename, String contentType) {
        super(inputStream);
        this.filename = filename;
        this.contentType = contentType;
    }

    @Override
    public String getFilename() {
        return filename;
    }

    @Override
    public long contentLength() {
        return -1;
    }
}


