package ru.bell.autoleasing.agent.api;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.bell.autoleasing.agent.core.AgentOrchestrator;
import ru.bell.autoleasing.user.AppUserPrincipal;

@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {
    private final AgentOrchestrator orchestrator;

    @PostMapping("/ask")
    public AgentAnswer ask(@AuthenticationPrincipal AppUserPrincipal user, @RequestBody AskRequest request) {
        return orchestrator.ask(user, request == null ? null : request.question());
    }
}
