package ru.bell.autoleasing.cabinet;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import ru.bell.autoleasing.cabinet.dto.*;
import ru.bell.autoleasing.exceptions.NotFoundException;
import ru.bell.autoleasing.payment.enums.PaymentStatus;
import ru.bell.autoleasing.user.AppUserPrincipal;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class CabinetController {
    private final ClientFinanceQueryService finance;
    private final Clock clock;

    @GetMapping
    public MeResponse me(@AuthenticationPrincipal AppUserPrincipal user) {
        return finance.findProfile(user.clientId(), user.username())
                .orElseThrow(() -> new NotFoundException("Клиент не найден"));
    }

    @GetMapping("/contracts")
    public List<ContractSummary> contracts(@AuthenticationPrincipal AppUserPrincipal user) {
        return finance.findContracts(user.clientId(), LocalDate.now(clock));
    }

    @GetMapping("/contracts/{contractId}/schedule")
    public List<PaymentRow> schedule(@AuthenticationPrincipal AppUserPrincipal user, @PathVariable long contractId) {
        return finance.findSchedule(user.clientId(), contractId, LocalDate.now(clock));
    }

    @GetMapping("/payments")
    public List<PaymentRow> payments(@AuthenticationPrincipal AppUserPrincipal user,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                     @RequestParam(required = false) PaymentStatus status) {
        return finance.findPayments(user.clientId(), from, to, status, LocalDate.now(clock));
    }

    @GetMapping("/payments/summary")
    public PaymentsSummary summary(@AuthenticationPrincipal AppUserPrincipal user,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return finance.summarize(user.clientId(), from, to);
    }

    @GetMapping("/payments/upcoming")
    public UpcomingPayments upcoming(@AuthenticationPrincipal AppUserPrincipal user,
                                     @RequestParam(required = false) Long contractId,
                                     @RequestParam(defaultValue = "3") int limit) {
        return finance.findUpcoming(user.clientId(), contractId, limit, LocalDate.now(clock));
    }
}
