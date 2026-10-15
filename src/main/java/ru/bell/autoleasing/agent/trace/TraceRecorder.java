package ru.bell.autoleasing.agent.trace;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Writes agent traces step by step. Every call commits on its own (no surrounding transaction),
 * so a failed run keeps its partial trace.
 */
@Component
@RequiredArgsConstructor
public class TraceRecorder {
    private final JdbcTemplate jdbc;

    public void start(UUID traceId, long clientId, String username, String question, String provider, String model,
                      String promptVersion, String requestId, String appEnv) {
        jdbc.update("INSERT INTO autoleasing_agent.traces (id, client_id, username, question, status, llm_provider, "
                        + "llm_model, prompt_version, request_id, app_env) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                traceId, clientId, username, question, TraceStatus.IN_PROGRESS.name(), provider, model, promptVersion,
                requestId, appEnv);
    }

    public void step(UUID traceId, int stepNo, StepType type, String toolName, String argumentsJson, String resultJson,
                     String error, long latencyMs, int promptTokens, int completionTokens) {
        jdbc.update("INSERT INTO autoleasing_agent.trace_steps (trace_id, step_no, step_type, tool_name, arguments_json, "
                        + "result_json, error, latency_ms, prompt_tokens, completion_tokens) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                traceId, stepNo, type.name(), toolName, argumentsJson, resultJson, error, latencyMs, promptTokens,
                completionTokens);
    }

    public void finish(UUID traceId, TraceStatus status, String routingRule, String answerJson, int promptTokens,
                       int completionTokens, long latencyMs, String error) {
        jdbc.update("UPDATE autoleasing_agent.traces SET status = ?, routing_rule = ?, answer_json = ?, prompt_tokens = ?, "
                        + "completion_tokens = ?, latency_ms = ?, error = ?, finished_at = now() WHERE id = ?",
                status.name(), routingRule, answerJson, promptTokens, completionTokens, latencyMs, error, traceId);
    }
}
