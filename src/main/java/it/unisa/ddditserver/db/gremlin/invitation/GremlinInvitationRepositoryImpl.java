package it.unisa.ddditserver.db.gremlin.invitation;

import it.unisa.ddditserver.db.gremlin.JanusConfig;
import it.unisa.ddditserver.subsystems.auth.dto.UserDTO;
import it.unisa.ddditserver.subsystems.invitation.dto.InvitationDTO;
import it.unisa.ddditserver.subsystems.invitation.exceptions.InvitationException;
import it.unisa.ddditserver.subsystems.versioning.dto.RepositoryDTO;
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
public class GremlinInvitationRepositoryImpl implements GremlinInvitationRepository {

    private static final String TO_USERNAME_KEY = "toUsername";

    private static final String REPOSITORY_NAME_KEY = "repositoryName";

    private static final String FROM_USERNAME_KEY = "fromUsername";

    private final JanusConfig config;
    private Cluster cluster;
    private Client client;

    @Autowired
    public GremlinInvitationRepositoryImpl(JanusConfig config) {
        this.config = config;
    }

    @PostConstruct
    public void init() {
        // Build cluster connection using GraphBinary
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
    public void saveInvitation(UserDTO fromUserDTO, UserDTO toUserDTO, RepositoryDTO repositoryDTO) {
        String query = "g.V().hasLabel('user').has('username', toUsername).as('target')" +
                ".V().hasLabel('user').has('username', fromUsername)" +
                ".addE('HAS_INVITED')" +
                ".property('repositoryName', repoName)" +
                ".property('status', 'pending')" +
                ".to('target')" +
                ".iterate(); g.tx().commit();";

        try {
            client.submit(query, Map.of(
                    FROM_USERNAME_KEY, fromUserDTO.getUsername(),
                    TO_USERNAME_KEY, toUserDTO.getUsername(),
                    "repoName", repositoryDTO.getRepositoryName())).all().get();
        } catch (Exception e) {
            throw new InvitationException("Error saving invitation: " + e.getMessage());
        }
    }

    @Override
    public boolean existsByUserAndRepository(UserDTO fromUserDTO, UserDTO toUserDTO, RepositoryDTO repositoryDTO) {
        String query = "g.V().hasLabel('user').has('username', fromUsername)" +
                ".outE('HAS_INVITED').has('repositoryName', repoName)" +
                ".where(inV().hasLabel('user').has('username', toUsername)).count()";
        try {
            List<Result> results = client.submit(query, Map.of(
                    FROM_USERNAME_KEY, fromUserDTO.getUsername(),
                    TO_USERNAME_KEY, toUserDTO.getUsername(),
                    "repoName", repositoryDTO.getRepositoryName())).all().get();
            return !results.isEmpty() && results.get(0).getLong() > 0;
        } catch (Exception e) {
            throw new InvitationException("Error checking invitation existence: " + e.getMessage());
        }
    }

    @Override
    public void acceptInvitation(UserDTO fromUserDTO, UserDTO toUserDTO, RepositoryDTO repositoryDTO) {
        String fromUsername = fromUserDTO.getUsername();
        String toUsername = toUserDTO.getUsername();
        String repositoryName = repositoryDTO.getRepositoryName();

        String query = "g.V().hasLabel('user').has('username', fromUsername)" +
                ".outE('HAS_INVITED').has('repositoryName', repositoryName)" +
                ".as('e')" +
                ".inV().hasLabel('user').has('username', toUsername)" +
                ".select('e')" +
                ".property('status', 'accepted').iterate(); g.tx().commit();";

        try {
            client.submit(query, Map.of(
                    FROM_USERNAME_KEY, fromUsername,
                    TO_USERNAME_KEY, toUsername,
                    REPOSITORY_NAME_KEY, repositoryName)).all().get();
        } catch (Exception e) {
            throw new InvitationException("Error updating invitation status in JanusGraph");
        }
    }

    @Override
    public boolean isAcceptedInvitation(UserDTO fromUserDTO, UserDTO toUserDTO, RepositoryDTO repositoryDTO) {
        String fromUsername = fromUserDTO.getUsername();
        String toUsername = toUserDTO.getUsername();
        String repositoryName = repositoryDTO.getRepositoryName();

        String query = "g.V().hasLabel('user').has('username', fromUsername)" +
                ".outE('HAS_INVITED').has('repositoryName', repositoryName).has('status', 'accepted')" +
                ".inV().hasLabel('user').has('username', toUsername).count()";

        try {
            List<Result> results = client.submit(query, Map.of(
                    FROM_USERNAME_KEY, fromUsername,
                    TO_USERNAME_KEY, toUsername,
                    REPOSITORY_NAME_KEY, repositoryName)).all().get();

            return !results.isEmpty() && results.get(0).getLong() > 0;
        } catch (Exception e) {
            throw new InvitationException("Error checking invitation status in JanusGraph");
        }
    }

    @Override
    public List<InvitationDTO> findInvitationsByUser(UserDTO userDTO) {
        String toUsername = userDTO.getUsername();

        String query = "g.V().hasLabel('user').has('username', toUsername)" +
                ".inE('HAS_INVITED').has('status', 'pending').as('e')" +
                ".outV().as('from')" +
                ".project('fromUsername', 'repositoryName')" +
                "  .by(__.values('username'))" +
                "  .by(__.select('e').values('repositoryName'))";

        try {
            List<Result> results = client.submit(query, Map.of(TO_USERNAME_KEY, toUsername)).all().get();
            List<InvitationDTO> invitations = new ArrayList<>();

            for (Result result : results) {
                Object resultObj = result.getObject();
                if (resultObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) resultObj;

                    String fromUsername = extractValue(map.get(FROM_USERNAME_KEY));
                    String repositoryName = extractValue(map.get(REPOSITORY_NAME_KEY));

                    if (fromUsername != null && repositoryName != null) {
                        invitations.add(new InvitationDTO(fromUsername, repositoryName));
                    }
                }
            }

            return invitations;
        } catch (Exception e) {
            e.printStackTrace(); 
            throw new InvitationException("Error finding pending invitations in JanusGraph: " + e.getMessage());
        }
    }

    private String extractValue(Object obj) {
        if (obj == null) return null;
        if (obj instanceof List<?> list) {
            return list.isEmpty() ? null : list.get(0).toString();
        }
        return obj.toString();
    }
}