package it.unisa.ddditserver.db.unit.cosmos.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import it.unisa.ddditserver.db.cosmos.auth.CosmosAuthRepositoryImpl;
import it.unisa.ddditserver.subsystems.auth.dto.BlacklistedTokenDTO;
import it.unisa.ddditserver.subsystems.auth.exceptions.AuthException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Date;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CosmosAuthRepositoryImplTest {

    private static final int TTL_TOLERANCE_SECONDS = 2;

    @Mock
    private MongoTemplate mongoTemplate;

    private CosmosAuthRepositoryImpl repository;

    private static final Algorithm ALG = Algorithm.HMAC256("test-secret"); // solo per costruire token validi

    @BeforeEach
    void setUp() {
        repository = new CosmosAuthRepositoryImpl(mongoTemplate);
    }

    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------

    private static String tokenWithExp(Date exp) {
        return JWT.create()
                .withSubject("user")
                .withIssuedAt(new Date())
                .withExpiresAt(exp)
                .sign(ALG);
    }

    private static String tokenWithoutExp() {
        return JWT.create()
                .withSubject("user")
                .withIssuedAt(new Date())
                // no expiresAt
                .sign(ALG);
    }

    private static String invalidToken() {
        return "not-a-jwt";
    }

    private static int expectedTtlSeconds(Date exp) {
        long tokenExpiryTimestamp = exp.getTime() / 1000;
        long currentTimestamp = System.currentTimeMillis() / 1000;
        return (int) (tokenExpiryTimestamp - currentTimestamp);
    }

    // ------------------------------------------------------------
    // Category Partition: blacklistToken
    //
    // Categories:
    // - token: valid | invalid
    // - exp: missing | past | future
    // - mongo save: ok | throws
    // ------------------------------------------------------------

    static Stream<Arguments> blacklistTokenCases() {
        return Stream.of(
                // tokenKind, expOffsetMillis, mongoThrows, expectedSaveCalls, expectException
                Arguments.of("INVALID",     null,      false, 0, true),

                Arguments.of("VALID_NOEXP", null,      false, 0, true),
                Arguments.of("VALID",       -120_000L, false, 0, false), // expired
                Arguments.of("VALID",       +300_000L, false, 1, false), // future
                Arguments.of("VALID",       +300_000L, true,  1, true)   // future + mongo down => save attempted once
        );
    }

    @ParameterizedTest
    @MethodSource("blacklistTokenCases")
    void blacklistToken_categoryPartition(String tokenKind,
                                          Long expOffsetMillis,
                                          boolean mongoThrows,
                                          int expectedSaveCalls,
                                          boolean expectException) {

        String token;
        Date exp = null;

        if ("INVALID".equals(tokenKind)) {
            token = invalidToken();
        } else if ("VALID_NOEXP".equals(tokenKind)) {
            token = tokenWithoutExp();
        } else {
            exp = new Date(System.currentTimeMillis() + expOffsetMillis);
            token = tokenWithExp(exp);
        }

        if (mongoThrows) {
            doThrow(new RuntimeException("mongo down"))
                    .when(mongoTemplate)
                    .save(any(BlacklistedTokenDTO.class));
        }

        if (expectException) {
            assertThrows(AuthException.class, () -> repository.blacklistToken(token));
        } else {
            assertDoesNotThrow(() -> repository.blacklistToken(token));
        }

        verify(mongoTemplate, times(expectedSaveCalls)).save(any(BlacklistedTokenDTO.class));

        // Asserzioni forti solo se ha salvato
        if (expectedSaveCalls == 1) {
            ArgumentCaptor<BlacklistedTokenDTO> captor = ArgumentCaptor.forClass(BlacklistedTokenDTO.class);
            verify(mongoTemplate).save(captor.capture());

            BlacklistedTokenDTO saved = captor.getValue();
            assertEquals(token, saved.getId());
            assertEquals(token, saved.getTokenId());
            assertNotNull(saved.getTtl());
            assertTrue(saved.getTtl() > 0);

            int expectedTtl = expectedTtlSeconds(exp);
            assertTrue(Math.abs(saved.getTtl() - expectedTtl) <= TTL_TOLERANCE_SECONDS);
        }
    }

    // ------------------------------------------------------------
    // Category Partition: isTokenBlacklisted
    //
    // Categories:
    // - mongo find: found | not found | throws
    // ------------------------------------------------------------

    static Stream<Arguments> isBlacklistedCases() {
        return Stream.of(
                Arguments.of("FOUND",    true,  false),
                Arguments.of("NOTFOUND", false, false),
                Arguments.of("THROWS",   false, true)
        );
    }

    @ParameterizedTest
    @MethodSource("isBlacklistedCases")
    void isTokenBlacklisted_categoryPartition(String findBehavior,
                                              boolean expected,
                                              boolean expectException) {

        String token = tokenWithExp(new Date(System.currentTimeMillis() + 120_000));

        if ("FOUND".equals(findBehavior)) {
            when(mongoTemplate.findById(token, BlacklistedTokenDTO.class))
                    .thenReturn(mock(BlacklistedTokenDTO.class));
        } else if ("NOTFOUND".equals(findBehavior)) {
            when(mongoTemplate.findById(token, BlacklistedTokenDTO.class))
                    .thenReturn(null);
        } else { // THROWS
            when(mongoTemplate.findById(token, BlacklistedTokenDTO.class))
                    .thenThrow(new RuntimeException("mongo down"));
        }

        if (expectException) {
            assertThrows(AuthException.class, () -> repository.isTokenBlacklisted(token));
        } else {
            assertEquals(expected, repository.isTokenBlacklisted(token));
        }
    }
}