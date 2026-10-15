package ru.bell.autoleasing.agent.tool;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolArgsTest {

    @Test
    void parsesDatesAndNumbersInDifferentShapes() {
        assertThat(ToolArgs.requiredDate(Map.of("d", " 2026-09-01 "), "d")).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(ToolArgs.optionalLong(Map.of("id", 2001), "id")).isEqualTo(2001L);
        assertThat(ToolArgs.optionalLong(Map.of("id", "2001"), "id")).isEqualTo(2001L);
        assertThat(ToolArgs.optionalLong(Map.of(), "id")).isNull();
        assertThat(ToolArgs.optionalInt(Map.of(), "limit", 3, 1, 12)).isEqualTo(3);
        assertThat(ToolArgs.optionalInt(Map.of("limit", 5.0), "limit", 3, 1, 12)).isEqualTo(5);
    }

    @Test
    void rejectsBadValuesWithRussianMessage() {
        assertThatThrownBy(() -> ToolArgs.requiredDate(Map.of(), "period_from"))
                .isInstanceOf(ToolArgumentException.class).hasMessageContaining("period_from");
        assertThatThrownBy(() -> ToolArgs.requiredDate(Map.of("d", "01.09.2026"), "d"))
                .isInstanceOf(ToolArgumentException.class);
        assertThatThrownBy(() -> ToolArgs.optionalLong(Map.of("id", "два"), "id"))
                .isInstanceOf(ToolArgumentException.class);
        assertThatThrownBy(() -> ToolArgs.optionalInt(Map.of("limit", 40), "limit", 3, 1, 12))
                .isInstanceOf(ToolArgumentException.class);
    }
}
