package ru.bell.autoleasing.agent.trace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import ru.bell.autoleasing.agent.trace.dto.TraceStepView;
import ru.bell.autoleasing.agent.trace.dto.TraceSummary;
import ru.bell.autoleasing.agent.trace.dto.TraceView;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TraceQueryService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public Optional<TraceView> find(UUID id) {
        List<TraceStepView> steps = jdbc.query("SELECT * FROM autoleasing_agent.trace_steps WHERE trace_id = ? ORDER BY step_no",
                (rs, i) -> new TraceStepView(rs.getInt("step_no"), rs.getString("step_type"), rs.getString("tool_name"),
                        rs.getString("arguments_json"), rs.getString("result_json"), rs.getString("error"),
                        rs.getLong("latency_ms"), rs.getInt("prompt_tokens"), rs.getInt("completion_tokens")), id);
        return jdbc.query("SELECT * FROM autoleasing_agent.traces WHERE id = ?",
                (rs, i) -> new TraceView(rs.getObject("id", UUID.class), rs.getLong("client_id"), rs.getString("username"),
                        rs.getString("question"), rs.getString("routing_rule"), rs.getString("status"),
                        parse(rs.getString("answer_json")), rs.getString("error"), rs.getString("llm_provider"),
                        rs.getString("llm_model"), rs.getString("prompt_version"), rs.getInt("prompt_tokens"),
                        rs.getInt("completion_tokens"), rs.getObject("latency_ms", Long.class), rs.getString("request_id"),
                        rs.getString("app_env"), rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("finished_at", OffsetDateTime.class), steps), id).stream().findFirst();
    }

    public List<TraceSummary> recent(int limit) {
        return jdbc.query("SELECT id, username, question, status, latency_ms, prompt_tokens + completion_tokens AS total_tokens, "
                        + "created_at FROM autoleasing_agent.traces ORDER BY created_at DESC LIMIT ?",
                (rs, i) -> new TraceSummary(rs.getObject("id", UUID.class), rs.getString("username"), rs.getString("question"),
                        rs.getString("status"), rs.getObject("latency_ms", Long.class), rs.getInt("total_tokens"),
                        rs.getObject("created_at", OffsetDateTime.class)), limit);
    }

    private JsonNode parse(String json) {
        if (json == null) {
            return null;
        }
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            return TextNode.valueOf(json);
        }
    }
}
