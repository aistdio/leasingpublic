package ru.bell.autoleasing.agent.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.bell.autoleasing.agent.AgentProperties;
import ru.bell.autoleasing.agent.api.AgentAnswer;
import ru.bell.autoleasing.agent.llm.*;
import ru.bell.autoleasing.agent.router.RoutingDecision;
import ru.bell.autoleasing.agent.router.ToolRouter;
import ru.bell.autoleasing.agent.tool.*;
import ru.bell.autoleasing.agent.trace.StepType;
import ru.bell.autoleasing.agent.trace.TraceRecorder;
import ru.bell.autoleasing.agent.trace.TraceStatus;
import ru.bell.autoleasing.exceptions.NotFoundException;
import ru.bell.autoleasing.exceptions.ValidationException;
import ru.bell.autoleasing.platform.RequestIdFilter;
import ru.bell.autoleasing.user.AppUserPrincipal;

import java.time.Clock;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
public class AgentOrchestrator {
    public static final String MAX_STEPS_WARNING = "MAX_STEPS_EXCEEDED";

    private final LlmClient llm;
    private final ToolRouter router;
    private final ToolRegistry tools;
    private final TraceRecorder traces;
    private final AnswerParser parser;
    private final SystemPrompt systemPrompt;
    private final AgentProperties properties;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final String appEnv;

    public AgentOrchestrator(LlmClient llm, ToolRouter router, ToolRegistry tools, TraceRecorder traces, AnswerParser parser,
                             SystemPrompt systemPrompt, AgentProperties properties, ObjectMapper mapper, Clock clock,
                             @Value("${autolease.app-env}") String appEnv) {
        this.llm = llm;
        this.router = router;
        this.tools = tools;
        this.traces = traces;
        this.parser = parser;
        this.systemPrompt = systemPrompt;
        this.properties = properties;
        this.mapper = mapper;
        this.clock = clock;
        this.appEnv = appEnv;
    }

    public AgentAnswer ask(AppUserPrincipal user, String question) {
        String q = question == null ? "" : question.trim();
        if (q.isEmpty()) {
            throw new ValidationException("Вопрос не должен быть пустым");
        }
        if (q.length() > properties.questionMaxLength()) {
            throw new ValidationException("Вопрос длиннее " + properties.questionMaxLength() + " символов");
        }
        UUID traceId = UUID.randomUUID();
        long started = System.nanoTime();
        LocalDate today = LocalDate.now(clock);
        ToolContext context = new ToolContext(user.clientId(), today);
        RoutingDecision decision = router.route(q);
        traces.start(traceId, user.clientId(), user.username(), q, llm.provider(), llm.model(), systemPrompt.version(),
                MDC.get(RequestIdFilter.MDC_KEY), appEnv);

        int stepNo = 0;
        Set<String> toolsUsed = new LinkedHashSet<>();
        int promptTokens = 0;
        int completionTokens = 0;
        try {
            traces.step(traceId, ++stepNo, StepType.ROUTING, null, json(decision.allowedTools()), decision.rule(), null, 0, 0, 0);

            List<LlmMessage> messages = new ArrayList<>(List.of(LlmMessage.system(systemPrompt.render(today)), LlmMessage.user(q)));
            List<ToolSpec> specs = tools.specs(decision.allowedTools());
            for (int i = 0; i < properties.maxSteps(); i++) {
                long callStarted = System.nanoTime();
                LlmResponse response = llm.chat(new LlmRequest(List.copyOf(messages), specs));
                promptTokens += response.promptTokens();
                completionTokens += response.completionTokens();
                traces.step(traceId, ++stepNo, StepType.LLM_CALL, null, null,
                        response.hasToolCalls() ? json(response.toolCalls()) : response.content(), null,
                        millisSince(callStarted), response.promptTokens(), response.completionTokens());

                if (!response.hasToolCalls()) {
                    AnswerParser.Result result = parser.parse(response.content(), traceId, List.copyOf(toolsUsed));
                    traces.finish(traceId, result.valid() ? TraceStatus.OK : TraceStatus.INVALID_OUTPUT, decision.rule(),
                            json(result.answer()), promptTokens, completionTokens, millisSince(started), result.error());
                    return result.answer();
                }

                messages.add(LlmMessage.assistant(response.content(), response.toolCalls()));
                for (LlmToolCall call : response.toolCalls()) {
                    long toolStarted = System.nanoTime();
                    String resultJson;
                    String error = null;
                    Optional<AgentTool> tool = tools.find(call.name());
                    if (!decision.allowedTools().contains(call.name()) || tool.isEmpty()) {
                        error = "TOOL_NOT_ALLOWED";
                        resultJson = json(Map.of("error", "Инструмент " + call.name() + " недоступен для этого вопроса"));
                    } else {
                        try {
                            resultJson = json(tool.get().execute(call.arguments(), context));
                            toolsUsed.add(call.name());
                        } catch (ToolArgumentException | NotFoundException | ValidationException e) {
                            error = describe(e);
                            resultJson = json(Map.of("error", error));
                        }
                    }
                    traces.step(traceId, ++stepNo, StepType.TOOL_CALL, call.name(), json(call.arguments()), resultJson,
                            error, millisSince(toolStarted), 0, 0);
                    messages.add(LlmMessage.tool(call.name(), resultJson));
                }
            }
            AgentAnswer fallback = new AgentAnswer(AnswerParser.FALLBACK_TEXT, List.of(), List.copyOf(toolsUsed),
                    traceId.toString(), List.of(MAX_STEPS_WARNING));
            traces.finish(traceId, TraceStatus.MAX_STEPS, decision.rule(), json(fallback), promptTokens, completionTokens,
                    millisSince(started), "Превышено число шагов: " + properties.maxSteps());
            return fallback;
        } catch (LlmUnavailableException e) {
            log.warn("Языковая модель недоступна, трассировка {}: {}", traceId, e.getMessage());
            traces.finish(traceId, TraceStatus.LLM_ERROR, decision.rule(), null, promptTokens, completionTokens,
                    millisSince(started), e.getMessage());
            throw new AgentUnavailableException(traceId, e);
        } catch (RuntimeException e) {
            log.error("Непредвиденная ошибка ассистента, трассировка {}: {}", traceId, e.getMessage(), e);
            finishWithError(traceId, decision.rule(), promptTokens, completionTokens, millisSince(started), e);
            throw e;
        }
    }

    private void finishWithError(UUID traceId, String routingRule, int promptTokens, int completionTokens, long latencyMs,
                                 RuntimeException cause) {
        try {
            traces.finish(traceId, TraceStatus.ERROR, routingRule, null, promptTokens, completionTokens, latencyMs, describe(cause));
        } catch (RuntimeException failure) {
            cause.addSuppressed(failure);
        }
    }

    private static String describe(Throwable e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getName() : message;
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static long millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
