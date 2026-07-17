package com.techpulse.techradar.features.system.domain;

/**
 * Aggregate real counts shown as decorative stat chips on the public login/register pages.
 * {@code companies} is capped at whatever {@code GetCompaniesUseCase}'s underlying query limits
 * to (currently 500) — a value equal to that cap should be displayed as "500+", not "500".
 */
public record PublicStats(long companies, long jobs, long users) {
}
