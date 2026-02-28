package it.unisa.ddditserver.db.gremlin.versioning.resource;

import it.unisa.ddditserver.db.gremlin.JanusConfig;
import it.unisa.ddditserver.subsystems.versioning.dto.RepositoryDTO;
import it.unisa.ddditserver.subsystems.versioning.dto.ResourceDTO;
import it.unisa.ddditserver.subsystems.versioning.exceptions.resource.ResourceException;
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
public class GremlinResourceRepositoryImpl implements GremlinResourceRepository {

    private final JanusConfig config;
    private Cluster cluster;
    private Client client;

    @Autowired
    public GremlinResourceRepositoryImpl(JanusConfig config) {
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
    public void saveResource(ResourceDTO resourceDTO) {
        String query = "g.V().hasLabel('repository').has('repositoryName', repoName).as('repo')" +
                ".addV('resource')" +
                ".property('resourceName', resName)" +
                ".as('res')" +
                ".addE('CONTAINS').from('repo').to('res')" +
                ".iterate(); g.tx().commit();";
        try {
            client.submit(query, Map.of(
                    "repoName", resourceDTO.getRepositoryName(),
                    "resName", resourceDTO.getResourceName())).all().get();
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            throw new ResourceException("Error saving resource: " + e.getMessage());
        }
    }

    @Override
    public boolean existsByRepository(ResourceDTO resourceDTO) {
        String query = "g.V().hasLabel('repository').has('repositoryName', repoName)" +
                ".out('CONTAINS').hasLabel('resource').has('resourceName', resName).count()";
        try {
            List<Result> results = client.submit(query, Map.of(
                    "repoName", resourceDTO.getRepositoryName(),
                    "resName", resourceDTO.getResourceName())).all().get();
            return !results.isEmpty() && results.get(0).getLong() > 0;
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            throw new ResourceException("Error checking resource existence");
        }
    }

    @Override
    public List<ResourceDTO> findResourcesByRepository(RepositoryDTO repositoryDTO) {
        String repositoryName = repositoryDTO.getRepositoryName();

        String query = "g.V().hasLabel('repository').has('repositoryName', repositoryName)" +
                ".out('CONTAINS').hasLabel('resource')" +
                ".valueMap('resourceName')";

        try {
            List<Result> results = client.submit(query, Map.of("repositoryName", repositoryName)).all().get();
            List<ResourceDTO> resources = new ArrayList<>();

            for (Result result : results) {
                @SuppressWarnings("unchecked")
                Map<String, List<Object>> props = (Map<String, List<Object>>) result.getObject();

                String resourceName = props.get("resourceName").get(0).toString();
                resources.add(new ResourceDTO(repositoryName, resourceName));
            }

            return resources;
        } catch (Exception e) {
            Thread.currentThread().interrupt();
            throw new ResourceException("Error finding resources by repository in JanusGraph");
        }
    }
}