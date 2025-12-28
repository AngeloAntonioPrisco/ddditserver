package it.unisa.ddditserver.db.blobstorage;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Component
public class MinioConfig {

    @Value("${MINIO_HOST}")
    private String host;

    @Value("${MINIO_PORT}")
    private int port;

    @Value("${MINIO_ROOT_USERNAME}")
    private String username;

    @Value("${MINIO_ROOT_PASSWORD}")
    private String password;

    @Value("${BLOB_STORAGE_BUCKET_MESHES}")
    private String meshesBucket;

    @Value("${BLOB_STORAGE_BUCKET_MATERIALS}")
    private String materialsBucket;

    public String getEndpoint() {
        return "http://" + host + ":" + port; // solo host e porta
    }
}
