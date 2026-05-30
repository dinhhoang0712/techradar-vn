package com.techpulse.techradar.features.system.application;

import com.techpulse.techradar.features.system.domain.AppSettings;
import com.techpulse.techradar.features.system.ports.SettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Admin settings service.
 */
@Component
@RequiredArgsConstructor
public class AdminService {

    private final SettingsRepository settingsRepository;

    public Mono<AppSettings> getSetting(String key) {
        return settingsRepository.getSetting(key);
    }

    public Flux<AppSettings> getAllSettings() {
        return settingsRepository.getAllSettings();
    }

    public Mono<AppSettings> updateSetting(String key, String value, String description) {
        AppSettings settings = AppSettings.builder()
                .key(key)
                .value(value)
                .description(description)
                .build();
        return settingsRepository.saveSetting(settings);
    }

    public Mono<Void> deleteSetting(String key) {
        return settingsRepository.deleteSetting(key);
    }
}
