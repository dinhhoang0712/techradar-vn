package com.techpulse.techradar.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables {@code @Scheduled} support (used by the optional analytics ETL job).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
