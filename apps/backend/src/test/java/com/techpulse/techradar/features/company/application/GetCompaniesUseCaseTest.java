package com.techpulse.techradar.features.company.application;

import com.techpulse.techradar.features.company.domain.CompanyProfile;
import com.techpulse.techradar.features.company.ports.CompanyRepository;
import com.techpulse.techradar.shared.redis.ReactiveRedisCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetCompaniesUseCaseTest {

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private ReactiveRedisCache redisCache;

    private GetCompaniesUseCase useCase;

    private void stubCacheAsPassThrough() {
        when(redisCache.getOrLoad(anyString(), any(Duration.class), any(Flux.class), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        useCase = new GetCompaniesUseCase(companyRepository, redisCache);
    }

    private static CompanyRepository.CompanyRaw raw(int i) {
        return new CompanyRepository.CompanyRaw(
                "id-" + i, "Company " + i + "\nPro Company", "Hà Nội", List.of("Java"), i);
    }

    @Test
    void all_cleansNameAndDelegatesToRedisCacheUnderTheCompanyAllKey() {
        stubCacheAsPassThrough();
        when(companyRepository.findAllWithTechStack()).thenReturn(Flux.just(raw(1)));

        StepVerifier.create(useCase.all())
                .assertNext(profile -> assertThat(profile.name()).isEqualTo("Company 1"))
                .verifyComplete();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisCache).getOrLoad(keyCaptor.capture(), any(Duration.class), any(Flux.class), any());
        assertThat(keyCaptor.getValue()).isEqualTo("cache:company:all");
    }

    @Test
    void execute_skipsAndTakesAccordingToPageAndSize() {
        stubCacheAsPassThrough();
        List<CompanyRepository.CompanyRaw> raws = IntStream.range(0, 25).mapToObj(GetCompaniesUseCaseTest::raw).toList();
        when(companyRepository.findAllWithTechStack()).thenReturn(Flux.fromIterable(raws));

        StepVerifier.create(useCase.execute(1, 10).map(CompanyProfile::name))
                .expectNext("Company 10", "Company 11", "Company 12", "Company 13", "Company 14",
                        "Company 15", "Company 16", "Company 17", "Company 18", "Company 19")
                .verifyComplete();
    }

    @Test
    void execute_defaultsSizeTo20WhenNonPositive() {
        stubCacheAsPassThrough();
        List<CompanyRepository.CompanyRaw> raws = IntStream.range(0, 25).mapToObj(GetCompaniesUseCaseTest::raw).toList();
        when(companyRepository.findAllWithTechStack()).thenReturn(Flux.fromIterable(raws));

        StepVerifier.create(useCase.execute(0, 0))
                .expectNextCount(20)
                .verifyComplete();
    }

    @Test
    void execute_clampsSizeToMax100() {
        stubCacheAsPassThrough();
        List<CompanyRepository.CompanyRaw> raws = IntStream.range(0, 150).mapToObj(GetCompaniesUseCaseTest::raw).toList();
        when(companyRepository.findAllWithTechStack()).thenReturn(Flux.fromIterable(raws));

        StepVerifier.create(useCase.execute(0, 500))
                .expectNextCount(100)
                .verifyComplete();
    }
}
