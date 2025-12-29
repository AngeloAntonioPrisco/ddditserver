package it.unisa.ddditserver.db.gremlin.versioning.repo;

import it.unisa.ddditserver.db.gremlin.JanusConfig;
import it.unisa.ddditserver.subsystems.auth.dto.UserDTO;
import it.unisa.ddditserver.subsystems.versioning.dto.RepositoryDTO;
import it.unisa.ddditserver.subsystems.versioning.exceptions.repo.RepositoryException;
import it.unisa.ddditserver.subsystems.versioning.exceptions.version.VersionException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.tinkerpop.gremlin.driver.Client;
import org.apache.tinkerpop.gremlin.driver.Cluster;
import org.apache.tinkerpop.gremlin.driver.Result;
import org.apache.tinkerpop.gremlin.util.ser.GraphBinaryMessageSerializerV1;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
public class GremlinRepositoryRepositoryImpl implements GremlinRepositoryRepository {

    private final JanusConfig config;
    private Cluster cluster;
    private Client client;

    @Autowired
    public GremlinRepositoryRepositoryImpl(JanusConfig config) {
        this.config = config;
    }

    @PostConstruct
    public void init() {
        this.cluster = Cluster.build()
                .addContactPoint(config.getHost())
                .port(config.getPort())
                .credentials(config.getUsername(), config.getPassword())
                .serializer(new GraphBinaryMessageSerializerV1(
                        org.apache.tinkerpop.gremlin.structure.io.binary.TypeSerializerRegistry.build()
                                .addRegistry(org.janusgraph.graphdb.tinkerpop.JanusGraphIoRegistry.instance())
                                .create()
                ))
                .create();

        this.client = cluster.connect();
    }

    @PreDestroy
    public void close() {
        if (client != null) client.close();
        if (cluster != null) cluster.close();
    }

    @Override
    public void saveRepository(RepositoryDTO repositoryDTO, UserDTO userDTO) {
        String query = "g.V().hasLabel('user').has('username', uName).as('u')" +
                ".addV('repository')" +
                ".property('repositoryName', rName)" +
                ".as('r')" +
                ".addE('OWNS').from('u').to('r')" +
                ".iterate(); g.tx().commit();";
        try {
            client.submit(query, Map.of(
                    "uName", userDTO.getUsername(),
                    "rName", repositoryDTO.getRepositoryName())).all().get();
        } catch (Exception e) {
            throw new RepositoryException("Error saving repository: " + e.getMessage());
        }
    }

    @Override
    public boolean existsByRepository(RepositoryDTO repositoryDTO) {
        String query = "g.V().hasLabel('repository').has('repositoryName', rName).count()";
        try {
            List<Result> results = client.submit(query, Map.of("rName", repositoryDTO.getRepositoryName())).all().get();
            return !results.isEmpty() && results.get(0).getLong() > 0;
        } catch (Exception e) {
            throw new RepositoryException("Error checking repository existence");
        }
    }

    @Override
    public List<UserDTO> findContributorsByRepository(RepositoryDTO repositoryDTO) {
        String repositoryName = repositoryDTO.getRepositoryName();

        String query = "g.V().hasLabel('repository').has('repositoryName', repositoryName)" +
                ".union(__.in('CONTRIBUTES_TO'), __.in('OWNS'))" +
                ".dedup().valueMap('username')";

        try {
            List<Result> results = client.submit(query, Map.of("repositoryName", repositoryName)).all().get();
            List<UserDTO> contributors = new ArrayList<>();

            for (Result result : results) {
                @SuppressWarnings("unchecked")
                Map<String, List<Object>> props = (Map<String, List<Object>>) result.getObject();
                String username = props.get("username").get(0).toString();
                contributors.add(new UserDTO(username, null));
            }
            return contributors;
        } catch (Exception e) {
            throw new RepositoryException("Error finding contributors in JanusGraph");
        }
    }

    @Override
    public boolean isContributor(RepositoryDTO repositoryDTO, UserDTO userDTO) {
        String username = userDTO.getUsername();
        String repositoryName = repositoryDTO.getRepositoryName();

        String query = "g.V().hasLabel('user').has('username', username)" +
                ".out('CONTRIBUTES_TO').hasLabel('repository').has('repositoryName', repositoryName).count()";

        try {
            List<Result> results = client.submit(query, Map.of("username", username, "repositoryName", repositoryName)).all().get();
            return !results.isEmpty() && results.get(0).getLong() > 0;
        } catch (Exception e) {
            throw new RepositoryException("Error checking contributor status in JanusGraph");
        }
    }

    @Override
    public boolean isOwner(RepositoryDTO repositoryDTO, UserDTO userDTO) {
        String username = userDTO.getUsername();
        String repositoryName = repositoryDTO.getRepositoryName();

        String query = "g.V().hasLabel('user').has('username', username)" +
                ".out('OWNS').hasLabel('repository').has('repositoryName', repositoryName).count()";

        try {
            List<Result> results = client.submit(query, Map.of("username", username, "repositoryName", repositoryName)).all().get();
            return !results.isEmpty() && results.get(0).getLong() > 0;
        } catch (Exception e) {
            throw new RepositoryException("Error checking owner status in JanusGraph");
        }
    }

    @Override
    public void addContributor(RepositoryDTO repositoryDTO, UserDTO userDTO) {
        String username = userDTO.getUsername();
        String repositoryName = repositoryDTO.getRepositoryName();

        String query = "g.V().hasLabel('user').has('username', username).as('u')" +
                ".V().hasLabel('repository').has('repositoryName', repositoryName).as('r')" +
                ".coalesce(" +
                "  __.inE('CONTRIBUTES_TO').where(__.outV().as('u'))," +
                "  __.addE('CONTRIBUTES_TO').from('u').to('r')" +
                ").iterate(); g.tx().commit();";

        try {
            client.submit(query, Map.of("username", username, "repositoryName", repositoryName)).all().get();
        } catch (Exception e) {
            throw new RepositoryException("Error adding contributor in JanusGraph");
        }
    }

    @Override
    public List<RepositoryDTO> findOwnedRepositoriesByUser(UserDTO userDTO) {
        String username = userDTO.getUsername();
        String query = "g.V().hasLabel('user').has('username', username).out('OWNS').valueMap('repositoryName')";

        try {
            List<Result> results = client.submit(query, Map.of("username", username)).all().get();
            List<RepositoryDTO> repositories = new ArrayList<>();
            for (Result result : results) {
                @SuppressWarnings("unchecked")
                Map<String, List<Object>> props = (Map<String, List<Object>>) result.getObject();
                repositories.add(new RepositoryDTO(props.get("repositoryName").get(0).toString()));
            }
            return repositories;
        } catch (Exception e) {
            throw new RepositoryException("Error finding owned repositories in JanusGraph");
        }
    }

    @Override
    public List<RepositoryDTO> findContributedRepositoriesByUser(UserDTO userDTO) {
        String username = userDTO.getUsername();
        String query = "g.V().hasLabel('user').has('username', username).out('CONTRIBUTES_TO').valueMap('repositoryName')";

        try {
            List<Result> results = client.submit(query, Map.of("username", username)).all().get();
            List<RepositoryDTO> repositories = new ArrayList<>();
            for (Result result : results) {
                @SuppressWarnings("unchecked")
                Map<String, List<Object>> props = (Map<String, List<Object>>) result.getObject();
                repositories.add(new RepositoryDTO(props.get("repositoryName").get(0).toString()));
            }
            return repositories;
        } catch (Exception e) {
            throw new RepositoryException("Error finding contributed repositories in JanusGraph");
        }
    }
}