package it.unisa.ddditserver.subsystems.service.version;

import it.unisa.ddditserver.db.gremlin.versioning.repo.GremlinRepositoryRepository;
import it.unisa.ddditserver.db.gremlin.versioning.version.GremlinVersionRepository;
import it.unisa.ddditserver.subsystems.ai.service.TagClassificationService;
import it.unisa.ddditserver.subsystems.versioning.dto.version.VersionDTO;
import it.unisa.ddditserver.subsystems.versioning.service.version.VersionServiceImpl;
import it.unisa.ddditserver.validators.ValidationResult;
import it.unisa.ddditserver.validators.auth.JWT.JWTokenValidator;
import it.unisa.ddditserver.validators.auth.user.UserValidator;
import it.unisa.ddditserver.validators.versioning.version.VersionValidator;
import org.mockito.Mockito;
import org.openjdk.jmh.annotations.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@State(Scope.Benchmark)
@Fork(2)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class VersionServiceImplBenchmark {

    private VersionServiceImpl service;
    private VersionDTO versionDTO;
    private String token = "valid.token.test";

    @Setup(Level.Trial)
    public void setup() {
        GremlinRepositoryRepository repoRepo = Mockito.mock(GremlinRepositoryRepository.class, Mockito.withSettings().stubOnly());
        GremlinVersionRepository verRepo = Mockito.mock(GremlinVersionRepository.class, Mockito.withSettings().stubOnly());
        JWTokenValidator jwtVal = Mockito.mock(JWTokenValidator.class, Mockito.withSettings().stubOnly());
        TagClassificationService tagService = Mockito.mock(TagClassificationService.class, Mockito.withSettings().stubOnly());
        UserValidator userVal = Mockito.mock(UserValidator.class, Mockito.withSettings().stubOnly());
        VersionValidator verVal = Mockito.mock(VersionValidator.class, Mockito.withSettings().stubOnly());

        service = new VersionServiceImpl(repoRepo, verRepo, jwtVal, tagService, userVal, verVal);

        MultipartFile mesh = Mockito.mock(MultipartFile.class);
        when(mesh.getOriginalFilename()).thenReturn("cube.fbx");

        versionDTO = new VersionDTO();
        versionDTO.setRepositoryName("repo");
        versionDTO.setResourceName("resource");
        versionDTO.setBranchName("main");
        versionDTO.setVersionName("v1");
        versionDTO.setMesh(mesh);

        when(jwtVal.isTokenValid(anyString())).thenReturn("testUser");
        when(repoRepo.isOwner(any(), any())).thenReturn(true);
        when(userVal.validateExistence(any(), anyBoolean())).thenReturn(ValidationResult.valid());
        when(verVal.validateVersion(any(), anyBoolean())).thenReturn(ValidationResult.valid());
        when(verVal.validateExistence(any(), anyBoolean())).thenReturn(ValidationResult.valid());
        when(tagService.classify(any())).thenReturn(Collections.singletonList("test-tag"));

        when(verRepo.getFile(any())).thenReturn(Collections.emptyList());
        when(verRepo.findVersionByBranch(any(VersionDTO.class))).thenReturn(versionDTO);
        when(verRepo.existsByVersion(any(VersionDTO.class))).thenReturn(true);

        versionDTO.setPushedAt(java.time.LocalDateTime.now());
        versionDTO.setTags(Collections.singletonList("test-tag"));
    }

    @Benchmark
    public Object benchmarkCreateVersion() {
        return service.createVersion(versionDTO, token);
    }

    @Benchmark
    public Object benchmarkShowVersionMetadata() {
        return service.showVersionMetadata(versionDTO, token);
    }

    @Benchmark
    public Object benchmarkPullVersion() {
        return service.pullVersion(versionDTO, token);
    }
}