package com.techpulse.techradar.features.system.domain;

/**
 * Fixed vocabulary for {@link CmsContent#getStatus()} — single source of truth backing
 * {@code @OneOf} on {@code AdminCmsController.CmsContentRequest}. Constant names deliberately
 * match the existing Title-Case string vocabulary used throughout the codebase (CmsService,
 * MonthlyReportSchedulerService, RadarAnalyticsEtlService, JobCompletionNotifier) rather than
 * conventional {@code UPPER_SNAKE_CASE} — this enum exists purely as a validation vocabulary
 * source (via {@code .name()}), not as a typed field anywhere, so matching the real string values
 * directly avoids a separate label-mapping layer for no benefit. See
 * docs/adr/0010-oneof-validation-for-fixed-vocabulary-strings.md.
 */
public enum CmsStatus {
    Published,
    Analyzed,
    Pending,
    Archived
}
