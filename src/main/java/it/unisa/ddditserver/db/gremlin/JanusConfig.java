package it.unisa.ddditserver.db.gremlin;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Component
public class JanusConfig {

    @Value("${JANUS_HOST}")
    private String host;

    @Value("${JANUS_PORT}")
    private int port;

    @Value("${JANUS_USERNAME}")
    private String username;

    @Value("${JANUS_PASSWORD}")
    private String password;

    public String getEndpoint() {
        return "ws://" + host + ":" + port + "/gremlin";
    }
}