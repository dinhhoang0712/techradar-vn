package com.techpulse.techradar.features.company.application;

import com.techpulse.techradar.features.company.domain.CompanyMention;
import com.techpulse.techradar.features.company.ports.CompanyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetCompanyMentionsUseCaseTest {

    @Mock
    private CompanyRepository companyRepository;

    private GetCompanyMentionsUseCase useCase;

    private static CompanyMention mention(String id) {
        return new CompanyMention(id, "Title " + id, "https://example.com/" + id, "2026-01-01", "VNExpress");
    }

    @Test
    void execute_delegatesToRepositoryWithDefaultLimitWhenNonPositive() {
        useCase = new GetCompanyMentionsUseCase(companyRepository);
        when(companyRepository.findMentions("id-1", 5)).thenReturn(Flux.just(mention("a-1")));

        StepVerifier.create(useCase.execute("id-1", 0))
                .expectNextCount(1)
                .verifyComplete();

        verify(companyRepository).findMentions("id-1", 5);
    }

    @Test
    void execute_clampsLimitToMax50() {
        useCase = new GetCompanyMentionsUseCase(companyRepository);
        when(companyRepository.findMentions("id-1", 50)).thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute("id-1", 500)).verifyComplete();

        verify(companyRepository).findMentions("id-1", 50);
    }

    @Test
    void execute_passesThroughAPositiveLimitWithinRange() {
        useCase = new GetCompanyMentionsUseCase(companyRepository);
        when(companyRepository.findMentions("id-1", 3)).thenReturn(Flux.just(mention("a-1"), mention("a-2")));

        StepVerifier.create(useCase.execute("id-1", 3))
                .expectNextCount(2)
                .verifyComplete();
    }
}
