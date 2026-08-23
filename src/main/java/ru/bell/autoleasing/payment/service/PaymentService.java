package ru.bell.autoleasing.payment.service;

import ru.bell.autoleasing.contract.model.Contract;
import ru.bell.autoleasing.payment.enums.PaymentStatus;
import ru.bell.autoleasing.payment.model.Payment;
import ru.bell.autoleasing.payment.model.PaymentIdContractId;
import ru.bell.autoleasing.payment.model.dto.PaymentDto;

import java.util.List;

public interface PaymentService {
    PaymentDto save(PaymentDto paymentDto);

    PaymentDto updateStatus(PaymentIdContractId paymentIdContractId, PaymentStatus paymentStatus);

    List<Payment> createPaymentsForContract(Contract contract);

    List<PaymentDto> findAll();

    PaymentDto findByContractIdAndPaymentId(Long contractId, Long paymentId);

    List<PaymentDto> findByContractId(Long contractId);

    List<PaymentDto> findByContractIdOrderedByPaymentId(Long contractId);

    void deleteByContractIdAndPaymentId(PaymentIdContractId paymentIdContractId);

    void deleteByContractId(Long contractId);
}
