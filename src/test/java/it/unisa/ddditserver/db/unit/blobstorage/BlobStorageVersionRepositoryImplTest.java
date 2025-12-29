package it.unisa.ddditserver.db.unit.blobstorage;

import io.minio.*;
import io.minio.messages.Item;
import it.unisa.ddditserver.db.blobstorage.MinioConfig;
import it.unisa.ddditserver.db.blobstorage.versioning.BlobStorageVersionRepositoryImpl;
import it.unisa.ddditserver.subsystems.versioning.dto.version.VersionDTO;
import org.apache.commons.lang3.tuple.Triple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class BlobStorageVersionRepositoryImplTest {

    @Mock
    private MinioConfig config;

    @Mock
    private MinioClient minioClient;

    private BlobStorageVersionRepositoryImpl repository;

    @BeforeEach
    public void setUp() throws Exception {
        repository = new BlobStorageVersionRepositoryImpl(config);

        Field clientField = BlobStorageVersionRepositoryImpl.class.getDeclaredField("minioClient");
        clientField.setAccessible(true);
        clientField.set(repository, minioClient);

        lenient().when(config.getMeshesBucket()).thenReturn("meshes");
        lenient().when(config.getMaterialsBucket()).thenReturn("materials");
        lenient().when(config.getEndpoint()).thenReturn("http://localhost:9000");
    }

    @Test
    void saveMeshSuccess() throws Exception {
        VersionDTO version = new VersionDTO();
        version.setRepositoryName("repo");
        version.setResourceName("res");
        version.setVersionName("v1");

        MultipartFile mesh = mock(MultipartFile.class);
        when(mesh.getOriginalFilename()).thenReturn("mesh.fbx");
        when(mesh.getSize()).thenReturn(100L);
        when(mesh.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[]{1,2,3}));
        when(mesh.getContentType()).thenReturn("application/octet-stream");
        version.setMesh(mesh);

        String url = repository.saveMesh(version);

        assertNotNull(url);
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    void saveMaterialSuccess() throws Exception {
        VersionDTO version = new VersionDTO();
        version.setRepositoryName("repo");
        version.setResourceName("res");
        version.setBranchName("main");
        version.setVersionName("v1");

        MultipartFile texture = mock(MultipartFile.class);
        when(texture.getOriginalFilename()).thenReturn("texture.png");
        when(texture.getSize()).thenReturn(50L);
        when(texture.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[]{4,5,6}));
        when(texture.getContentType()).thenReturn("image/png");
        version.setMaterial(List.of(texture));

        String url = repository.saveMaterial(version);

        assertNotNull(url);
        verify(minioClient, atLeastOnce()).putObject(any(PutObjectArgs.class));
    }

    @Test
    void existsMaterialByUrlReturnsTrue() throws Exception {
        String materialUrl = "http://localhost:9000/materials/repo/res/v1/";

        @SuppressWarnings("unchecked")
        Result<Item> mockItem = (Result<Item>) mock(Result.class);

        // NON serve when(mockItem.get()) perché existsMaterialByUrl usa solo iterator().hasNext()
        when(minioClient.listObjects(any(ListObjectsArgs.class))).thenReturn(List.of(mockItem));

        assertTrue(repository.existsMaterialByUrl(materialUrl));
    }

    @Test
    void findMeshByUrlSuccess() throws Exception {
        String meshUrl = "http://localhost:9000/meshes/repo/res/v1/mesh.fbx";
        GetObjectResponse response = mock(GetObjectResponse.class);

        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(response);

        Triple<InputStream, String, String> result = repository.findMeshByUrl(meshUrl);

        assertEquals("mesh.fbx", result.getRight());
        assertNotNull(result.getLeft());
    }

    @Test
    void findMaterialByUrlSuccess() throws Exception {
        String materialUrl = "http://localhost:9000/materials/repo/res/v1/";

        Item item = mock(Item.class);
        when(item.objectName()).thenReturn("repo/res/v1/texture.png");

        @SuppressWarnings("unchecked")
        Result<Item> mockResult = (Result<Item>) mock(Result.class);
        when(mockResult.get()).thenReturn(item);

        when(minioClient.listObjects(any(ListObjectsArgs.class))).thenReturn(List.of(mockResult));
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(mock(GetObjectResponse.class));

        List<Triple<InputStream, String, String>> results = repository.findMaterialByUrl(materialUrl);

        assertFalse(results.isEmpty());
        assertEquals("texture.png", results.get(0).getRight());
    }

    @Test
    void deleteMeshByUrlSuccess() throws Exception {
        String meshUrl = "http://localhost:9000/meshes/repo/res/v1/mesh.fbx";
        repository.deleteMeshByUrl(meshUrl);
        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    void deleteMaterialByUrlSuccess() throws Exception {
        String materialUrl = "http://localhost:9000/materials/repo/res/v1/";
        
        Item item = mock(Item.class);
        when(item.objectName()).thenReturn("repo/res/v1/texture.png");
        Result<Item> mockResult = (Result<Item>) mock(Result.class);
        when(mockResult.get()).thenReturn(item);

        when(minioClient.listObjects(any(ListObjectsArgs.class))).thenReturn(List.of(mockResult));

        repository.deleteMaterialByUrl(materialUrl);

        verify(minioClient, atLeastOnce()).removeObject(any(RemoveObjectArgs.class));
    }
}

