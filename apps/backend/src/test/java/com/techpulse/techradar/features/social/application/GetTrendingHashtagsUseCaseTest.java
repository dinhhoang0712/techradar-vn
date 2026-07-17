package com.techpulse.techradar.features.social.application;

import com.techpulse.techradar.features.social.ports.HashtagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetTrendingHashtagsUseCaseTest {

    @Mock
    private HashtagRepository hashtagRepository;

    private GetTrendingHashtagsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetTrendingHashtagsUseCase(hashtagRepository);
        when(hashtagRepository.trending(any(), anyInt())).thenReturn(Flux.empty());
    }

    @Test
    void execute_defaultsLimitToTenWhenNonPositive() {
        useCase.execute(0).blockLast();
        verify(hashtagRepository).trending(any(), eq(10));
    }

    @Test
    void execute_clampsLimitToFifty() {
        useCase.execute(500).blockLast();
        verify(hashtagRepository).trending(any(), eq(50));
    }

    @Test
    void execute_passesRegularLimitUnchanged() {
        useCase.execute(5).blockLast();
        verify(hashtagRepository).trending(any(), eq(5));
    }

    @Test
    void execute_usesASevenDayLookbackWindow() {
        LocalDateTime before = LocalDateTime.now().minusDays(7);
        useCase.execute(10).blockLast();
        LocalDateTime after = LocalDateTime.now().minusDays(7);

        ArgumentCaptor<LocalDateTime> sinceCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(hashtagRepository).trending(sinceCaptor.capture(), eq(10));
        assertThat(sinceCaptor.getValue()).isBetween(before, after);
    }
}
