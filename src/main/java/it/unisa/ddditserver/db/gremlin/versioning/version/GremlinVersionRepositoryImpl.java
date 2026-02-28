package it.unisa.ddditserver.db.gremlin.versioning.version;

import it.unisa.ddditserver.db.blobstorage.versioning.BlobStorageVersionRepository;
import it.unisa.ddditserver.db.cosmos.versioning.CosmosVersionRepository;
import it.unisa.ddditserver.db.gremlin.JanusConfig;
import it.unisa.ddditserver.subsystems.versioning.dto.BranchDTO;
import it.unisa.ddditserver.subsystems.versioning.dto.version.VersionDTO;
import it.unisa.ddditserver.subsystems.versioning.exceptions.version.VersionException;
import it.unisa.ddditserver.subsystems.versioning.service.version.NonClosingInputStreamResource;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.tinkerpop.gremlin.driver.Client;
import org.apache.tinkerpop.gremlin.driver.Cluster;
import org.apache.tinkerpop.gremlin.driver.Result;
import org.apache.tinkerpop.gremlin.util.ser.GraphBinaryMessageSerializerV1;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
public class GremlinVersionRepositoryImpl implements GremlinVersionRepository {

    private static final String BNAME_KEY = "bName";

    private static final String RES_NAME_KEY = "resName";

    private static final String REPO_NAME_KEY = "repoName";

    private final JanusConfig config;
    private final CosmosVersionRepository cosmosService;
    private final BlobStorageVersionRepository blobStorageService;
    private Client client;

    @Autowired
    public GremlinVersionRepositoryImpl(JanusConfig config,
                                        CosmosVersionRepository cosmosService,
                                        BlobStorageVersionRepository blobStorageService) {
        this.config = config;
        this.cosmosService = cosmosService;
        this.blobStorageService = blobStorageService;
    }

    @PostConstruct
    public void init() {
        Cluster cluster = Cluster.build()
                .addContactPoint(config.getHost())
                .port(config.getPort())
                .credentials(config.getUsername(), config.getPassword())
                .serializer(new GraphBinaryMessageSerializerV1())
                .create();

        this.client = cluster.connect();
    }

    @PreDestroy
    public void close() {
        if (client != null) client.close();
    }

    @Override
    public void saveVersion(VersionDTO versionDTO, boolean resourceType) {
        String url = "";
        String cosmosDocumentUrl = "";

        try {
            url = resourceType ? blobStorageService.saveMesh(versionDTO) : blobStorageService.saveMaterial(versionDTO);
            cosmosDocumentUrl = cosmosService.saveVersion(versionDTO, url);

            String findBranchQuery = "g.V().hasLabel('repository').has('repositoryName', repoName)" +
                    ".out('CONTAINS').hasLabel('resource').has('resourceName', resName)" +
                    ".out('HAS_BRANCH').hasLabel('branch').has('branchName', bName)" +
                    ".id()";

            List<Result> branchResults = client.submit(findBranchQuery, Map.of(
                    REPO_NAME_KEY, versionDTO.getRepositoryName(),
                    RES_NAME_KEY, versionDTO.getResourceName(),
                    BNAME_KEY, versionDTO.getBranchName())).all().get();

            if (branchResults.isEmpty()) {
                rollback(url, cosmosDocumentUrl, resourceType);
                throw new VersionException("Branch not found for resource " + versionDTO.getResourceName());
            }

                Object branchId = branchResults.get(0).getObject();

                String getChainQuery = "g.V(branchId).out('HAS_VERSION').repeat(out('HAS_NEXT_VERSION')).emit().id()";
                List<Result> chainResults = client.submit(getChainQuery, Map.of("branchId", branchId)).all().get();

                String createVersionQuery = "g.addV('version')" +
                        ".property('versionName', vName)" +
                        ".property('username', uName)" +
                        ".property('pushedAt', pTime.toString())" +
                        ".property('comment', vComment)" +
                        ".property('tags', vTags)" +
                        ".property('cosmosDocumentUrl', cUrl)" +
                        ".property('resourceType', rType)" +
                        ".id()";

                Object newVersionId = client.submit(createVersionQuery, Map.of(
                        "vName", versionDTO.getVersionName(),
                        "uName", versionDTO.getUsername() != null ? versionDTO.getUsername() : "anonymous",
                        "pTime", versionDTO.getPushedAt() != null ? versionDTO.getPushedAt() : LocalDateTime.now(), // Valore per la data
                        "vComment", versionDTO.getComment() != null ? versionDTO.getComment() : "",
                        "vTags", versionDTO.getTagsAsString(),
                        "cUrl", cosmosDocumentUrl,
                        "rType", resourceType ? "mesh" : "material")).one().getObject();

                if (chainResults.isEmpty()) {
                    String linkQuery = "g.V(vId).as('v').V(branchId).addE('HAS_VERSION').to('v').iterate(); g.tx().commit();";
                    client.submit(linkQuery, Map.of("branchId", branchId, "vId", newVersionId)).all().get();
                } else {
                    Object lastVersionId = chainResults.get(chainResults.size() - 1).getObject();
                    String linkQuery = "g.V(vId).as('v').V(lastId).addE('HAS_NEXT_VERSION').to('v').iterate(); g.tx().commit();";
                    client.submit(linkQuery, Map.of("lastId", lastVersionId, "vId", newVersionId)).all().get();
                }

            } catch (Exception e) {
            rollback(url, cosmosDocumentUrl, resourceType);
            throw new VersionException("Error saving new version in JanusGraph: " + e.getMessage());
        }
    }

