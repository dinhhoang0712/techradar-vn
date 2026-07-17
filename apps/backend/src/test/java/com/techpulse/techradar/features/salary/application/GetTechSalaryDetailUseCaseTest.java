package com.techpulse.techradar.features.salary.application;

import com.techpulse.techradar.features.salary.ports.SalaryRepository;
import com.techpulse.techradar.shared.exception.NotFoundException;
import com.techpulse.techradar.shared.redis.ReactiveRedisCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetTechSalaryDetailUseCaseTest {

    @Mock
    private SalaryRepository salaryRepository;

    @Mock
    private ReactiveRedisCache redisCache;

    private GetTechSalaryDetailUseCase useCase;

    private void stubCacheAsPassThrough() {
        when(redisCache.getOrLoadMono(anyString(), any(Duration.class), any(Mono.class), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        useCase = new GetTechSalaryDetailUseCase(salaryRepository, redisCache);
    }

    private static Map.Entry<String, Integer> coTech(String name, int count) {
        return new AbstractMap.SimpleEntry<>(name, count);
    }

    @Test
    void execute_buildsCacheKeyFromLowercasedTechName() {
        stubCacheAsPassThrough();
        when(salaryRepository.findTechSalaryDetail("Java")).thenReturn(Mono.just(
                new SalaryRepository.TechSalaryDetailRaw("Java", 1, List.of("10 - 20 triệu"), List.of())));

        useCase.execute("Java").block();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisCache).getOrLoadMono(keyCaptor.capture(), any(Duration.class), any(Mono.class), any());
        assertThat(keyCaptor.getValue()).isEqualTo("cache:salary:tech:java");
    }

    @Test
    void execute_failsWithNotFoundWhenTechHasNoJobs() {
        stubCacheAsPassThrough();
        when(salaryRepository.findTechSalaryDetail("Cobol"))
                .thenReturn(Mono.just(new SalaryRepository.TechSalaryDetailRaw("Cobol", 0, List.of(), List.of())));

        StepVerifier.create(useCase.execute("Cobol"))
                .expectErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(NotFoundException.class);
                    assertThat(ex.getMessage()).isEqualTo("Technology not found: Cobol");
                })
                .verify();
    }

    @Test
    void execute_returnsZeroStatsWhenNoSalaryStringParses() {
        stubCacheAsPassThrough();
        when(salaryRepository.findTechSalaryDetail("Java")).thenReturn(Mono.just(
                new SalaryRepository.TechSalaryDetailRaw(
                        "Java", 5, List.of("Thỏa thuận"), List.of(coTech("Spring", 3)))));

        StepVerifier.create(useCase.execute("Java"))
                .assertNext(insight -> {
                    assertThat(insight.totalJobs()).isEqualTo(5);
                    assertThat(insight.jobsWithSalary()).isEqualTo(0);
                    assertThat(insight.medianVnd()).isEqualTo(0);
                    assertThat(insight.topCoTechs()).containsExactly("Spring");
                })
                .verifyComplete();
    }

    @Test
    void execute_computesStatsAndCapsCoTechsAtEight() {
        stubCacheAsPassThrough();
        List<Map.Entry<String, Integer>> tenCoTechs = IntStream.range(0, 10)
                .mapToObj(i -> coTech("Tech" + i, 10 - i))
                .toList();
        when(salaryRepository.findTechSalaryDetail("Java")).thenReturn(Mono.just(
                new SalaryRepository.TechSalaryDetailRaw(
                        "Java", 2, List.of("10 - 20 triệu", "15 - 25 triệu"), tenCoTechs)));

        StepVerifier.create(useCase.execute("Java"))
                .assertNext(insight -> {
                    assertThat(insight.jobsWithSalary()).isEqualTo(2);
                    assertThat(insight.medianVnd()).isEqualTo(17.5);
                    assertThat(insight.topCoTechs()).hasSize(8)
                            .containsExactly("Tech0", "Tech1", "Tech2", "Tech3", "Tech4", "Tech5", "Tech6", "Tech7");
                })
                .verifyComplete();
    }
}
