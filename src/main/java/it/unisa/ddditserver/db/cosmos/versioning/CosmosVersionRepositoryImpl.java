package it.unisa.ddditserver.db.cosmos.versioning;

import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import it.unisa.ddditserver.db.cosmos.MongoConfig;
import it.unisa.ddditserver.subsystems.versioning.dto.version.VersionDTO;
import it.unisa.ddditserver.subsystems.versioning.exceptions.version.VersionException;
import jakarta.annotation.PostConstruct;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class CosmosVersionRepositoryImpl implements CosmosVersionRepository {

    private final MongoConfig config;
    private MongoClient mongoClient;
    private MongoCollection<Document> versionsCollection;

    @Autowired
    public CosmosVersionRepositoryImpl(MongoConfig config) {
        this.config = config;
    }

    @PostConstruct
    public void init() {
        mongoClient = MongoClients.create(config.getConnectionString());
        MongoDatabase database = mongoClient.getDatabase(config.getDatabaseName());
        versionsCollection = database.getCollection(config.getVersionsCollection());
    }

    @Override
    public String saveVersion(VersionDTO versionDTO, String blobUrl) {
        Document doc = new Document("_id", UUID.randomUUID().toString())
                .append("resourceName", versionDTO.getResourceName())
                .append("branchName", versionDTO.getBranchName())
                .append("versionName", versionDTO.getVersionName())
                .append("username", versionDTO.getUsername())
                .append("pushedAt", versionDTO.getPushedAt())
                .append("comment", versionDTO.getComment())
                .append("tags", versionDTO.getTags())
                .append("blobUrl", blobUrl);

        versionsCollection.insertOne(doc);

        return String.format(
                "mongodb://%s/%s/%s/%s",
                config.getHost(),
                config.getDatabaseName(),
                config.getVersionsCollection(),
                doc.getString("_id")
        );
    }

    @Override
    public VersionDTO findVersionByUrl(String url) {
        String id = url.substring(url.lastIndexOf("/") + 1);
        Document document = versionsCollection.find(Filters.eq("_id", id)).first();

        if (document == null) {
            throw new VersionException("Version document not found in MongoDB");
        }

        Object dateObj = document.get("pushedAt");
        LocalDateTime pushedAt;
        
        if (dateObj instanceof java.util.Date date) {
            pushedAt = date.toInstant()
                           .atZone(java.time.ZoneId.systemDefault())
                           .toLocalDateTime();
        } else if (dateObj instanceof String dateStr) {
            pushedAt = LocalDateTime.parse(dateStr);
        } else {
            pushedAt = LocalDateTime.now();
        }
        
        return new VersionDTO(
                null,
                document.getString("branchName"),
                null,
                document.getString("versionName"),
                document.getString("username"),
                pushedAt,
                document.getString("comment"),
                (List<String>) document.get("tags"),
                null,
                null
        );
    }

    @Override
    public String getBlobUrlByUrl(String versionUrl) {
        String id = versionUrl.substring(versionUrl.lastIndexOf("/") + 1);
        Document doc = versionsCollection.find(Filters.eq("_id", id)).first();

        if (doc == null) {
            throw new VersionException("Version document not found in MongoDB");
        }

        return doc.getString("blobUrl");
    }

    @Override
    public void deleteVersionByUrl(String versionUrl) {
        String id = versionUrl.substring(versionUrl.lastIndexOf("/") + 1);
        versionsCollection.deleteOne(Filters.eq("_id", id));
    }
}