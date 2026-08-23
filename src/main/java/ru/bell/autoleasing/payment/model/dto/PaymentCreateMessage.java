package ru.bell.autoleasing.payment.model.dto;

import lombok.*;

import java.io.Serial;
import java.io.Serializable;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class PaymentCreateMessage implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private CreatePaymentDto createPaymentDto;
}
