package ru.bell.autoleasing.contract.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.envers.AuditTable;
import org.hibernate.envers.Audited;
import ru.bell.autoleasing.car.model.Car;
import ru.bell.autoleasing.client.model.Client;
import ru.bell.autoleasing.contract.enums.ContractStatus;
import ru.bell.autoleasing.contract.enums.ContractStatusConverter;
import ru.bell.autoleasing.payment.enums.PaymentStatus;
import ru.bell.autoleasing.payment.model.Payment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Entity
@Table(name = "contracts", schema = "autoleasing_core")
@Audited
@AuditTable(value = "contracts_aud", schema = "autoleasing_core_history")
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Contract {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(mappedBy = "contract")
    @OrderBy("dueDate ASC, paymentIdContractId.paymentId ASC")
    @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private List<Payment> payments;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "car_vin", referencedColumnName = "vin", nullable = false)
    private Car car;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", referencedColumnName = "id", nullable = false)
    private Client client;

    @Column(name = "duration_in_months", nullable = false)
    private Integer durationInMonths;

    @Column(name = "down_payment", nullable = false, precision = 20, scale = 2)
    private BigDecimal downPayment;

    @Column(name = "interest_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal interestRate;

    @Column(name = "monthly_payment", nullable = false, precision = 20, scale = 2)
    private BigDecimal monthlyPayment;

    @Column(name = "status", nullable = false, length = 100)
    @Convert(converter = ContractStatusConverter.class)
    private ContractStatus status;

    @Column(name = "contract_number", length = 30)
    private String contractNumber;

    @Column(name = "start_date")
    private LocalDate startDate;

    public List<Payment> getPayments() {
        if (payments == null) payments = new ArrayList<>();
        return List.copyOf(payments);
    }

    public static Optional<Payment> getCurrentMonthToPay(List<Payment> payments) {
        for (Payment payment : payments) {
            if (payment.getStatus().equals(PaymentStatus.UNPAID)) {
                return Optional.of(payment);
            }
        }
        return Optional.empty();
    }

    public static ContractStatus determineStatus(List<Payment> payments) {
        boolean allPaymentsArePaid = true;
        for (Payment payment : payments) {
            if (payment.getStatus().equals(PaymentStatus.UNPAID)) {
                allPaymentsArePaid = false;
                break;
            }
        }
        return allPaymentsArePaid ?
                ContractStatus.CLOSED : ContractStatus.ACTIVE;
    }

    @Override
    public String toString() {
        return "Contract{" +
                "id=" + id +
                ", payments=" + payments +
                ", car=" + car +
                ", client=" + client +
                ", durationInMonths=" + durationInMonths +
                ", downPayment=" + downPayment +
                ", interestRate=" + interestRate +
                ", monthlyPayment=" + monthlyPayment +
                ", status=" + status +
                '}';
    }

    public String toFileString() {
        String paymentsString = "";
        if (payments != null && !payments.isEmpty()) {
            paymentsString = payments.stream()
                    .map(Payment::toFileString)
                    .collect(Collectors.joining(" || "));
        }

        return String.join(
                " ||| ",
                id != null ? id.toString() : "",
                paymentsString,
                car != null ? car.toFileString() : "",
                client != null ? client.toFileString() : "",
                durationInMonths != null ? durationInMonths.toString() : "",
                downPayment != null ? downPayment.toPlainString() : "",
                interestRate != null ? interestRate.toPlainString() : "",
                status != null ? status.toString() : ""
        );
    }

    public static Contract fromFileString(String line) {
        String[] p = line.split(" \\|\\|\\| ", -1);
        if (p.length != 8) {
            throw new IllegalArgumentException("Неверный формат строки для получения сущности: " + line);
        }

        List<Payment> payments = new ArrayList<>();
        String paymentsString = p[1];
        if (!paymentsString.isEmpty()) {
            String[] paymentParts = paymentsString.split(" \\|\\| ", -1);
            for (String paymentPart : paymentParts) {
                if (!paymentPart.isEmpty()) {
                    payments.add(Payment.fromFileString(paymentPart));
                }
            }
        }

        return new Contract(
                Long.parseLong(p[0]),
                payments,
                Car.fromFileString(p[2]),
                Client.fromFileString(p[3]),
                Integer.parseInt(p[4]),
                new BigDecimal(p[5]),
                new BigDecimal(p[6]),
                payments.isEmpty() ? null : payments.getFirst().getAmount(),
                ContractStatus.fromString(p[7]),
                null,
                null
        );
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Contract contract = (Contract) o;

        Set<Payment> thisPayments = (this.payments == null) ? Collections.emptySet() : new HashSet<>(this.payments);
        Set<Payment> otherPayments = (contract.payments == null) ? Collections.emptySet() : new HashSet<>(contract.payments);

        return Objects.equals(id, contract.id)
                && Objects.equals(thisPayments, otherPayments)
                && Objects.equals(car, contract.car)
                && Objects.equals(client, contract.client)
                && Objects.equals(status, contract.status);
    }

    @Override
    public int hashCode() {
        Set<Payment> paymentsSet = (payments == null) ? Collections.emptySet() : new HashSet<>(payments);
        return Objects.hash(id, paymentsSet, car, client, status);
    }
}
