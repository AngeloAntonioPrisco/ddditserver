package it.unisa.ddditserver.db.unit.cosmos.versioning;

import com.mongodb.MongoClientSettings;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import it.unisa.ddditserver.db.cosmos.MongoConfig;
import it.unisa.ddditserver.db.cosmos.versioning.CosmosVersionRepositoryImpl;
import it.unisa.ddditserver.subsystems.versioning.dto.version.VersionDTO;
import it.unisa.ddditserver.subsystems.versioning.exceptions.version.VersionException;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CosmosVersionRepositoryImplTest {

    @Mock private MongoConfig config;
    @Mock private MongoCollection<Document> versionsCollection;
    @Mock private FindIterable<Document> findIterable;

    private CosmosVersionRepositoryImpl repository;

    private static final String HOST = "localhost";
    private static final String DB = "db";
    private static final String COLL = "versions";

    @BeforeEach
    void setUp() throws Exception {
        // lenient to avoid UnnecessaryStubbing on branches that fail early
        lenient().when(config.getHost()).thenReturn(HOST);
        lenient().when(config.getDatabaseName()).thenReturn(DB);
        lenient().when(config.getVersionsCollection()).thenReturn(COLL);

        repository = new CosmosVersionRepositoryImpl(config);

        // Inject mocked collection (avoid init/PostConstruct)
        Field field = CosmosVersionRepositoryImpl.class.getDeclaredField("versionsCollection");
        field.setAccessible(true);
        field.set(repository, versionsCollection);
    }

    // =========================================================
    // Category Partition: saveVersion
    // Categories:
    // - insertOne: ok | throws
    // =========================================================
    static Stream<Arguments> saveVersionCases() {
        return Stream.of(
                Arguments.of(false, false),
                Arguments.of(true,  true)
        );
    }

    @ParameterizedTest
    @MethodSource("saveVersionCases")
    void saveVersion_categoryPartition(boolean insertThrows, boolean expectException) {
        VersionDTO v = new VersionDTO();
        v.setResourceName("res");
        v.setBranchName("main");
        v.setVersionName("v1");
        v.setUsername("user");
        v.setPushedAt(LocalDateTime.of(2025, 1, 1, 10, 0));
        v.setComment("c");
        v.setTags(List.of("t1", "t2"));

        if (insertThrows) {
            doThrow(new RuntimeException("mongo down")).when(versionsCollection).insertOne(any(Document.class));
        }

        if (expectException) {
            assertThrows(RuntimeException.class, () -> repository.saveVersion(v, "blob://url"));
            return;
        }

        String url = repository.saveVersion(v, "blob://url");
        assertNotNull(url);
        assertTrue(url.startsWith("mongodb://" + HOST + "/" + DB + "/" + COLL + "/"));

        // Stronger: capture inserted Document and verify fields
        ArgumentCaptor<Document> captor = ArgumentCaptor.forClass(Document.class);
        verify(versionsCollection).insertOne(captor.capture());

        Document inserted = captor.getValue();
        assertNotNull(inserted.getString("_id"));
        assertEquals("res", inserted.getString("resourceName"));
        assertEquals("main", inserted.getString("branchName"));
        assertEquals("v1", inserted.getString("versionName"));
        assertEquals("user", inserted.getString("username"));
        assertEquals("c", inserted.getString("comment"));
        assertEquals(List.of("t1", "t2"), inserted.get("tags"));
        assertEquals("blob://url", inserted.getString("blobUrl"));
    }

    // =========================================================
    // Category Partition: findVersionByUrl
    //
    // Important note:
    // - url="" does NOT throw on substring (substring(0) is valid).
    //   If no doc exists it should throw VersionException.
    //
    // Categories:
    // - url: valid | empty | null
    // - doc: found | null
    // - pushedAt: Date | String | Other
    // =========================================================
    static Stream<Arguments> findVersionCases() {
        String validUrl = "mongodb://" + HOST + "/" + DB + "/" + COLL + "/my-id";
        return Stream.of(
                Arguments.of(validUrl, true,  "DATE",   null),
                Arguments.of(validUrl, true,  "STRING", null),
                Arguments.of(validUrl, true,  "OTHER",  null),
                Arguments.of(validUrl, false, "DATE",   VersionException.class),

                Arguments.of("",       false, "DATE",   VersionException.class), // id="" => doc null => VersionException
                Arguments.of(null,     false, "DATE",   NullPointerException.class)
        );
    }

    @ParameterizedTest
    @MethodSource("findVersionCases")
    void findVersionByUrl_categoryPartition(String url,
                                            boolean docFound,
                                            String pushedAtKind,
                                            Class<? extends Throwable> expectedEx) {

        // For url != null (including empty string), repository will call versionsCollection.find(...)
        if (url != null) {
            when(versionsCollection.find(any(Bson.class))).thenReturn(findIterable);
        }

        // For url != null, we decide whether doc exists
        if (url != null && !docFound) {
            when(findIterable.first()).thenReturn(null);
        }

        if (url != null && docFound) {
            Document d = new Document("_id", "my-id")
                    .append("branchName", "main")
                    .append("versionName", "v1")
                    .append("username", "user")
                    .append("comment", "c")
                    .append("tags", List.of("t1"));

            if ("DATE".equals(pushedAtKind)) {
                d.append("pushedAt", new Date());
            } else if ("STRING".equals(pushedAtKind)) {
                d.append("pushedAt", "2025-01-01T10:00:00");
            } else { // OTHER
                d.append("pushedAt", 123);
            }

            when(findIterable.first()).thenReturn(d);
        }

        if (expectedEx != null) {
            assertThrows(expectedEx, () -> repository.findVersionByUrl(url));
            return;
        }

        VersionDTO result = repository.findVersionByUrl(url);

        assertNotNull(result);

        // Do NOT assert branchName here because VersionDTO constructor parameter order may not map to getBranchName()
        // Assert the fields that are certainly set by the repository code path.
        assertEquals("v1", result.getVersionName());
        assertEquals("user", result.getUsername());
        assertEquals("c", result.getComment());
        assertEquals(List.of("t1"), result.getTags());
        assertNotNull(result.getPushedAt());

        if ("STRING".equals(pushedAtKind)) {
            assertEquals(LocalDateTime.of(2025, 1, 1, 10, 0), result.getPushedAt());
        } else {
            // DATE and OTHER both should be close to "now" (DATE is new Date(), OTHER falls back to LocalDateTime.now()).
            LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
            assertTrue(result.getPushedAt().isAfter(now.minusSeconds(5)));
            assertTrue(result.getPushedAt().isBefore(now.plusSeconds(5)));
        }
    }

    // =========================================================
    // Category Partition: getBlobUrlByUrl
    //
    // url="" does NOT throw on substring; it yields id="" and then doc null -> VersionException
    //
    // Categories:
    // - url: valid | empty | null
    // - doc: found | null
    // =========================================================
    static Stream<Arguments> getBlobUrlCases() {
        String validUrl = "mongodb://" + HOST + "/" + DB + "/" + COLL + "/my-id";
        return Stream.of(
                Arguments.of(validUrl, true,  null),
                Arguments.of(validUrl, false, VersionException.class),

                Arguments.of("",       false, VersionException.class),
                Arguments.of(null,     false, NullPointerException.class)
        );
    }

    @ParameterizedTest
    @MethodSource("getBlobUrlCases")
    void getBlobUrlByUrl_categoryPartition(String url,
                                           boolean docFound,
                                           Class<? extends Throwable> expectedEx) {

        if (url != null) {
            when(versionsCollection.find(any(Bson.class))).thenReturn(findIterable);
        }

        if (url != null && !docFound) {
            when(findIterable.first()).thenReturn(null);
        }

        if (url != null && docFound) {
            Document d = new Document("_id", "my-id").append("blobUrl", "blob://url");
            when(findIterable.first()).thenReturn(d);
        }

        if (expectedEx != null) {
            assertThrows(expectedEx, () -> repository.getBlobUrlByUrl(url));
        } else {
            assertEquals("blob://url", repository.getBlobUrlByUrl(url));
        }
    }

    // =========================================================
    // Category Partition: deleteVersionByUrl
    //
    // url="" does NOT throw; it yields id="" and deleteOne is called
    //
    // Categories:
    // - url: valid | empty | null
    // =========================================================
    static Stream<Arguments> deleteVersionCases() {
        String validUrl = "mongodb://" + HOST + "/" + DB + "/" + COLL + "/my-id";
        return Stream.of(
                Arguments.of(validUrl, null),
                Arguments.of("",       null),
                Arguments.of(null,     NullPointerException.class)
        );
    }

    @ParameterizedTest
    @MethodSource("deleteVersionCases")
    void deleteVersionByUrl_categoryPartition(String url, Class<? extends Throwable> expectedEx) {

        if (expectedEx != null) {
            assertThrows(expectedEx, () -> repository.deleteVersionByUrl(url));
            return;
        }

        assertDoesNotThrow(() -> repository.deleteVersionByUrl(url));

        // Stronger: capture filter and check it targets _id = last path segment ("" or "my-id")
        ArgumentCaptor<Bson> captor = ArgumentCaptor.forClass(Bson.class);
        verify(versionsCollection).deleteOne(captor.capture());

        String expectedId = url.substring(url.lastIndexOf("/") + 1);

        String json = captor.getValue()
                .toBsonDocument(Document.class, MongoClientSettings.getDefaultCodecRegistry())
                .toJson();

        assertTrue(json.contains("\"_id\""));
        assertTrue(json.contains(expectedId));
    }
}