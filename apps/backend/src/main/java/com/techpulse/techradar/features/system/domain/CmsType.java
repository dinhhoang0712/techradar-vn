package com.techpulse.techradar.features.system.domain;

/**
 * Fixed vocabulary for {@link CmsContent#getType()} — see {@link CmsStatus} for why the constant
 * names deliberately keep Title-Case instead of {@code UPPER_SNAKE_CASE}.
 */
public enum CmsType {
    Report,
    Job,
    Keyword
}
