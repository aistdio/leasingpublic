package ru.bell.autoleasing.payment.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.envers.AuditTable;
import org.hibernate.envers.Audited;
import ru.bell.autoleasing.contract.model.Contract;
import ru.bell.autoleasing.payment.enums.PaymentStatus;
import ru.bell.autoleasing.payment.enums.PaymentStatusConverter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "payments", schema = "autoleasing_core")
@Audited
@AuditTable(value = "payments_aud", schema = "autoleasing_core_history")
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Payment {
    @Valid
    @EmbeddedId
    @JsonUnwrapped
    private PaymentIdContractId paymentIdContractId;

    @NotNull
    @Column(precision = 20, scale = 2)
    private BigDecimal amount;

    @NotNull
    @Convert(converter = PaymentStatusConverter.class)
    private PaymentStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", insertable = false, updatable = false)
    @JsonIgnore
    private Contract contract;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "paid_date")
    private LocalDate paidDate;

    @Column(name = "principal_part", precision = 20, scale = 2, nullable = false)
    private BigDecimal principalPart;

    @Column(name = "markup", precision = 20, scale = 2, nullable = false)
    private BigDecimal markup;

    @Column(name = "vat", precision = 20, scale = 2, nullable = false)
    private BigDecimal vat;

    @Column(name = "penalty", precision = 20, scale = 2, nullable = false)
    private BigDecimal penalty;

    @PrePersist
    void applyDefaults() {
        if (principalPart == null) principalPart = BigDecimal.ZERO.setScale(2);
        if (markup == null) markup = BigDecimal.ZERO.setScale(2);
        if (vat == null) vat = BigDecimal.ZERO.setScale(2);
        if (penalty == null) penalty = BigDecimal.ZERO.setScale(2);
    }

    @Override
    public String toString() {
        return "Payment{" +
                "paymentId=" + paymentIdContractId.getPaymentId() +
                ", contractId=" + paymentIdContractId.getContractId() +
                ", amount=" + amount +
                ", status=" + status +
                '}';
    }

    public String toFileString() {
        return String.join(
                "|",
                paymentIdContractId.getPaymentId() == null ? "" : paymentIdContractId.getPaymentId().toString(),
                paymentIdContractId.getContractId() == null ? "" : paymentIdContractId.getContractId().toString(),
                amount.toPlainString(),
                status.toString()
        );
    }

    public static Payment fromFileString(String line) {
        String[] p = line.split("\\|", -1);
        if (p.length != 4) {
            throw new IllegalArgumentException("Неверный формат строки для получения сущности: " + line);
        }

        Long id = null;
        Long contractId = null;
        try {
            id = Long.parseLong(p[0]);
        } catch (NumberFormatException ignored) {
        }
        try {
            contractId = Long.parseLong(p[1]);
        } catch (NumberFormatException ignored) {
        }

        return new Payment(
                new PaymentIdContractId(id, contractId),
                new BigDecimal(p[2]),
                PaymentStatus.fromString(p[3]),
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Payment payment = (Payment) o;
        return Objects.equals(paymentIdContractId, payment.paymentIdContractId) && Objects.equals(status, payment.status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(paymentIdContractId, status);
    }
}
