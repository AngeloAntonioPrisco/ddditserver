package it.unisa.ddditserver.validators.repo;

import it.unisa.ddditserver.db.gremlin.versioning.repo.GremlinRepositoryRepository;
import it.unisa.ddditserver.validators.ValidationResult;
import it.unisa.ddditserver.validators.versioning.repo.RepositoryValidationDTO;
import it.unisa.ddditserver.validators.versioning.repo.RepositoryValidatorImpl;
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
public class RepositoryValidatorImplBenchmark {

    private RepositoryValidatorImpl validator;
    private RepositoryValidationDTO dto;

    @Setup(Level.Trial)
    public void setup() {
        GremlinRepositoryRepository gremlinService = Mockito.mock(GremlinRepositoryRepository.class, Mockito.withSettings().stubOnly());
        validator = new RepositoryValidatorImpl(gremlinService);
        dto = new RepositoryValidationDTO("valid_repo.name123");

        when(gremlinService.existsByRepository(any())).thenReturn(true);
    }

    @Benchmark
    public boolean benchmarkIsValidRepositoryName() {
        return validator.isValidRepositoryName("valid_repo.name123");
    }

    @Benchmark
    public ValidationResult benchmarkValidateRepository() {
        return validator.validateRepository(dto);
    }

    @Benchmark
    public ValidationResult benchmarkValidateFull() {
        return validator.validate(dto);
    }
}