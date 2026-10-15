package ru.bell.autoleasing.agent.tool;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

public final class ToolArgs {
    private ToolArgs() {
    }

    public static LocalDate requiredDate(Map<String, Object> args, String name) {
        Object value = args == null ? null : args.get(name);
        if (value == null || value.toString().isBlank()) {
            throw new ToolArgumentException("Параметр " + name + " обязателен, формат ГГГГ-ММ-ДД");
        }
        try {
            return LocalDate.parse(value.toString().trim());
        } catch (DateTimeParseException e) {
            throw new ToolArgumentException("Параметр " + name + " должен быть датой ГГГГ-ММ-ДД, получено: " + value);
        }
    }

    public static Long optionalLong(Map<String, Object> args, String name) {
        Object value = args == null ? null : args.get(name);
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException e) {
            throw new ToolArgumentException("Параметр " + name + " должен быть целым числом, получено: " + value);
        }
    }

    public static int optionalInt(Map<String, Object> args, String name, int defaultValue, int min, int max) {
        Long value = optionalLong(args, name);
        if (value == null) {
            return defaultValue;
        }
        if (value < min || value > max) {
            throw new ToolArgumentException("Параметр " + name + " должен быть от " + min + " до " + max + ", получено: " + value);
        }
        return value.intValue();
    }
}
