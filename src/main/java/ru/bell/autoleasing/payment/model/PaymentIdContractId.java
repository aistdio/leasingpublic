package ru.bell.autoleasing.payment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.Hibernate;

import java.io.Serializable;
import java.util.Objects;

@Getter
@Setter
@Embeddable
@AllArgsConstructor
@NoArgsConstructor
public class PaymentIdContractId implements Serializable {
    @NotNull
    @Min(1)
    @Column(name = "payment_id")
    private Long paymentId;

    @NotNull
    @Min(1)
    @Column(name = "contract_id")
    private Long contractId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        PaymentIdContractId entity = (PaymentIdContractId) o;
        return Objects.equals(this.contractId, entity.contractId) &&
                Objects.equals(this.paymentId, entity.paymentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(contractId, paymentId);
    }

}