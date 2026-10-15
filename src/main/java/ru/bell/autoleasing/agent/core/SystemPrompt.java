package ru.bell.autoleasing.agent.core;

import org.springframework.stereotype.Component;
import ru.bell.autoleasing.agent.AgentProperties;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@Component
public class SystemPrompt {
    private final String version;
    private final String template;

    public SystemPrompt(AgentProperties properties) {
        this.version = properties.promptVersion();
        String path = "/agent/system-prompt-" + version + ".txt";
        try (InputStream in = SystemPrompt.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Нет файла промпта " + path);
            }
            this.template = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String version() {
        return version;
    }

    public String render(LocalDate today) {
        return template.replace("{today}", today.toString());
    }
}
