package com.techpulse.techradar.features.compare.application;

import com.techpulse.techradar.features.compare.domain.TechComparison;
import com.techpulse.techradar.features.compare.ports.LlmSummaryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenerateLlmSummaryUseCaseTest {

    @Mock
    private LlmSummaryPort llmSummaryPort;

    private GenerateLlmSummaryUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GenerateLlmSummaryUseCase(llmSummaryPort);
    }

    @Test
    void execute_delegatesToLlmSummaryPort_andReturnsItsSummary() {
        TechComparison comparison = TechComparison.builder()
                .technology1("java")
                .technology2("python")
                .growthRate1(0.2)
                .growthRate2(0.3)
                .build();
        when(llmSummaryPort.generateSummary(comparison)).thenReturn(Mono.just("java is trending up"));

        StepVerifier.create(useCase.execute(comparison))
                .expectNext("java is trending up")
                .verifyComplete();

        verify(llmSummaryPort).generateSummary(comparison);
    }

    @Test
    void execute_propagatesErrorFromPort() {
        TechComparison comparison = TechComparison.builder().technology1("java").technology2("python").build();
        RuntimeException failure = new RuntimeException("ai-rag-core unreachable");
        when(llmSummaryPort.generateSummary(comparison)).thenReturn(Mono.error(failure));

        StepVerifier.create(useCase.execute(comparison))
                .expectErrorMatches(e -> e == failure)
                .verify();
    }
}
