package it.unisa.ddditserver.validators.branch;

import it.unisa.ddditserver.db.gremlin.versioning.branch.GremlinBranchRepository;
import it.unisa.ddditserver.validators.ValidationResult;
import it.unisa.ddditserver.validators.versioning.branch.BranchValidationDTO;
import it.unisa.ddditserver.validators.versioning.branch.BranchValidatorImpl;
import it.unisa.ddditserver.validators.versioning.repo.RepositoryValidator;
import it.unisa.ddditserver.validators.versioning.resource.ResourceValidator;
import org.mockito.Mockito;
import org.openjdk.jmh.annotations.*;
import java.util.concurrent.TimeUnit;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@State(Scope.Benchmark)
@Fork(2)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class BranchValidatorImplBenchmark {

    private BranchValidatorImpl validator;
    private BranchValidationDTO dto;

    @Setup(Level.Trial)
    public void setup() {
        RepositoryValidator repoVal = Mockito.mock(RepositoryValidator.class, Mockito.withSettings().stubOnly());
        ResourceValidator resVal = Mockito.mock(ResourceValidator.class, Mockito.withSettings().stubOnly());
        GremlinBranchRepository gremlinService = Mockito.mock(GremlinBranchRepository.class, Mockito.withSettings().stubOnly());

        validator = new BranchValidatorImpl(repoVal, resVal, gremlinService);
        dto = new BranchValidationDTO("repo", "res", "main_branch");

        when(repoVal.validate(any())).thenReturn(ValidationResult.valid());
        when(resVal.validate(any())).thenReturn(ValidationResult.valid());
        when(gremlinService.existsByResource(any())).thenReturn(true);
    }

    @Benchmark
    public ValidationResult benchmarkValidateBranch() {
        return validator.validateBranch(dto);
    }

    @Benchmark
    public ValidationResult benchmarkValidateFullChain() {
        return validator.validate(dto);
    }
}