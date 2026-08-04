package com.techpulse.techradar.shared.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.r2dbc.core.DatabaseClient;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class R2dbcBindersTest {

    @Mock
    private DatabaseClient.GenericExecuteSpec spec;

    @Test
    void bindNullable_string_bindsValue_whenPresent() {
        when(spec.bind("name", "value")).thenReturn(spec);

        DatabaseClient.GenericExecuteSpec result = R2dbcBinders.bindNullable(spec, "name", "value");

        verify(spec).bind("name", "value");
        verifyNoMoreInteractions(spec);
        assertThat(result).isSameAs(spec);
    }

    @Test
    void bindNullable_string_bindsNull_whenAbsent() {
        when(spec.bindNull("name", String.class)).thenReturn(spec);

        R2dbcBinders.bindNullable(spec, "name", null);

        verify(spec).bindNull("name", String.class);
    }

    @Test
    void bindNullable_typed_bindsValue_whenPresent() {
        LocalDate date = LocalDate.of(2026, 1, 1);
        when(spec.bind("date", date)).thenReturn(spec);

        R2dbcBinders.bindNullable(spec, "date", date, LocalDate.class);

        verify(spec).bind("date", date);
    }

    @Test
    void bindNullable_typed_bindsNull_whenAbsent() {
        when(spec.bindNull("date", LocalDate.class)).thenReturn(spec);

        R2dbcBinders.bindNullable(spec, "date", null, LocalDate.class);

        verify(spec).bindNull("date", LocalDate.class);
    }
}