    private void rollback(String url, String cosmosUrl, boolean resourceType) {
        if (url != null && !url.isEmpty()) {
            if (resourceType) blobStorageService.deleteMeshByUrl(url);
            else blobStorageService.deleteMaterialByUrl(url);
        }
        if (cosmosUrl != null && !cosmosUrl.isEmpty()) {
            cosmosService.deleteVersionByUrl(cosmosUrl);
        }
    }

    @Override
    public boolean existsByVersion(VersionDTO versionDTO) {
        String query = getBaseVersionQuery() + ".has('versionName', vName).count()";
        try {
            List<Result> results = client.submit(query, getVersionParams(versionDTO)).all().get();
            return !results.isEmpty() && results.get(0).getLong() > 0;
        } catch (Exception e) {
            throw new VersionException("Error checking version existence: " + e.getMessage());
        }
    }

    @Override
    public VersionDTO findVersionByBranch(VersionDTO versionDTO) {
        String query = getBaseVersionQuery() + ".has('versionName', vName).valueMap('cosmosDocumentUrl', 'pushedAt', 'username', 'comment', 'tags')";
        try {
            List<Result> results = client.submit(query, getVersionParams(versionDTO)).all().get();
            if (results.isEmpty()) throw new VersionException("Version not found");

            Map<Object, Object> props = results.get(0).get(Map.class);

            String cosmosUrl = safeExtractString(props.get("cosmosDocumentUrl"));
            if (cosmosUrl == null) throw new VersionException("Cosmos document URL missing in Graph");

            VersionDTO dto = cosmosService.findVersionByUrl(cosmosUrl);

            dto.setUsername(safeExtractString(props.get("username")));
            dto.setComment(safeExtractString(props.get("comment")));

            String dateStr = safeExtractString(props.get("pushedAt"));
            if (dateStr != null) {
                try {
                    dto.setPushedAt(LocalDateTime.parse(dateStr));
                } catch (Exception e) {
                    dto.setPushedAt(LocalDateTime.now());
                }
            } else if (dto.getPushedAt() == null) {
                dto.setPushedAt(LocalDateTime.now());
            }
            
            return dto;
        } catch (Exception e) {
            e.printStackTrace();
            throw new VersionException("Error retrieving version: " + e.getMessage());
        }
    }

    private String safeExtractString(Object obj) {
        if (obj == null) return null;
        if (obj instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            return first != null ? String.valueOf(first) : null;
        }
        return String.valueOf(obj);
    }

    @Override
    public List<VersionDTO> findVersionsByBranch(BranchDTO branchDTO) {
        String query = "g.V().hasLabel('repository').has('repositoryName', repoName)" +
                ".out('CONTAINS').hasLabel('resource').has('resourceName', resName)" +
                ".out('HAS_BRANCH').hasLabel('branch').has('branchName', bName)" +
                ".out('HAS_VERSION').union(identity(), repeat(out('HAS_NEXT_VERSION')).emit())" +
                ".valueMap('versionName')";
        try {
            List<Result> results = client.submit(query, Map.of(
                    REPO_NAME_KEY, branchDTO.getRepositoryName(),
                    RES_NAME_KEY, branchDTO.getResourceName(),
                    BNAME_KEY, branchDTO.getBranchName())).all().get();

            List<VersionDTO> versions = new ArrayList<>();
            for (Result r : results) {
                @SuppressWarnings("unchecked")
                Map<Object, List<Object>> props = r.get(Map.class);
                VersionDTO dto = new VersionDTO();
                dto.setVersionName(props.get("versionName").get(0).toString());
                versions.add(dto);
            }
            return versions;
        } catch (Exception e) {
            throw new VersionException("Error finding versions: " + e.getMessage());
        }
    }

    @Override
    public List<Pair<NonClosingInputStreamResource, String>> getFile(VersionDTO versionDTO) {
        String query = getBaseVersionQuery() + ".has('versionName', vName).valueMap('cosmosDocumentUrl', 'resourceType')";
        try {
            List<Result> results = client.submit(query, getVersionParams(versionDTO)).all().get();
            if (results.isEmpty()) throw new VersionException("Version not found");

            @SuppressWarnings("unchecked")
            Map<Object, List<Object>> props = results.get(0).get(Map.class);
            String cosmosUrl = props.get("cosmosDocumentUrl").get(0).toString();
            String type = props.get("resourceType").get(0).toString();

            String blobUrl = cosmosService.getBlobUrlByUrl(cosmosUrl);

            if (type.equalsIgnoreCase("mesh")) {
                Triple<InputStream, String, String> data = blobStorageService.findMeshByUrl(blobUrl);
                return List.of(Pair.of(new NonClosingInputStreamResource(data.getLeft(), data.getRight(), data.getMiddle()), data.getMiddle()));
            } else {
                return blobStorageService.findMaterialByUrl(blobUrl).stream()
                        .map(t -> Pair.of(new NonClosingInputStreamResource(t.getLeft(), t.getRight(), t.getMiddle()), t.getMiddle()))
                        .toList();
            }
        } catch (Exception e) {
            throw new VersionException("Error retrieving file: " + e.getMessage());
        }
    }

    private String getBaseVersionQuery() {
        return "g.V().hasLabel('repository').has('repositoryName', repoName)" +
                ".out('CONTAINS').hasLabel('resource').has('resourceName', resName)" +
                ".out('HAS_BRANCH').hasLabel('branch').has('branchName', bName)" +
                ".out('HAS_VERSION').union(identity(), repeat(out('HAS_NEXT_VERSION')).emit())";
    }

    private Map<String, Object> getVersionParams(VersionDTO dto) {
        return Map.of(
                REPO_NAME_KEY, dto.getRepositoryName(),
                RES_NAME_KEY, dto.getResourceName(),
                BNAME_KEY, dto.getBranchName(),
                "vName", dto.getVersionName()
        );
    }
}