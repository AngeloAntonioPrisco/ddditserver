package it.unisa.ddditserver.db.blobstorage.versioning;

import io.minio.*;
import io.minio.messages.Item;
import it.unisa.ddditserver.db.blobstorage.MinioConfig;
import it.unisa.ddditserver.subsystems.versioning.dto.version.VersionDTO;
import it.unisa.ddditserver.subsystems.versioning.exceptions.version.VersionException;
import jakarta.annotation.PostConstruct;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.stereotype.Repository;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Repository
public class BlobStorageVersionRepositoryImpl implements BlobStorageVersionRepository {
    private final MinioConfig config;
    private MinioClient minioClient;

    public BlobStorageVersionRepositoryImpl(MinioConfig config) {
        this.config = config;
    }

    @PostConstruct
    public void init() {
        try {
            minioClient = MinioClient.builder()
                    .endpoint(config.getEndpoint())
                    .credentials(config.getUsername(), config.getPassword())
                    .build();

            // Bucket creation if not existing
            if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(config.getMeshesBucket()).build())) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(config.getMeshesBucket()).build());
            }
            if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(config.getMaterialsBucket()).build())) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(config.getMaterialsBucket()).build());
            }
        } catch (Exception e) {
            throw new RuntimeException("Errore durante la creazione dei bucket MinIO", e);
        }
    }

    @Override
    public String saveMesh(VersionDTO versionDTO) {
        String objectName = versionDTO.getRepositoryName() + "/" +
                versionDTO.getResourceName() + "/" +
                versionDTO.getBranchName() + "/" +
                versionDTO.getVersionName() + "/" +
                versionDTO.getMesh().getOriginalFilename();
        try (InputStream is = versionDTO.getMesh().getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(config.getMeshesBucket())
                    .object(objectName)
                    .stream(is, versionDTO.getMesh().getSize(), -1)
                    .contentType(versionDTO.getMesh().getContentType())
                    .build());
        } catch (Exception e) {
            throw new VersionException("Errore durante il salvataggio del mesh su MinIO");
        }
        return config.getEndpoint() + "/" + config.getMeshesBucket() + "/" + objectName;
    }

    @Override
    public String saveMaterial(VersionDTO versionDTO) {

        private static final String PATH_SEPARATOR = "/";

        String basePath = versionDTO.getRepositoryName() + PATH_SEPARATOR +
                versionDTO.getResourceName() + PATH_SEPARATOR +
                versionDTO.getBranchName() + PATH_SEPARATOR +
                versionDTO.getVersionName() + PATH_SEPARATOR;

        try {
            for (var texture : versionDTO.getMaterial()) {
                String objectName = basePath + texture.getOriginalFilename();
                try (InputStream is = texture.getInputStream()) {
                    minioClient.putObject(PutObjectArgs.builder()
                            .bucket(config.getMaterialsBucket())
                            .object(objectName)
                            .stream(is, texture.getSize(), -1)
                            .contentType(texture.getContentType())
                            .build());
                }
            }
        } catch (Exception e) {
            throw new VersionException("Errore durante il salvataggio dei materiali su MinIO");
        }

        return config.getEndpoint() + "/" + config.getMaterialsBucket() + "/" + basePath;
    }

    @Override
    public boolean existsMeshByUrl(String meshUrl) {
        if (meshUrl == null || meshUrl.isEmpty()) return false;
        String objectName = meshUrl.substring(meshUrl.lastIndexOf(config.getMeshesBucket() + "/") + (config.getMeshesBucket() + "/").length());
        try {
            return minioClient.statObject(StatObjectArgs.builder()
                    .bucket(config.getMeshesBucket())
                    .object(objectName)
                    .build()) != null;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean existsMaterialByUrl(String materialFolderUrl) {
        if (materialFolderUrl == null || materialFolderUrl.isEmpty()) return false;
        String prefix = materialFolderUrl.substring(materialFolderUrl.lastIndexOf(config.getMaterialsBucket() + "/") + (config.getMaterialsBucket() + "/").length());
        try {
            Iterable<Result<Item>> objects = minioClient.listObjects(ListObjectsArgs.builder()
                    .bucket(config.getMaterialsBucket())
                    .prefix(prefix)
                    .recursive(true)
                    .build());
            return objects.iterator().hasNext();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public Triple<InputStream, String, String> findMeshByUrl(String meshUrl) {
        String objectName = meshUrl.substring(meshUrl.lastIndexOf(config.getMeshesBucket() + "/") + (config.getMeshesBucket() + "/").length());
        try {
            InputStream is = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(config.getMeshesBucket())
                    .object(objectName)
                    .build());
            String contentType = "application/octet-stream"; // MinIO non memorizza sempre il content type
            String fileName = objectName.substring(objectName.lastIndexOf("/") + 1);
            return Triple.of(is, contentType, fileName);
        } catch (Exception e) {
            throw new VersionException("Errore durante il recupero del mesh da MinIO");
        }
    }

    @Override
    public List<Triple<InputStream, String, String>> findMaterialByUrl(String materialFolderUrl) {
        String prefix = materialFolderUrl.substring(materialFolderUrl.lastIndexOf(config.getMaterialsBucket() + "/") + (config.getMaterialsBucket() + "/").length());
        List<Triple<InputStream, String, String>> result = new ArrayList<>();
        try {
            Iterable<Result<Item>> objects = minioClient.listObjects(ListObjectsArgs.builder()
                    .bucket(config.getMaterialsBucket())
                    .prefix(prefix)
                    .recursive(true)
                    .build());
            for (Result<Item> r : objects) {
                Item item = r.get();
                InputStream is = minioClient.getObject(GetObjectArgs.builder()
                        .bucket(config.getMaterialsBucket())
                        .object(item.objectName())
                        .build());
                String fileName = item.objectName().substring(item.objectName().lastIndexOf("/") + 1);
                result.add(Triple.of(is, "application/octet-stream", fileName));
            }
        } catch (Exception e) {
            throw new VersionException("Errore durante il recupero dei materiali da MinIO");
        }
        return result;
    }

    @Override
    public void deleteMeshByUrl(String meshUrl) {
        String objectName = meshUrl.substring(meshUrl.lastIndexOf(config.getMeshesBucket() + "/") + (config.getMeshesBucket() + "/").length());
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(config.getMeshesBucket())
                    .object(objectName)
                    .build());
        } catch (Exception e) {
            throw new VersionException("Errore durante la cancellazione del mesh da MinIO");
        }
    }

    @Override
    public void deleteMaterialByUrl(String materialFolderUrl) {
        String prefix = materialFolderUrl.substring(materialFolderUrl.lastIndexOf(config.getMaterialsBucket() + "/") + (config.getMaterialsBucket() + "/").length());
        try {
            Iterable<Result<Item>> objects = minioClient.listObjects(ListObjectsArgs.builder()
                    .bucket(config.getMaterialsBucket())
                    .prefix(prefix)
                    .recursive(true)
                    .build());
            for (Result<Item> r : objects) {
                Item item = r.get();
                minioClient.removeObject(RemoveObjectArgs.builder()
                        .bucket(config.getMaterialsBucket())
                        .object(item.objectName())
                        .build());
            }
        } catch (Exception e) {
            throw new VersionException("Errore durante la cancellazione dei materiali da MinIO");
        }
    }
}