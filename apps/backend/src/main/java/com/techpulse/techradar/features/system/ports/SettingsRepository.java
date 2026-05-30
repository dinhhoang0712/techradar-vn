package com.techpulse.techradar.features.system.ports;

import com.techpulse.techradar.features.system.domain.AppSettings;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Output port for admin settings repository.
 */
public interface SettingsRepository {
    Mono<AppSettings> getSetting(String key);

    Flux<AppSettings> getAllSettings();

    Mono<AppSettings> saveSetting(AppSettings settings);

    Mono<Void> deleteSetting(String key);
}
