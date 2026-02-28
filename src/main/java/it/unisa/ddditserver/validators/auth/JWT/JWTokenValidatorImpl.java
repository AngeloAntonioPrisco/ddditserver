package it.unisa.ddditserver.validators.auth.JWT;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import it.unisa.ddditserver.db.cosmos.auth.CosmosAuthRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.util.Base64;

/**
 * Component responsible for validating JWT token.
 *
 * This validator verifies that the token sent by the user is valid.
 *
 * @author Angelo Antonio Prisco
 * @version 1.0
 * @since 2025-08-13
 */
@Component
public class JWTokenValidatorImpl implements JWTokenValidator {
    private CosmosAuthRepository cosmosAuthService;

    public JWTokenValidatorImpl(CosmosAuthRepository cosmosAuthService) {
        this.cosmosAuthService = cosmosAuthService;
    }

    @Value("${JWT_SECRET}")
    public String jwtSecretBase64;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        byte[] decodedKey = Base64.getDecoder().decode(jwtSecretBase64);
        this.secretKey = Keys.hmacShaKeyFor(decodedKey);
    }

    @Override
    public String isTokenValid(String token) {
        if (token != null && cosmosAuthService.isTokenBlacklisted(token)) {
            return null;
        }

        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            String username = claims.getSubject();

            if (username == null || username.isBlank()) {
                throw new JwtException("Username can't be null or empty in JWT token");
            }

            return username;
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
