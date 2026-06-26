package com.techpulse.techradar.features.system.application;

import com.techpulse.techradar.features.system.domain.AppSettings;
import com.techpulse.techradar.features.system.ports.SettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Admin settings service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminService {

    private final SettingsRepository settingsRepository;

    public Mono<AppSettings> getSetting(String key) {
        return settingsRepository.getSetting(key)
                .doOnSuccess(s -> log.info("Retrieved admin setting '{}'", key))
                .switchIfEmpty(Mono.<AppSettings>empty()
                        .doOnSubscribe(sub -> log.warn("Admin setting '{}' not found", key)))
                .doOnError(e -> log.error("Failed to retrieve admin setting '{}'", key, e));
    }

    public Flux<AppSettings> getAllSettings() {
        return settingsRepository.getAllSettings()
                .doOnComplete(() -> log.info("Retrieved all admin settings"))
                .doOnError(e -> log.error("Failed to retrieve admin settings", e));
    }

    public Mono<AppSettings> updateSetting(String key, String value, String description) {
        AppSettings settings = AppSettings.builder()
                .key(key)
                .value(value)
                .description(description)
                .build();
        return settingsRepository.saveSetting(settings)
                .doOnSubscribe(s -> log.info("Updating admin setting '{}'", key))
                .doOnSuccess(s -> log.info("Saved admin setting '{}'", key))
                .doOnError(e -> log.error("Failed to save admin setting '{}'", key, e));
    }

    public Mono<Void> deleteSetting(String key) {
        return settingsRepository.deleteSetting(key)
                .doOnSubscribe(s -> log.info("Deleting admin setting '{}'", key))
                .doOnSuccess(v -> log.info("Deleted admin setting '{}'", key))
                .doOnError(e -> log.error("Failed to delete admin setting '{}'", key, e));
    }
}
