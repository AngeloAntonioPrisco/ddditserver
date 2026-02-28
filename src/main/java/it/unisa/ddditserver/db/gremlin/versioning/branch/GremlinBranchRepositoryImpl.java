package it.unisa.ddditserver.db.gremlin.versioning.branch;

import it.unisa.ddditserver.db.gremlin.JanusConfig;
import it.unisa.ddditserver.subsystems.versioning.dto.BranchDTO;
import it.unisa.ddditserver.subsystems.versioning.dto.ResourceDTO;
import it.unisa.ddditserver.subsystems.versioning.exceptions.branch.BranchException;
import it.unisa.ddditserver.subsystems.versioning.exceptions.version.VersionException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.tinkerpop.gremlin.driver.Client;
import org.apache.tinkerpop.gremlin.driver.Cluster;
import org.apache.tinkerpop.gremlin.driver.Result;
import org.apache.tinkerpop.gremlin.util.ser.GraphBinaryMessageSerializerV1;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
public class GremlinBranchRepositoryImpl implements GremlinBranchRepository {

    private final JanusConfig config;
    private Cluster cluster;
    private Client client;

    @Autowired
    public GremlinBranchRepositoryImpl(JanusConfig config) {
        this.config = config;
    }

    @PostConstruct
    public void init() {
        this.cluster = Cluster.build()
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
        if (cluster != null) cluster.close();
    }

    @Override
    public void saveBranch(BranchDTO branchDTO) {
        String query = "g.V().hasLabel('repository').has('repositoryName', repoName)" +
                ".out('CONTAINS').hasLabel('resource').has('resourceName', resName)" +
                ".as('r')" +
                ".addV('branch')" +
                ".property('branchName', bName)" +
                ".as('b')" +
                ".addE('HAS_BRANCH').from('r').to('b')" +
                ".iterate(); g.tx().commit();";

        try {
            client.submit(query, Map.of(
                    "repoName", branchDTO.getRepositoryName(),
                    "resName", branchDTO.getResourceName(),
                    "bName", branchDTO.getBranchName())).all().get();
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            throw new BranchException("Error creating branch: " + e.getMessage());
        }
    }

    @Override
    public boolean existsByResource(BranchDTO branchDTO) {
        String query = "g.V().hasLabel('repository').has('repositoryName', repoName)" +
                ".out('CONTAINS').hasLabel('resource').has('resourceName', resName)" +
                ".out('HAS_BRANCH').hasLabel('branch').has('branchName', bName).count()";
        try {
            List<Result> results = client.submit(query, Map.of(
                    "repoName", branchDTO.getRepositoryName(),
                    "resName", branchDTO.getResourceName(),
                    "bName", branchDTO.getBranchName())).all().get();
            return !results.isEmpty() && results.get(0).getLong() > 0;
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            throw new BranchException("Error checking branch existence: " + e.getMessage());
        }
    }

    @Override
    public List<BranchDTO> findBranchesByResource(ResourceDTO resourceDTO) {
        String repositoryName = resourceDTO.getRepositoryName();
        String resourceName = resourceDTO.getResourceName();

        String query = "g.V().hasLabel('repository').has('repositoryName', repositoryName)" +
                ".out('CONTAINS').hasLabel('resource').has('resourceName', resourceName)" +
                ".out('HAS_BRANCH').hasLabel('branch').valueMap('branchName')";

        try {
            List<Result> results = client.submit(query, Map.of(
                    "repositoryName", repositoryName,
                    "resourceName", resourceName)).all().get();

            List<BranchDTO> branches = new ArrayList<>();

            for (Result result : results) {
                @SuppressWarnings("unchecked")
                Map<String, List<Object>> props = (Map<String, List<Object>>) result.getObject();

                String branchName = props.get("branchName").get(0).toString();
                branches.add(new BranchDTO(repositoryName, resourceName, branchName));
            }

            return branches;
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            throw new VersionException("Error finding branches by resource in JanusGraph");
        }
    }
}