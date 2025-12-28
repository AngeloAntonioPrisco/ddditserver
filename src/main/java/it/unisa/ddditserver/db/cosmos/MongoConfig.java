package it.unisa.ddditserver.db.cosmos;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Component
public class MongoConfig {

    @Value("${MONGO_HOST}")
    private String host;

    @Value("${MONGO_PORT}")
    private int port;

    @Value("${MONGODB_ROOT_USERNAME}")
    private String username;

    @Value("${MONGODB_ROOT_PASSWORD}")
    private String password;

    @Value("${MONGODB_NAME}")
    private String databaseName;

    @Value("${MONGODB_COLLECTION_VERSIONS}")
    private String versionsCollection;

    @Value("${MONGODB_COLLECTION_TOKEN_BLACKLIST}")
    private String tokenBlacklistCollection;

    public String getConnectionString() {
        return "mongodb://" + username + ":" + password + "@" + host + ":" + port;
    }
}