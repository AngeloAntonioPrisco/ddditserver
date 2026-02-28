package it.unisa.ddditserver.db.gremlin.auth;

import it.unisa.ddditserver.db.gremlin.JanusConfig;
import jakarta.annotation.PreDestroy;
import org.apache.tinkerpop.gremlin.driver.Client;
import org.apache.tinkerpop.gremlin.driver.Cluster;
import org.apache.tinkerpop.gremlin.driver.Result;
import it.unisa.ddditserver.subsystems.auth.dto.UserDTO;
import it.unisa.ddditserver.subsystems.auth.exceptions.AuthException;
import jakarta.annotation.PostConstruct;
import org.apache.tinkerpop.gremlin.util.ser.GraphBinaryMessageSerializerV1;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;

@Repository
public class GremlinAuthRepositoryImpl implements GremlinAuthRepository {

    private final JanusConfig config;
    private Client client;

    @Autowired
    public GremlinAuthRepositoryImpl(JanusConfig config) {
        this.config = config;
    }

    @PostConstruct
    public void init() {
        // Build cluster connection to JanusGraph server
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
    public void saveUser(UserDTO user) {
        String username = user.getUsername();
        String password = user.getPassword();

        String query = "g.addV('user')" +
                ".property('repoId', repoId)" +
                ".property('username', username)" +
                ".property('password', password)" +
                ".iterate(); g.tx().commit();";

        try {
            client.submit(query, Map.of(
                    "repoId", "unassignedRepoId",
                    "username", username,
                    "password", password
            )).all().get();

        } catch (Exception e) {
            throw new AuthException("Error saving user in JanusGraph: " + e.getMessage());
        }
    }

    @Override
    public UserDTO findByUser(UserDTO userDTO) {
        String username = userDTO.getUsername();
        String query = "g.V().hasLabel('user').has('username', username).valueMap('username', 'password')";

        try {
            List<Result> results = client.submit(query, Map.of("username", username)).all().get();

            if (results.isEmpty()) {
                throw new AuthException("User not found in JanusGraph");
            }

            @SuppressWarnings("unchecked")
            Map<Object, List<Object>> props = results.get(0).get(Map.class);

            String fetchedUsername = props.get("username").get(0).toString();
            String password = props.get("password").get(0).toString();

            return new UserDTO(fetchedUsername, password);
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthException("Error retrieving user from JanusGraph: " + e.getMessage());
        }
    }

    @Override
    public boolean existsByUser(UserDTO userDTO) {
        String username = userDTO.getUsername();

        String query = "g.V().hasLabel('user').has('username', username).count()";

        try {
            List<Result> results = client.submit(query, Map.of("username", username)).all().get();

            if (results.isEmpty()) {
                return false;
            }

            long count = results.get(0).getLong();
            return count > 0;
        } catch (Exception e) {
            throw new AuthException("Error checking user existence in JanusGraph: " + e.getMessage());
        }
    }
}