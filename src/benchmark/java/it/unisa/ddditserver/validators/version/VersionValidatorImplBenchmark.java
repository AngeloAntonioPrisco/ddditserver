package it.unisa.ddditserver.validators.version;

import it.unisa.ddditserver.db.gremlin.versioning.version.GremlinVersionRepository;
import it.unisa.ddditserver.validators.ValidationResult;
import it.unisa.ddditserver.validators.versioning.branch.BranchValidator;
import it.unisa.ddditserver.validators.versioning.repo.RepositoryValidator;
import it.unisa.ddditserver.validators.versioning.resource.ResourceValidator;
import it.unisa.ddditserver.validators.versioning.version.VersionValidationDTO;
import it.unisa.ddditserver.validators.versioning.version.VersionValidatorImpl;
import org.mockito.Mockito;
import org.openjdk.jmh.annotations.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.concurrent.TimeUnit;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@State(Scope.Benchmark)
@Fork(2)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class VersionValidatorImplBenchmark {

    private VersionValidatorImpl validator;
    private VersionValidationDTO validDTO;
    private MultipartFile mockMesh;

    @Setup(Level.Trial)
    public void setup() {
        GremlinVersionRepository gremlinRepo = Mockito.mock(GremlinVersionRepository.class, Mockito.withSettings().stubOnly());
        BranchValidator branchVal = Mockito.mock(BranchValidator.class, Mockito.withSettings().stubOnly());
        ResourceValidator resVal = Mockito.mock(ResourceValidator.class, Mockito.withSettings().stubOnly());
        RepositoryValidator repoVal = Mockito.mock(RepositoryValidator.class, Mockito.withSettings().stubOnly());

        validator = new VersionValidatorImpl(gremlinRepo, branchVal, resVal, repoVal);

        when(repoVal.validate(any())).thenReturn(ValidationResult.valid());
        when(resVal.validate(any())).thenReturn(ValidationResult.valid());
        when(branchVal.validate(any())).thenReturn(ValidationResult.valid());
        when(gremlinRepo.existsByVersion(any())).thenReturn(false);

        mockMesh = Mockito.mock(MultipartFile.class);
        when(mockMesh.getOriginalFilename()).thenReturn("model_test.fbx");
        when(mockMesh.getSize()).thenReturn(1024L);
        when(mockMesh.isEmpty()).thenReturn(false);

        validDTO = new VersionValidationDTO(
                "repoTest", "resTest", "main", "v1_0_0",
                "Valid comment with symbols !?", mockMesh, null
        );
    }

    @Benchmark
    public boolean benchmarkIsValidComment() {
        return validator.isValidComment("Commento di test standard 123 !?");
    }

    @Benchmark
    public boolean benchmarkIsValidVersionName() {
        return validator.isValidVersionName("version_name_test_123");
    }

    @Benchmark
    public Object benchmarkValidateVersionMesh() {
        return validator.validateVersion(validDTO, true);
    }

    @Benchmark
    public Object benchmarkValidateExistence() {
        return validator.validateExistence(validDTO, false);
    }
}