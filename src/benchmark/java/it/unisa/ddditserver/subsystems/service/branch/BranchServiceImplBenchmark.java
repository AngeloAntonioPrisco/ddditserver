package it.unisa.ddditserver.subsystems.service.branch;

import it.unisa.ddditserver.db.gremlin.versioning.branch.GremlinBranchRepository;
import it.unisa.ddditserver.db.gremlin.versioning.repo.GremlinRepositoryRepository;
import it.unisa.ddditserver.subsystems.versioning.dto.BranchDTO;
import it.unisa.ddditserver.subsystems.versioning.dto.ResourceDTO;
import it.unisa.ddditserver.subsystems.versioning.service.branch.BranchServiceImpl;
import it.unisa.ddditserver.validators.ValidationResult;
import it.unisa.ddditserver.validators.auth.JWT.JWTokenValidator;
import it.unisa.ddditserver.validators.auth.user.UserValidator;
import it.unisa.ddditserver.validators.versioning.branch.BranchValidator;
import it.unisa.ddditserver.validators.versioning.resource.ResourceValidator;
import org.mockito.Mockito;
import org.openjdk.jmh.annotations.*;
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
// To run this benchmark:
// 1. mvn clean package -DskipTests
// 2. java -cp target/benchmarks.jar org.openjdk.jmh.Main BranchServiceImplBenchmark
public class BranchServiceImplBenchmark {

    private BranchServiceImpl service;
    private BranchDTO branchDTO;
    private ResourceDTO resourceDTO;
    private String token = "token";

    @Setup(Level.Trial)
    public void setup() {
        GremlinBranchRepository branchRepo = Mockito.mock(GremlinBranchRepository.class, Mockito.withSettings().stubOnly());
        GremlinRepositoryRepository repoRepo = Mockito.mock(GremlinRepositoryRepository.class, Mockito.withSettings().stubOnly());
        JWTokenValidator jwtVal = Mockito.mock(JWTokenValidator.class, Mockito.withSettings().stubOnly());
        UserValidator userVal = Mockito.mock(UserValidator.class, Mockito.withSettings().stubOnly());
        ResourceValidator resVal = Mockito.mock(ResourceValidator.class, Mockito.withSettings().stubOnly());
        BranchValidator branchVal = Mockito.mock(BranchValidator.class, Mockito.withSettings().stubOnly());

        service = new BranchServiceImpl(branchRepo, repoRepo, jwtVal, userVal, resVal, branchVal);

        branchDTO = new BranchDTO("repo", "res", "new_branch");
        resourceDTO = new ResourceDTO("repo", "res");

        when(jwtVal.isTokenValid(anyString())).thenReturn("user");
        when(repoRepo.isOwner(any(), any())).thenReturn(true);
        when(userVal.validateExistence(any(), anyBoolean())).thenReturn(ValidationResult.valid());
        when(branchVal.validateBranch(any())).thenReturn(ValidationResult.valid());
        when(branchVal.validateExistence(any(), anyBoolean())).thenReturn(ValidationResult.valid());
        when(resVal.validateResource(any())).thenReturn(ValidationResult.valid());
        when(resVal.validateExistence(any(), anyBoolean())).thenReturn(ValidationResult.valid());
        when(branchRepo.findBranchesByResource(any())).thenReturn(Collections.emptyList());
    }

    @Benchmark
    public Object benchmarkCreateBranch() {
        return service.createBranch(branchDTO, token);
    }

    @Benchmark
    public Object benchmarkListBranchesByResource() {
        return service.listBranchesByResource(resourceDTO, token);
    }
}