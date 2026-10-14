package ru.bell.autoleasing.seed;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.bell.autoleasing.user.UserRole;

import java.sql.Date;
import java.sql.Types;

@Component
@RequiredArgsConstructor
public class SeedDataWriter {
    private final JdbcTemplate jdbc;

    @Transactional
    public void write(SeedData data, String clientPasswordHash, String managerPasswordHash) {
        jdbc.batchUpdate("INSERT INTO autoleasing_core.cars (vin, brand, model, release_year, price, status, version) "
                + "VALUES (?, ?, ?, ?, ?, ?, 0)", data.cars(), 200, (ps, c) -> {
            ps.setString(1, c.vin());
            ps.setString(2, c.brand());
            ps.setString(3, c.model());
            ps.setInt(4, c.releaseYear());
            ps.setBigDecimal(5, c.price());
            ps.setString(6, c.status().toString());
        });
        jdbc.batchUpdate("INSERT INTO autoleasing_core.clients (id, name, passport, phone, client_type, inn, legal_name) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)", data.clients(), 200, (ps, c) -> {
            ps.setLong(1, c.id());
            ps.setString(2, c.name());
            ps.setString(3, c.passport());
            ps.setString(4, c.phone());
            ps.setString(5, c.type().toString());
            ps.setString(6, c.inn());
            ps.setString(7, c.legalName());
        });
        jdbc.batchUpdate("INSERT INTO autoleasing_core.contracts (id, contract_number, car_vin, client_id, duration_in_months, "
                + "down_payment, interest_rate, monthly_payment, status, start_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                data.contracts(), 200, (ps, c) -> {
            ps.setLong(1, c.id());
            ps.setString(2, c.number());
            ps.setString(3, c.carVin());
            ps.setLong(4, c.clientId());
            ps.setInt(5, c.durationInMonths());
            ps.setBigDecimal(6, c.downPayment());
            ps.setBigDecimal(7, c.interestRate());
            ps.setBigDecimal(8, c.monthlyPayment());
            ps.setString(9, c.status().toString());
            ps.setDate(10, Date.valueOf(c.startDate()));
        });
        jdbc.batchUpdate("INSERT INTO autoleasing_core.payments (contract_id, payment_id, due_date, paid_date, amount, "
                + "principal_part, markup, vat, penalty, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                data.payments(), 500, (ps, p) -> {
            ps.setLong(1, p.contractId());
            ps.setLong(2, p.paymentId());
            ps.setDate(3, Date.valueOf(p.dueDate()));
            if (p.paidDate() == null) {
                ps.setNull(4, Types.DATE);
            } else {
                ps.setDate(4, Date.valueOf(p.paidDate()));
            }
            ps.setBigDecimal(5, p.amount());
            ps.setBigDecimal(6, p.principalPart());
            ps.setBigDecimal(7, p.markup());
            ps.setBigDecimal(8, p.vat());
            ps.setBigDecimal(9, p.penalty());
            ps.setString(10, p.status().toString());
        });
        jdbc.batchUpdate("INSERT INTO autoleasing_core.app_users (username, password_hash, role, client_id) VALUES (?, ?, ?, ?)",
                data.users(), 200, (ps, u) -> {
            ps.setString(1, u.username());
            ps.setString(2, u.role() == UserRole.MANAGER ? managerPasswordHash : clientPasswordHash);
            ps.setString(3, u.role().name());
            if (u.clientId() == null) {
                ps.setNull(4, Types.BIGINT);
            } else {
                ps.setLong(4, u.clientId());
            }
        });
        jdbc.execute("SELECT setval(pg_get_serial_sequence('autoleasing_core.clients', 'id'), (SELECT MAX(id) FROM autoleasing_core.clients))");
        jdbc.execute("SELECT setval(pg_get_serial_sequence('autoleasing_core.contracts', 'id'), (SELECT MAX(id) FROM autoleasing_core.contracts))");
    }
}
