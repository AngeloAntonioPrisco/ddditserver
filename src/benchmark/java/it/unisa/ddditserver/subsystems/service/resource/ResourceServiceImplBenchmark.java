package it.unisa.ddditserver.subsystems.service.resource;

import it.unisa.ddditserver.db.gremlin.versioning.branch.GremlinBranchRepository;
import it.unisa.ddditserver.db.gremlin.versioning.repo.GremlinRepositoryRepository;
import it.unisa.ddditserver.db.gremlin.versioning.resource.GremlinResourceRepository;
import it.unisa.ddditserver.db.gremlin.versioning.version.GremlinVersionRepository;
import it.unisa.ddditserver.subsystems.versioning.dto.ResourceDTO;
import it.unisa.ddditserver.subsystems.versioning.service.resource.ResourceServiceImpl;
import it.unisa.ddditserver.validators.ValidationResult;
import it.unisa.ddditserver.validators.auth.JWT.JWTokenValidator;
import it.unisa.ddditserver.validators.auth.user.UserValidator;
import it.unisa.ddditserver.validators.versioning.repo.RepositoryValidator;
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
public class ResourceServiceImplBenchmark {

    private ResourceServiceImpl service;
    private ResourceDTO resourceDTO;
    private String token = "token";

    @Setup(Level.Trial)
    public void setup() {
        GremlinBranchRepository branchRepo = Mockito.mock(GremlinBranchRepository.class, Mockito.withSettings().stubOnly());
        GremlinRepositoryRepository repoRepo = Mockito.mock(GremlinRepositoryRepository.class, Mockito.withSettings().stubOnly());
        GremlinResourceRepository resRepo = Mockito.mock(GremlinResourceRepository.class, Mockito.withSettings().stubOnly());
        GremlinVersionRepository verRepo = Mockito.mock(GremlinVersionRepository.class, Mockito.withSettings().stubOnly());
        JWTokenValidator jwtVal = Mockito.mock(JWTokenValidator.class, Mockito.withSettings().stubOnly());
        RepositoryValidator repoVal = Mockito.mock(RepositoryValidator.class, Mockito.withSettings().stubOnly());
        ResourceValidator resVal = Mockito.mock(ResourceValidator.class, Mockito.withSettings().stubOnly());
        UserValidator userVal = Mockito.mock(UserValidator.class, Mockito.withSettings().stubOnly());

        service = new ResourceServiceImpl(branchRepo, repoRepo, resRepo, verRepo, jwtVal, repoVal, resVal, userVal);
        resourceDTO = new ResourceDTO("repo", "resource");

        when(jwtVal.isTokenValid(anyString())).thenReturn("testUser");
        when(repoRepo.isOwner(any(), any())).thenReturn(true);
        when(userVal.validateExistence(any(), anyBoolean())).thenReturn(ValidationResult.valid());
        when(resVal.validateResource(any())).thenReturn(ValidationResult.valid());
        when(resVal.validateExistence(any(), anyBoolean())).thenReturn(ValidationResult.valid());

        // Mock per showVersionTree
        when(branchRepo.findBranchesByResource(any())).thenReturn(Collections.emptyList());
    }

    @Benchmark
    public Object benchmarkCreateResource() {
        return service.createResource(resourceDTO, token);
    }

    @Benchmark
    public Object benchmarkShowVersionTree() {
        return service.showVersionTree(resourceDTO, token);
    }
}