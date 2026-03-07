package it.unisa.ddditserver.validators.resource;

import it.unisa.ddditserver.db.gremlin.versioning.resource.GremlinResourceRepository;
import it.unisa.ddditserver.validators.ValidationResult;
import it.unisa.ddditserver.validators.versioning.repo.RepositoryValidator;
import it.unisa.ddditserver.validators.versioning.resource.ResourceValidationDTO;
import it.unisa.ddditserver.validators.versioning.resource.ResourceValidatorImpl;
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
public class ResourceValidatorImplBenchmark {

    private ResourceValidatorImpl validator;
    private ResourceValidationDTO dto;

    @Setup(Level.Trial)
    public void setup() {
        RepositoryValidator repoVal = Mockito.mock(RepositoryValidator.class, Mockito.withSettings().stubOnly());
        GremlinResourceRepository gremlinService = Mockito.mock(GremlinResourceRepository.class, Mockito.withSettings().stubOnly());

        validator = new ResourceValidatorImpl(repoVal, gremlinService);
        dto = new ResourceValidationDTO("repo_test", "resource_test_123");

        when(repoVal.validate(any())).thenReturn(ValidationResult.valid());
        when(gremlinService.existsByRepository(any())).thenReturn(true);
    }

    @Benchmark
    public boolean benchmarkIsValidResourceName() {
        return validator.isValidResourceName("resource_test_123");
    }

    @Benchmark
    public ValidationResult benchmarkValidateResource() {
        return validator.validateResource(dto);
    }

    @Benchmark
    public ValidationResult benchmarkValidateExistence() {
        return validator.validateExistence(dto, true);
    }
}