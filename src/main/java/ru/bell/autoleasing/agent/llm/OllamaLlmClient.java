package ru.bell.autoleasing.agent.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import ru.bell.autoleasing.agent.AgentProperties;
import ru.bell.autoleasing.agent.tool.ToolSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OllamaLlmClient implements LlmClient {
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final RestClient restClient;
    private final AgentProperties.Llm props;
    private final ObjectMapper mapper;

    public OllamaLlmClient(RestClient.Builder builder, AgentProperties.Llm props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.restClient = builder.baseUrl(props.baseUrl()).build();
    }

    @Override
    public String provider() {
        return "ollama";
    }

    @Override
    public String model() {
        return props.model();
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        JsonNode response;
        try {
            response = restClient.post().uri("/api/chat").contentType(MediaType.APPLICATION_JSON)
                    .body(buildBody(request)).retrieve().body(JsonNode.class);
        } catch (RestClientException e) {
            throw new LlmUnavailableException("Ollama недоступна: " + e.getMessage(), e);
        }
        if (response == null) {
            throw new LlmUnavailableException("Пустой ответ Ollama", null);
        }
        return parse(response);
    }

    private ObjectNode buildBody(LlmRequest request) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", props.model());
        body.put("stream", false);
        body.put("think", false);
        ObjectNode options = body.putObject("options");
        options.put("num_ctx", props.numCtx());
        options.put("temperature", props.temperature());
        ArrayNode messages = body.putArray("messages");
        for (LlmMessage message : request.messages()) {
            ObjectNode node = messages.addObject();
            node.put("role", message.role());
            node.put("content", message.content() == null ? "" : message.content());
            if (message.toolName() != null) {
                node.put("tool_name", message.toolName());
            }
            if (message.toolCalls() != null && !message.toolCalls().isEmpty()) {
                ArrayNode calls = node.putArray("tool_calls");
                for (LlmToolCall call : message.toolCalls()) {
                    ObjectNode function = calls.addObject().putObject("function");
                    function.put("name", call.name());
                    function.set("arguments", mapper.valueToTree(call.arguments()));
                }
            }
        }
        if (request.tools() != null && !request.tools().isEmpty()) {
            ArrayNode tools = body.putArray("tools");
            for (ToolSpec spec : request.tools()) {
                ObjectNode function = tools.addObject().put("type", "function").putObject("function");
                function.put("name", spec.name());
                function.put("description", spec.description());
                function.set("parameters", mapper.valueToTree(spec.parameters()));
            }
        }
        return body;
    }

    private LlmResponse parse(JsonNode response) {
        JsonNode message = response.path("message");
        List<LlmToolCall> calls = new ArrayList<>();
        for (JsonNode call : message.path("tool_calls")) {
            JsonNode function = call.path("function");
            calls.add(new LlmToolCall(function.path("name").asText(), arguments(function.path("arguments"))));
        }
        return new LlmResponse(message.path("content").asText(""), calls, response.path("prompt_eval_count").asInt(0),
                response.path("eval_count").asInt(0), response.path("model").asText(props.model()));
    }

    private Map<String, Object> arguments(JsonNode node) {
        try {
            if (node.isTextual()) {
                return mapper.readValue(node.asText(), MAP);
            }
            if (node.isObject()) {
                return mapper.convertValue(node, MAP);
            }
        } catch (JsonProcessingException | IllegalArgumentException e) {
            return Map.of("_raw", node.toString());
        }
        return Map.of();
    }
}
