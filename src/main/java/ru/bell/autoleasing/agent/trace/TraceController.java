package ru.bell.autoleasing.agent.trace;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import ru.bell.autoleasing.agent.trace.dto.TraceSummary;
import ru.bell.autoleasing.agent.trace.dto.TraceView;
import ru.bell.autoleasing.exceptions.NotFoundException;
import ru.bell.autoleasing.exceptions.ValidationException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/agent/traces")
@RequiredArgsConstructor
public class TraceController {
    private final TraceQueryService traces;

    @GetMapping("/{id}")
    public TraceView get(@PathVariable String id) {
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("Неверный идентификатор трассировки: " + id);
        }
        return traces.find(uuid).orElseThrow(() -> new NotFoundException("Трассировка " + id + " не найдена"));
    }

    @GetMapping
    public List<TraceSummary> recent(@RequestParam(defaultValue = "50") int limit) {
        if (limit < 1 || limit > 200) {
            throw new ValidationException("limit должен быть от 1 до 200");
        }
        return traces.recent(limit);
    }
}
