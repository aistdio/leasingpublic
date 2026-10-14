package ru.bell.autoleasing.cabinet;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import ru.bell.autoleasing.cabinet.dto.*;
import ru.bell.autoleasing.contract.enums.ContractStatus;
import ru.bell.autoleasing.exceptions.NotFoundException;
import ru.bell.autoleasing.exceptions.ValidationException;
import ru.bell.autoleasing.payment.enums.PaymentStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ClientFinanceQueryService {
    private static final int MAX_LIMIT = 12;
    private static final String PAYMENT_SELECT = """
            SELECT p.contract_id, c.contract_number, p.payment_id, p.due_date, p.paid_date, p.amount,
                   p.principal_part, p.markup, p.vat, p.penalty, p.status
            FROM autoleasing_core.payments p
            JOIN autoleasing_core.contracts c ON c.id = p.contract_id
            WHERE c.client_id = :clientId
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public Optional<MeResponse> findProfile(long clientId, String username) {
        return jdbc.query("SELECT id, name, client_type, inn, legal_name FROM autoleasing_core.clients WHERE id = :clientId",
                        params(clientId),
                        (rs, i) -> new MeResponse(username, rs.getLong("id"), rs.getString("client_type"),
                                rs.getString("legal_name"), rs.getString("inn"), rs.getString("name")))
                .stream().findFirst();
    }

    public List<ContractSummary> findContracts(long clientId, LocalDate today) {
        String sql = """
                SELECT c.id, c.contract_number, c.status, c.start_date, c.duration_in_months, c.monthly_payment,
                       car.brand, car.model, car.vin,
                       COALESCE(SUM(p.amount) FILTER (WHERE p.status = :unpaid), 0) AS remaining_debt,
                       COUNT(p.payment_id) FILTER (WHERE p.status = :unpaid) AS unpaid_count,
                       MIN(p.due_date) FILTER (WHERE p.status = :unpaid AND p.due_date >= :today) AS next_due_date
                FROM autoleasing_core.contracts c
                JOIN autoleasing_core.cars car ON car.vin = c.car_vin
                LEFT JOIN autoleasing_core.payments p ON p.contract_id = c.id
                WHERE c.client_id = :clientId
                GROUP BY c.id, car.vin
                ORDER BY c.start_date, c.id
                """;
        return jdbc.query(sql, params(clientId).addValue("today", today), (rs, i) -> {
            ContractStatus status = ContractStatus.fromString(rs.getString("status"));
            return new ContractSummary(rs.getLong("id"), rs.getString("contract_number"), status.name(),
                    status.toString(), toLocalDate(rs.getDate("start_date")), rs.getInt("duration_in_months"),
                    money(rs.getBigDecimal("monthly_payment")), rs.getString("brand") + " " + rs.getString("model"),
                    rs.getString("vin"), money(rs.getBigDecimal("remaining_debt")), rs.getInt("unpaid_count"),
                    toLocalDate(rs.getDate("next_due_date")));
        });
    }

    public List<PaymentRow> findSchedule(long clientId, long contractId, LocalDate today) {
        requireOwnContract(clientId, contractId);
        return jdbc.query(PAYMENT_SELECT + " AND p.contract_id = :contractId ORDER BY p.due_date, p.payment_id",
                params(clientId).addValue("contractId", contractId), (rs, i) -> paymentRow(rs, today));
    }

    public List<PaymentRow> findPayments(long clientId, LocalDate from, LocalDate to, PaymentStatus statusOrNull,
                                         LocalDate today) {
        requirePeriod(from, to);
        MapSqlParameterSource p = params(clientId).addValue("from", from).addValue("to", to);
        String sql = PAYMENT_SELECT + " AND p.due_date BETWEEN :from AND :to";
        if (statusOrNull != null) {
            sql += " AND p.status = :status";
            p.addValue("status", statusOrNull.toString());
        }
        return jdbc.query(sql + " ORDER BY p.due_date, p.contract_id, p.payment_id", p, (rs, i) -> paymentRow(rs, today));
    }

    public PaymentsSummary summarize(long clientId, LocalDate from, LocalDate to) {
        requirePeriod(from, to);
        String sql = """
                SELECT
                  COALESCE(SUM(p.amount + p.penalty) FILTER (WHERE p.status = :paid AND p.paid_date BETWEEN :from AND :to), 0) AS paid_total,
                  COUNT(*) FILTER (WHERE p.status = :paid AND p.paid_date BETWEEN :from AND :to) AS paid_count,
                  COALESCE(SUM(p.amount + p.penalty) FILTER (WHERE p.status <> :cancelled AND p.due_date BETWEEN :from AND :to), 0) AS accrued_total,
                  COUNT(*) FILTER (WHERE p.status <> :cancelled AND p.due_date BETWEEN :from AND :to) AS accrued_count
                FROM autoleasing_core.payments p
                JOIN autoleasing_core.contracts c ON c.id = p.contract_id
                WHERE c.client_id = :clientId
                """;
        return jdbc.queryForObject(sql, params(clientId).addValue("from", from).addValue("to", to),
                (rs, i) -> new PaymentsSummary(from, to, money(rs.getBigDecimal("paid_total")), rs.getInt("paid_count"),
                        money(rs.getBigDecimal("accrued_total")), rs.getInt("accrued_count"), "RUB"));
    }

    public UpcomingPayments findUpcoming(long clientId, Long contractIdOrNull, int limit, LocalDate today) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new ValidationException("limit должен быть от 1 до " + MAX_LIMIT);
        }
        MapSqlParameterSource p = params(clientId);
        String sql = PAYMENT_SELECT + " AND p.status = :unpaid";
        if (contractIdOrNull != null) {
            requireOwnContract(clientId, contractIdOrNull);
            sql += " AND p.contract_id = :contractId";
            p.addValue("contractId", contractIdOrNull);
        }
        List<PaymentRow> unpaid = jdbc.query(sql + " ORDER BY p.due_date, p.contract_id, p.payment_id", p,
                (rs, i) -> paymentRow(rs, today));
        List<PaymentRow> overdue = unpaid.stream().filter(PaymentRow::overdue).toList();
        PaymentRow next = unpaid.stream().filter(r -> r.dueDate().isAfter(today)).findFirst().orElse(null);
        BigDecimal overdueTotal = overdue.stream().map(PaymentRow::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new UpcomingPayments(next, unpaid.stream().limit(limit).toList(), overdue.size(), money(overdueTotal));
    }

    private void requireOwnContract(long clientId, long contractId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM autoleasing_core.contracts WHERE id = :contractId AND client_id = :clientId",
                params(clientId).addValue("contractId", contractId), Integer.class);
        if (count == null || count == 0) {
            throw new NotFoundException("Договор " + contractId + " не найден");
        }
    }

    private static void requirePeriod(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new ValidationException("Начало периода должно быть не позже конца периода");
        }
    }

    private static MapSqlParameterSource params(long clientId) {
        return new MapSqlParameterSource("clientId", clientId)
                .addValue("paid", PaymentStatus.PAID.toString())
                .addValue("unpaid", PaymentStatus.UNPAID.toString())
                .addValue("cancelled", PaymentStatus.CANCELLED.toString());
    }

    private static PaymentRow paymentRow(ResultSet rs, LocalDate today) throws SQLException {
        PaymentStatus status = PaymentStatus.fromString(rs.getString("status"));
        LocalDate due = toLocalDate(rs.getDate("due_date"));
        boolean overdue = status == PaymentStatus.UNPAID && due != null && !due.isAfter(today);
        return new PaymentRow(rs.getLong("contract_id"), rs.getString("contract_number"), rs.getLong("payment_id"), due,
                toLocalDate(rs.getDate("paid_date")), money(rs.getBigDecimal("amount")),
                money(rs.getBigDecimal("principal_part")), money(rs.getBigDecimal("markup")),
                money(rs.getBigDecimal("vat")), money(rs.getBigDecimal("penalty")), status.name(), status.toString(), overdue);
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static LocalDate toLocalDate(Date date) {
        return date == null ? null : date.toLocalDate();
    }
}
