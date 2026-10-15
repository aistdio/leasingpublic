package ru.bell.autoleasing.agent.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.springframework.stereotype.Component;
import ru.bell.autoleasing.agent.api.AgentAnswer;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class AnswerParser {
    public static final String INVALID_OUTPUT_WARNING = "INVALID_MODEL_OUTPUT";
    public static final String FALLBACK_TEXT = "Не удалось сформировать ответ. Попробуйте переформулировать вопрос.";

    public record Result(AgentAnswer answer, boolean valid, String error) {
    }

    private final ObjectMapper mapper;
    private final JsonSchema schema;

    public AnswerParser(ObjectMapper mapper) {
        this.mapper = mapper;
        this.schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema(AnswerParser.class.getResourceAsStream("/agent/answer-schema.json"));
    }

    public Result parse(String modelContent, UUID traceId, List<String> toolsUsed) {
        String text = stripFences(modelContent);
        try {
            // Money must not pass through double: read decimals as BigDecimal.
            JsonNode node = mapper.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(text);
            if (node == null || !node.isObject()) {
                return invalid(traceId, toolsUsed, "Ответ модели не является JSON-объектом");
            }
            ObjectNode answer = (ObjectNode) node;
            answer.set("tools_used", mapper.valueToTree(toolsUsed));
            answer.put("trace_id", traceId.toString());
            Set<ValidationMessage> errors = schema.validate(answer);
            if (!errors.isEmpty()) {
                return invalid(traceId, toolsUsed, errors.stream().map(ValidationMessage::getMessage).sorted()
                        .collect(Collectors.joining("; ")));
            }
            return new Result(mapper.treeToValue(answer, AgentAnswer.class), true, null);
        } catch (JsonProcessingException e) {
            return invalid(traceId, toolsUsed, "Невалидный JSON: " + e.getOriginalMessage());
        }
    }

    static String stripFences(String content) {
        if (content == null) {
            return "";
        }
        String text = content.trim();
        if (text.startsWith("```")) {
            int firstLineEnd = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstLineEnd > 0 && lastFence > firstLineEnd) {
                text = text.substring(firstLineEnd + 1, lastFence).trim();
            }
        }
        return text;
    }

    private Result invalid(UUID traceId, List<String> toolsUsed, String error) {
        return new Result(new AgentAnswer(FALLBACK_TEXT, List.of(), List.copyOf(toolsUsed), traceId.toString(),
                List.of(INVALID_OUTPUT_WARNING)), false, error);
    }
}
