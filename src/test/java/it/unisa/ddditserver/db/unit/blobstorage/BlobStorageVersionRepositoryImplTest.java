package it.unisa.ddditserver.db.unit.blobstorage;

import io.minio.*;
import io.minio.messages.Item;
import it.unisa.ddditserver.db.blobstorage.MinioConfig;
import it.unisa.ddditserver.db.blobstorage.versioning.BlobStorageVersionRepositoryImpl;
import it.unisa.ddditserver.subsystems.versioning.dto.version.VersionDTO;
import it.unisa.ddditserver.subsystems.versioning.exceptions.version.VersionException;
import org.apache.commons.lang3.tuple.Triple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.List;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
 class BlobStorageVersionRepositoryImplTest {

    @Mock
    private MinioConfig config;

    @Mock
    private MinioClient minioClient;

    private BlobStorageVersionRepositoryImpl repository;

    private static final String ENDPOINT = "http://localhost:9000";
    private static final String MESHES_BUCKET = "meshes";
    private static final String MATERIALS_BUCKET = "materials";

    @BeforeEach
     void setUp() throws Exception {
        repository = new BlobStorageVersionRepositoryImpl(config);

        Field clientField = BlobStorageVersionRepositoryImpl.class.getDeclaredField("minioClient");
        clientField.setAccessible(true);
        clientField.set(repository, minioClient);

        lenient().when(config.getMeshesBucket()).thenReturn("meshes");
        lenient().when(config.getMaterialsBucket()).thenReturn("materials");
        lenient().when(config.getEndpoint()).thenReturn("http://localhost:9000");
    }

    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------

    private static VersionDTO baseVersion() {
        VersionDTO v = new VersionDTO();
        v.setRepositoryName("repo");
        v.setResourceName("res");
        v.setBranchName("main"); // IMPORTANT: used in saveMesh/saveMaterial paths
        v.setVersionName("v1");
        return v;
    }

    private static MultipartFile mockFile(String filename, String contentType, boolean streamThrows) throws Exception {
        MultipartFile f = mock(MultipartFile.class);

        // lenient -> evita UnnecessaryStubbing quando alcuni stub non vengono usati in certi casi
        lenient().when(f.getOriginalFilename()).thenReturn(filename);
        lenient().when(f.getSize()).thenReturn(3L);
        lenient().when(f.getContentType()).thenReturn(contentType);

        if (streamThrows) {
            lenient().when(f.getInputStream()).thenThrow(new IOException("stream failed"));
        } else {
            lenient().when(f.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));
        }
        return f;
    }

    private static Item mockItem(String objectName) {
        Item item = mock(Item.class);
        when(item.objectName()).thenReturn(objectName);
        return item;
    }

    @SuppressWarnings("unchecked")
    private static Result<Item> mockResult(Item item) throws Exception {
        Result<Item> r = (Result<Item>) mock(Result.class);
        when(r.get()).thenReturn(item);
        return r;
    }

    @Test
    void saveMeshSuccess() throws Exception {
        VersionDTO version = new VersionDTO();
        version.setRepositoryName("repo");
        version.setResourceName("res");
        version.setBranchName("main");
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
    void existsMaterialByUrlReturnsTrue() {
        String materialUrl = "http://localhost:9000/materials/repo/res/v1/";

        @SuppressWarnings("unchecked")
        Result<Item> mockItem = (Result<Item>) mock(Result.class);

        // It is not necessary when(mockItem.get()) because existsMaterialByUrl use only iterator().hasNext()
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
        assertEquals("application/octet-stream", result.getMiddle());
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
        assertNotNull(results.get(0).getLeft());
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


    // ------------------------------------------------------------
    // Category Partition providers
    // ------------------------------------------------------------

    // saveMesh: stream ok/throws, put ok/throws
    static Stream<Arguments> saveMeshCases() {
        return Stream.of(
                Arguments.of(false, false, false), // stream ok, put ok -> success
                Arguments.of(true,  false, true),  // stream throws -> VersionException
                Arguments.of(false, true,  true)   // put throws -> VersionException
        );
    }

    // saveMaterial: number of files, stream throws, put throws
    static Stream<Arguments> saveMaterialCases() {
        return Stream.of(
                Arguments.of(1, false, false, false),
                Arguments.of(2, false, false, false),
                Arguments.of(1, true,  false, true),
                Arguments.of(1, false, true,  true)
        );
    }

    // existsMeshByUrl: url null/empty/valid + stat throws or not
    static Stream<Arguments> existsMeshCases() {
        String valid = ENDPOINT + "/" + MESHES_BUCKET + "/repo/res/main/v1/mesh.fbx";
        return Stream.of(
                Arguments.of(null,  false, false),
                Arguments.of("",    false, false),
                Arguments.of(valid, false, true),
                Arguments.of(valid, true,  false)
        );
    }

    // existsMaterialByUrl: url null/empty/valid + list behavior
    static Stream<Arguments> existsMaterialCases() {
        String valid = ENDPOINT + "/" + MATERIALS_BUCKET + "/repo/res/main/v1/";
        return Stream.of(
                Arguments.of(null,  "EMPTY",  false),
                Arguments.of("",    "EMPTY",  false),
                Arguments.of(valid, "HAS",    true),
                Arguments.of(valid, "EMPTY",  false),
                Arguments.of(valid, "THROWS", false)
        );
    }

    // findMeshByUrl: url valid/null/empty + getObject throws or not
    static Stream<Arguments> findMeshCases() {
        String valid = ENDPOINT + "/" + MESHES_BUCKET + "/repo/res/main/v1/mesh.fbx";
        return Stream.of(
                Arguments.of(valid, false, "mesh.fbx", null),
                Arguments.of(valid, true,  null,       VersionException.class),
                Arguments.of(null,  false, null,       NullPointerException.class),
                Arguments.of("",    false, null,       StringIndexOutOfBoundsException.class)
        );
    }

    // findMaterialByUrl: listObjects empty/has/throws + getObject throws + url null/empty
    static Stream<Arguments> findMaterialCases() {
        String valid = ENDPOINT + "/" + MATERIALS_BUCKET + "/repo/res/main/v1/";
        return Stream.of(
                Arguments.of(valid, "EMPTY",  false, 0, null),
                Arguments.of(valid, "HAS",    false, 1, null),
                Arguments.of(valid, "HAS",    true,  0, VersionException.class),
                Arguments.of(valid, "THROWS", false, 0, VersionException.class),
                Arguments.of(null,  "EMPTY",  false, 0, NullPointerException.class),
                Arguments.of("",    "EMPTY",  false, 0, StringIndexOutOfBoundsException.class)
        );
    }

    // deleteMeshByUrl: url valid/null/empty + remove throws or not
    static Stream<Arguments> deleteMeshCases() {
        String valid = ENDPOINT + "/" + MESHES_BUCKET + "/repo/res/main/v1/mesh.fbx";
        return Stream.of(
                Arguments.of(valid, false, null),
                Arguments.of(valid, true,  VersionException.class),
                Arguments.of(null,  false, NullPointerException.class),
                Arguments.of("",    false, StringIndexOutOfBoundsException.class)
        );
    }

    // deleteMaterialByUrl: listObjects empty/has/throws + remove throws + url null/empty
    static Stream<Arguments> deleteMaterialCases() {
        String valid = ENDPOINT + "/" + MATERIALS_BUCKET + "/repo/res/main/v1/";
        return Stream.of(
                Arguments.of(valid, "EMPTY",  false, null),
                Arguments.of(valid, "HAS",    false, null),
                Arguments.of(valid, "HAS",    true,  VersionException.class),
                Arguments.of(valid, "THROWS", false, VersionException.class),
                Arguments.of(null,  "EMPTY",  false, NullPointerException.class),
                Arguments.of("",    "EMPTY",  false, StringIndexOutOfBoundsException.class)
        );
    }

    // ------------------------------------------------------------
    // Category Partition tests
    // ------------------------------------------------------------

    @ParameterizedTest
    @MethodSource("saveMeshCases")
    void saveMesh_categoryPartition(boolean streamThrows, boolean putThrows, boolean expectException) throws Exception {
        VersionDTO v = baseVersion();
        MultipartFile mesh = mockFile("mesh.fbx", "application/octet-stream", streamThrows);
        v.setMesh(mesh);

        if (putThrows) {
            doThrow(new RuntimeException("minio down")).when(minioClient).putObject(any(PutObjectArgs.class));
        }

        if (expectException) {
            assertThrows(VersionException.class, () -> repository.saveMesh(v));
        } else {
            String url = repository.saveMesh(v);
            assertNotNull(url);
            assertTrue(url.startsWith(ENDPOINT + "/" + MESHES_BUCKET + "/"));
            verify(minioClient).putObject(any(PutObjectArgs.class));
        }
    }

    @ParameterizedTest
    @MethodSource("saveMaterialCases")
    void saveMaterial_categoryPartition(int files, boolean streamThrows, boolean putThrows, boolean expectException) throws Exception {
        VersionDTO v = baseVersion();

        MultipartFile f1 = mockFile("t1.png", "image/png", streamThrows);
        MultipartFile f2 = mockFile("t2.png", "image/png", false);

        v.setMaterial(files == 1 ? List.of(f1) : List.of(f1, f2));

        if (putThrows) {
            doThrow(new RuntimeException("minio down")).when(minioClient).putObject(any(PutObjectArgs.class));
        }

        if (expectException) {
            assertThrows(VersionException.class, () -> repository.saveMaterial(v));
        } else {
            String url = repository.saveMaterial(v);
            assertNotNull(url);
            assertTrue(url.startsWith(ENDPOINT + "/" + MATERIALS_BUCKET + "/repo/res/main/v1/"));
            verify(minioClient, times(files)).putObject(any(PutObjectArgs.class));
        }
    }

    @ParameterizedTest
    @MethodSource("existsMeshCases")
    void existsMeshByUrl_categoryPartition(String url, boolean statThrows, boolean expected) throws Exception {
        if (url == null || url.isEmpty()) {
            assertEquals(expected, repository.existsMeshByUrl(url));
            return;
        }

        if (statThrows) {
            doThrow(new RuntimeException("boom")).when(minioClient).statObject(any(StatObjectArgs.class));
        } else {
            when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(mock(StatObjectResponse.class));
        }

        assertEquals(expected, repository.existsMeshByUrl(url));
    }

    @ParameterizedTest
    @MethodSource("existsMaterialCases")
    void existsMaterialByUrl_categoryPartition(String url, String listBehavior, boolean expected) {
        if (url == null || url.isEmpty()) {
            assertEquals(expected, repository.existsMaterialByUrl(url));
            return;
        }

        if ("THROWS".equals(listBehavior)) {
            when(minioClient.listObjects(any(ListObjectsArgs.class))).thenThrow(new RuntimeException("boom"));
        } else if ("HAS".equals(listBehavior)) {
            @SuppressWarnings("unchecked")
            Result<Item> mockItem = (Result<Item>) mock(Result.class);
            when(minioClient.listObjects(any(ListObjectsArgs.class))).thenReturn(List.of(mockItem));
        } else { // EMPTY
            when(minioClient.listObjects(any(ListObjectsArgs.class))).thenReturn(List.of());
        }

        assertEquals(expected, repository.existsMaterialByUrl(url));
    }

    @ParameterizedTest
    @MethodSource("findMeshCases")
    void findMeshByUrl_categoryPartition(String url, boolean getThrows, String expectedFilename, Class<? extends Throwable> expectedEx) throws Exception {
        if (expectedEx != null) {
            if (VersionException.class.equals(expectedEx) && getThrows) {
                when(minioClient.getObject(any(GetObjectArgs.class))).thenThrow(new RuntimeException("boom"));
            }
            assertThrows(expectedEx, () -> repository.findMeshByUrl(url));
            return;
        }

        when(minioClient.getObject(any(GetObjectArgs.class)))
                .thenReturn(mock(GetObjectResponse.class));

        Triple<InputStream, String, String> triple = repository.findMeshByUrl(url);
        assertNotNull(triple.getLeft());
        assertEquals("application/octet-stream", triple.getMiddle());
        assertEquals(expectedFilename, triple.getRight());
    }

    @ParameterizedTest
    @MethodSource("findMaterialCases")
    void findMaterialByUrl_categoryPartition(String url, String listBehavior, boolean getThrows, int expectedSize, Class<? extends Throwable> expectedEx) throws Exception {

        if (expectedEx != null) {
            if (url != null && !url.isEmpty()) {
                if ("THROWS".equals(listBehavior)) {
                    when(minioClient.listObjects(any(ListObjectsArgs.class))).thenThrow(new RuntimeException("boom"));
                } else if ("HAS".equals(listBehavior)) {
                    Item item = mockItem("repo/res/main/v1/t1.png");
                    Result<Item> r = mockResult(item);
                    when(minioClient.listObjects(any(ListObjectsArgs.class))).thenReturn(List.of(r));
                    if (getThrows) {
                        when(minioClient.getObject(any(GetObjectArgs.class))).thenThrow(new RuntimeException("boom"));
                    }
                } else {
                    when(minioClient.listObjects(any(ListObjectsArgs.class))).thenReturn(List.of());
                }
            }
            assertThrows(expectedEx, () -> repository.findMaterialByUrl(url));
            return;
        }

        if ("EMPTY".equals(listBehavior)) {
            when(minioClient.listObjects(any(ListObjectsArgs.class))).thenReturn(List.of());
        } else { // HAS
            Item item = mockItem("repo/res/main/v1/t1.png");
            Result<Item> r = mockResult(item);
            when(minioClient.listObjects(any(ListObjectsArgs.class))).thenReturn(List.of(r));
            when(minioClient.getObject(any(GetObjectArgs.class)))
                    .thenReturn(mock(GetObjectResponse.class));
        }

        List<Triple<InputStream, String, String>> res = repository.findMaterialByUrl(url);
        assertEquals(expectedSize, res.size());
        if (expectedSize > 0) {
            assertEquals("t1.png", res.get(0).getRight());
            assertNotNull(res.get(0).getLeft());
        }
    }

    @ParameterizedTest
    @MethodSource("deleteMeshCases")
    void deleteMeshByUrl_categoryPartition(String url, boolean removeThrows, Class<? extends Throwable> expectedEx) throws Exception {
        if (removeThrows) {
            doThrow(new RuntimeException("boom"))
                    .when(minioClient)
                    .removeObject(any(RemoveObjectArgs.class));
        }

        if (expectedEx != null) {
            assertThrows(expectedEx, () -> repository.deleteMeshByUrl(url));
        } else {
            assertDoesNotThrow(() -> repository.deleteMeshByUrl(url));
            verify(minioClient).removeObject(any(RemoveObjectArgs.class));
        }
    }

    @ParameterizedTest
    @MethodSource("deleteMaterialCases")
    void deleteMaterialByUrl_categoryPartition(String url, String listBehavior, boolean removeThrows, Class<? extends Throwable> expectedEx) throws Exception {

        if (url != null && !url.isEmpty()) {
            if ("THROWS".equals(listBehavior)) {
                when(minioClient.listObjects(any(ListObjectsArgs.class))).thenThrow(new RuntimeException("boom"));
            } else if ("EMPTY".equals(listBehavior)) {
                when(minioClient.listObjects(any(ListObjectsArgs.class))).thenReturn(List.of());
            } else { // HAS
                Item item = mockItem("repo/res/main/v1/t1.png");
                Result<Item> r = mockResult(item);
                when(minioClient.listObjects(any(ListObjectsArgs.class))).thenReturn(List.of(r));
            }
        }

        if (removeThrows) {
            doThrow(new RuntimeException("boom")).when(minioClient).removeObject(any(RemoveObjectArgs.class));
        }

        if (expectedEx != null) {
            assertThrows(expectedEx, () -> repository.deleteMaterialByUrl(url));
        } else {
            assertDoesNotThrow(() -> repository.deleteMaterialByUrl(url));
            if ("HAS".equals(listBehavior)) {
                verify(minioClient, atLeastOnce()).removeObject(any(RemoveObjectArgs.class));
            }
        }
    }

}

