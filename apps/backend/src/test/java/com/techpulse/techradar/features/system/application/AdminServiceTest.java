package com.techpulse.techradar.features.system.application;

import com.techpulse.techradar.features.system.domain.AppSettings;
import com.techpulse.techradar.features.system.ports.SettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private SettingsRepository settingsRepository;

    private AdminService service;

    @BeforeEach
    void setUp() {
        service = new AdminService(settingsRepository);
    }

    @Test
    void getSetting_returnsSettingFromRepository() {
        AppSettings settings = AppSettings.builder().key("feature_chat").value("true").build();
        when(settingsRepository.getSetting("feature_chat")).thenReturn(Mono.just(settings));

        StepVerifier.create(service.getSetting("feature_chat"))
                .expectNext(settings)
                .verifyComplete();
    }

    @Test
    void getSetting_completesEmpty_whenSettingNotFound() {
        when(settingsRepository.getSetting("missing")).thenReturn(Mono.empty());

        StepVerifier.create(service.getSetting("missing")).verifyComplete();
    }

    @Test
    void getAllSettings_returnsAllSettingsFromRepository() {
        when(settingsRepository.getAllSettings()).thenReturn(Flux.just(
                AppSettings.builder().key("a").value("1").build(),
                AppSettings.builder().key("b").value("2").build()));

        StepVerifier.create(service.getAllSettings()).expectNextCount(2).verifyComplete();
    }

    @Test
    void updateSetting_buildsSettingsAndSavesThroughRepository() {
        when(settingsRepository.saveSetting(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.updateSetting("maintenance_web", "true", "Maintenance flag"))
                .assertNext(saved -> {
                    assertThat(saved.getKey()).isEqualTo("maintenance_web");
                    assertThat(saved.getValue()).isEqualTo("true");
                    assertThat(saved.getDescription()).isEqualTo("Maintenance flag");
                })
                .verifyComplete();

        ArgumentCaptor<AppSettings> captor = ArgumentCaptor.forClass(AppSettings.class);
        verify(settingsRepository).saveSetting(captor.capture());
        assertThat(captor.getValue().getKey()).isEqualTo("maintenance_web");
    }

    @Test
    void deleteSetting_delegatesToRepository() {
        when(settingsRepository.deleteSetting("feature_chat")).thenReturn(Mono.empty());

        StepVerifier.create(service.deleteSetting("feature_chat")).verifyComplete();

        verify(settingsRepository).deleteSetting("feature_chat");
    }
}
