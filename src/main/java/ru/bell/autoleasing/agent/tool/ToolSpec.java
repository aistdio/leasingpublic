package ru.bell.autoleasing.agent.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ToolSpec(String name, String description, Map<String, Object> parameters) {

    public record Param(String name, String type, String description, boolean required) {
    }

    public static ToolSpec of(String name, String description, List<Param> params) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (Param p : params) {
            Map<String, Object> property = new LinkedHashMap<>();
            property.put("type", p.type());
            property.put("description", p.description());
            properties.put(p.name(), property);
            if (p.required()) {
                required.add(p.name());
            }
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        return new ToolSpec(name, description, schema);
    }
}
