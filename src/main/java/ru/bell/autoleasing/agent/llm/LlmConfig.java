package ru.bell.autoleasing.agent.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import ru.bell.autoleasing.agent.AgentProperties;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

@Configuration
public class LlmConfig {

    @Bean
    public LlmClient llmClient(AgentProperties properties, RestClient.Builder builder, ObjectMapper mapper, Clock clock) {
        AgentProperties.Llm llm = properties.llm();
        return switch (llm.provider()) {
            case "ollama" -> {
                HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
                JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
                factory.setReadTimeout(Duration.ofSeconds(llm.timeoutSeconds()));
                yield new OllamaLlmClient(builder.clone().requestFactory(factory), llm, mapper);
            }
            case "mock" -> new MockLlmClient(clock, mapper);
            default -> throw new IllegalStateException("Неизвестный провайдер языковой модели: " + llm.provider());
        };
    }
}
