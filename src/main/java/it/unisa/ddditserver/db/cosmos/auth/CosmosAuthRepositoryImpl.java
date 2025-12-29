package it.unisa.ddditserver.db.cosmos.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import it.unisa.ddditserver.subsystems.auth.dto.BlacklistedTokenDTO;
import it.unisa.ddditserver.subsystems.auth.exceptions.AuthException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CosmosAuthRepositoryImpl implements CosmosAuthRepository {

    private final MongoTemplate mongoTemplate;

    @Autowired
    public CosmosAuthRepositoryImpl(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void blacklistToken(String token) {
        try {
            DecodedJWT decodedJWT = JWT.decode(token);

            if (decodedJWT.getExpiresAt() == null) {
                throw new AuthException("Il token non ha una data di scadenza valida");
            }

            long tokenExpiryTimestamp = decodedJWT.getExpiresAt().getTime() / 1000;
            long currentTimestamp = System.currentTimeMillis() / 1000;

            int remainingTtl = (int) (tokenExpiryTimestamp - currentTimestamp);

            if (remainingTtl <= 0) {
                return;
            }

            BlacklistedTokenDTO blacklistedToken = new BlacklistedTokenDTO(token, token, remainingTtl);

            mongoTemplate.save(blacklistedToken);

        } catch (Exception e) {
            throw new AuthException("Errore durante il blacklisting del token su MongoDB: " + e.getMessage());
        }
    }

    @Override
    public boolean isTokenBlacklisted(String token) {
        try {
            BlacklistedTokenDTO found = mongoTemplate.findById(token, BlacklistedTokenDTO.class);

            return found != null;
        } catch (Exception e) {
            throw new AuthException("Errore durante la verifica del token nella blacklist: " + e.getMessage());
        }
    }
}