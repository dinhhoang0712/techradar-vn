package com.techpulse.techradar.features.auth;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Auth module configuration.
 * Feature-based module with all layers auto-scanned.
 */
@Configuration
@ComponentScan(basePackages = "com.techpulse.techradar.features.auth")
public class AuthModuleConfig {
}
