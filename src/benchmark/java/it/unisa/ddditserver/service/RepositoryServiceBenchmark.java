package it.unisa.ddditserver.service;

import it.unisa.ddditserver.db.gremlin.versioning.repo.GremlinRepositoryRepository;
import it.unisa.ddditserver.subsystems.versioning.dto.RepositoryDTO;
import it.unisa.ddditserver.subsystems.versioning.service.repo.RepositoryServiceImpl;
import it.unisa.ddditserver.validators.ValidationResult;
import it.unisa.ddditserver.validators.auth.JWT.JWTokenValidator;
import it.unisa.ddditserver.validators.auth.user.UserValidator;
import it.unisa.ddditserver.validators.versioning.repo.RepositoryValidator;
import org.mockito.Mockito;
import org.openjdk.jmh.annotations.*;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@State(Scope.Benchmark)
@Fork(2)
@Warmup(iterations = 5)
@Measurement(iterations = 5)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
// To run this benchmark:
// 1. mvn clean package -DskipTests
// 2. java -cp target/benchmarks.jar org.openjdk.jmh.Main RepositoryServiceBenchmark
public class RepositoryServiceBenchmark {

    private RepositoryServiceImpl service;

    private GremlinRepositoryRepository gremlinService;
    private JWTokenValidator jwTokenValidator;
    private UserValidator userValidator;
    private RepositoryValidator repositoryValidator;

    private RepositoryDTO repositoryDTO;
    private String validToken;

    @Setup(Level.Trial)
    public void setup() {
        // Stub only is used to not let Mockito's log to saturate the memory
        gremlinService = Mockito.mock(GremlinRepositoryRepository.class, Mockito.withSettings().stubOnly());
        jwTokenValidator = Mockito.mock(JWTokenValidator.class, Mockito.withSettings().stubOnly());
        userValidator = Mockito.mock(UserValidator.class, Mockito.withSettings().stubOnly());
        repositoryValidator = Mockito.mock(RepositoryValidator.class, Mockito.withSettings().stubOnly());

        service = new RepositoryServiceImpl(
                gremlinService,
                jwTokenValidator,
                userValidator,
                repositoryValidator
        );

        repositoryDTO = new RepositoryDTO();
        repositoryDTO.setRepositoryName("testRepo");
        validToken = "valid-token";

        when(jwTokenValidator.isTokenValid(anyString())).thenReturn("testUser");

        when((userValidator.validateExistence(any(), anyBoolean()))).thenReturn(new ValidationResult(true));

        when(repositoryValidator.validateRepository(any())).thenReturn(new ValidationResult(true));

        when(repositoryValidator.validateExistence(any(), anyBoolean())).thenReturn(new ValidationResult(true));

        Mockito.doNothing().when(gremlinService).saveRepository(any(), any());

        when(gremlinService.findOwnedRepositoriesByUser(any())).thenReturn(Collections.emptyList());

        when(gremlinService.findContributedRepositoriesByUser(any())).thenReturn(Collections.emptyList());
    }

    @Benchmark
    public Object benchmarkCreateRepository() {
        return service.createRepository(repositoryDTO, validToken);
    }

    @Benchmark
    public Object benchmarkListRepositoriesContributed() {
        return service.listRepositoriesContributed(validToken);
    }

    @Benchmark
    public Object benchmarkListRepositoriesOwned() {
        return service.listRepositoriesOwned(validToken);
    }
}