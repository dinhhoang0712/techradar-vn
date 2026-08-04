package com.techpulse.techradar.shared.db;

import org.springframework.r2dbc.core.DatabaseClient;

/**
 * Null-safe R2DBC bind: {@code .bind(name, null)} throws, so every nullable column needs
 * {@code .bindNull(name, Class)} instead — this exact ternary used to be reimplemented as a
 * private static helper (or inlined per-field) in ~11 different {@code Postgres*Repository}
 * classes. One shared helper now, so a future repository doesn't add copy #12.
 */
public final class R2dbcBinders {

    private R2dbcBinders() {
    }

    /** String is by far the most common nullable column type — avoids passing {@code String.class} everywhere. */
    public static DatabaseClient.GenericExecuteSpec bindNullable(
            DatabaseClient.GenericExecuteSpec spec, String name, String value) {
        return bindNullable(spec, name, value, String.class);
    }

    public static <T> DatabaseClient.GenericExecuteSpec bindNullable(
            DatabaseClient.GenericExecuteSpec spec, String name, T value, Class<T> type) {
        return value != null ? spec.bind(name, value) : spec.bindNull(name, type);
    }
}
