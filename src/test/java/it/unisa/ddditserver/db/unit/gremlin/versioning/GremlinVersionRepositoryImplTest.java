package it.unisa.ddditserver.db.unit.gremlin.versioning;

import it.unisa.ddditserver.db.blobstorage.versioning.BlobStorageVersionRepository;
import it.unisa.ddditserver.db.cosmos.versioning.CosmosVersionRepository;
import it.unisa.ddditserver.db.gremlin.JanusConfig;
import it.unisa.ddditserver.db.gremlin.versioning.version.GremlinVersionRepositoryImpl;
import it.unisa.ddditserver.subsystems.versioning.dto.version.VersionDTO;
import it.unisa.ddditserver.subsystems.versioning.exceptions.version.VersionException;
import org.apache.tinkerpop.gremlin.driver.Client;
import org.apache.tinkerpop.gremlin.driver.Result;
import org.apache.tinkerpop.gremlin.driver.ResultSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GremlinVersionRepositoryImplTest {

    @Mock private JanusConfig config;
    @Mock private CosmosVersionRepository cosmosService;
    @Mock private BlobStorageVersionRepository blobStorageService;
    @Mock private Client gremlinClient;
    @Mock private ResultSet resultSet;
    @Mock private Result result;

    @InjectMocks
    private GremlinVersionRepositoryImpl repository;

    private VersionDTO versionDTO;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(repository, "client", gremlinClient);

        versionDTO = new VersionDTO();
        versionDTO.setRepositoryName("RepoTest");
        versionDTO.setResourceName("ResTest");
        versionDTO.setBranchName("main");
        versionDTO.setVersionName("v1.0.0");
        versionDTO.setPushedAt(LocalDateTime.now());
    }

    @Test
    void testSaveVersion_Success() {
        when(blobStorageService.saveMesh(any())).thenReturn("blob_url");
        when(cosmosService.saveVersion(any(), anyString())).thenReturn("cosmos_url");

        CompletableFuture<List<Result>> branchFuture = CompletableFuture.completedFuture(List.of(result));
        when(gremlinClient.submit(contains("HAS_BRANCH"), anyMap())).thenReturn(resultSet);
        when(resultSet.all()).thenReturn(branchFuture);
        when(result.getObject()).thenReturn("branch_id_123");

        ResultSet chainResultSet = mock(ResultSet.class);
        when(gremlinClient.submit(contains("HAS_VERSION"), anyMap())).thenReturn(chainResultSet);
        when(chainResultSet.all()).thenReturn(CompletableFuture.completedFuture(Collections.emptyList()));

        ResultSet createResultSet = mock(ResultSet.class);
        when(gremlinClient.submit(contains("addV('version')"), anyMap())).thenReturn(createResultSet);
        when(createResultSet.one()).thenReturn(result);
        when(result.getObject()).thenReturn("new_version_id");

        assertDoesNotThrow(() -> repository.saveVersion(versionDTO, true));

        verify(gremlinClient, atLeastOnce()).submit(anyString(), anyMap());
        verify(blobStorageService, never()).deleteMeshByUrl(any()); // Nessun rollback
    }

    @Test
    void testSaveVersion_BranchNotFound_ShouldRollback() {
        when(blobStorageService.saveMesh(any())).thenReturn("blob_url");
        when(cosmosService.saveVersion(any(), anyString())).thenReturn("cosmos_url");

        CompletableFuture<List<Result>> emptyFuture = CompletableFuture.completedFuture(Collections.emptyList());
        when(gremlinClient.submit(anyString(), anyMap())).thenReturn(resultSet);
        when(resultSet.all()).thenReturn(emptyFuture);

        assertThrows(VersionException.class, () -> repository.saveVersion(versionDTO, true));

        verify(blobStorageService, times(2)).deleteMeshByUrl("blob_url");
        verify(cosmosService, times(2)).deleteVersionByUrl("cosmos_url");
    }

    @Test
    void testExistsByVersion_Found() {
        when(gremlinClient.submit(anyString(), anyMap())).thenReturn(resultSet);
        when(resultSet.all()).thenReturn(CompletableFuture.completedFuture(List.of(result)));
        when(result.getLong()).thenReturn(1L);

        boolean exists = repository.existsByVersion(versionDTO);

        assertTrue(exists);
        verify(gremlinClient).submit(contains("count()"), anyMap());
    }

    @Test
    void testFindVersionByBranch_Success() {
        Map<String, Object> mockProps = Map.of(
                "cosmosDocumentUrl", List.of("cosmos_url_123"),
                "username", List.of("mario_rossi"),
                "comment", List.of("First commit"),
                "pushedAt", List.of(LocalDateTime.now().toString())
        );

        when(gremlinClient.submit(anyString(), anyMap())).thenReturn(resultSet);
        when(resultSet.all()).thenReturn(CompletableFuture.completedFuture(List.of(result)));
        when(result.get(Map.class)).thenReturn(mockProps);

        VersionDTO cosmosDto = new VersionDTO();
        cosmosDto.setVersionName("v1");
        when(cosmosService.findVersionByUrl("cosmos_url_123")).thenReturn(cosmosDto);

        VersionDTO r = repository.findVersionByBranch(versionDTO);

        assertNotNull(result);
        assertEquals("mario_rossi", r.getUsername());
        assertEquals("First commit", r.getComment());
    }
}