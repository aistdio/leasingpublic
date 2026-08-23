package ru.bell.autoleasing.payment.service;

import lombok.AllArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.bell.autoleasing.contract.model.Contract;
import ru.bell.autoleasing.exceptions.DataCreationException;
import ru.bell.autoleasing.exceptions.DataUpdateException;
import ru.bell.autoleasing.exceptions.NotFoundException;
import ru.bell.autoleasing.payment.enums.PaymentStatus;
import ru.bell.autoleasing.payment.model.Payment;
import ru.bell.autoleasing.payment.model.PaymentIdContractId;
import ru.bell.autoleasing.payment.model.dto.PaymentDto;
import ru.bell.autoleasing.payment.model.mapper.PaymentMapper;
import ru.bell.autoleasing.payment.repository.PaymentRepository;
import ru.bell.autoleasing.utilities.AnnuityUtilities;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@AllArgsConstructor
public class PaymentServiceImpl implements PaymentService {
    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;

    @Override
    @Transactional
    public PaymentDto save(PaymentDto paymentDto) {
        Payment payment = paymentMapper.toEntity(paymentDto);

        Long generatedPaymentId = paymentRepository.findLatestPaymentIdByContractIdWithPessimisticWriteLock(payment.getPaymentIdContractId().getContractId())
                .orElse(0L) + 1L;

        payment.setPaymentIdContractId(new PaymentIdContractId(generatedPaymentId, payment.getPaymentIdContractId().getContractId()));

        try {
            paymentRepository.insert(payment);
        } catch (DataIntegrityViolationException e) {
            throw new DataCreationException(String.format("Платеж номер %s у договора номер %s ранее уже был создан", generatedPaymentId, payment.getPaymentIdContractId().getContractId()));
        }

        return paymentMapper.toDto(payment);
    }

    @Override
    @Transactional
    public PaymentDto updateStatus(PaymentIdContractId paymentIdContractId, PaymentStatus paymentStatus) {
        String errorMessage = String.format("Не удалось обновить статус платежа номер %s у договора номер %s", paymentIdContractId.getPaymentId(), paymentIdContractId.getContractId());
        int affected = paymentRepository.updateStatus(paymentStatus, paymentIdContractId);
        if (affected == 0) {
            throw new DataUpdateException(errorMessage);
        }
        return this.findByContractIdAndPaymentId(paymentIdContractId.getContractId(), paymentIdContractId.getPaymentId());
    }

    @Override
    @Transactional
    public List<Payment> createPaymentsForContract(Contract contract) {
        BigDecimal annuityPayment = AnnuityUtilities.calculateMonthlyAnnuityWithCar(
                contract.getCar(),
                contract.getDownPayment(),
                contract.getInterestRate(),
                contract.getDurationInMonths()
        );

        List<Payment> createdPayments = new ArrayList<>(contract.getDurationInMonths());
        for (int i = 0; i < contract.getDurationInMonths(); i++) {
            PaymentDto savedDtoList = this.save(
                    new PaymentDto(
                            new PaymentIdContractId(null, contract.getId()),
                            annuityPayment,
                            PaymentStatus.UNPAID
                    )
            );
            createdPayments.add(paymentMapper.toEntity(savedDtoList));
        }

        return Collections.unmodifiableList(createdPayments);
    }

    @Override
    public List<PaymentDto> findAll() {
        return paymentMapper.toDto(paymentRepository.findAllByOrderByPaymentIdContractId());
    }

    @Override
    public PaymentDto findByContractIdAndPaymentId(Long contractId, Long paymentId) {
        return paymentMapper.toDto(
                paymentRepository.findByPaymentIdContractId(new PaymentIdContractId(paymentId, contractId)).orElseThrow(() -> new NotFoundException(
                String.format("У договора номер %s не удалось найти платеж номер %s", contractId, paymentId)
        )));
    }

    @Override
    public List<PaymentDto> findByContractId(Long contractId) {
        return paymentMapper.toDto(paymentRepository.findByContractId(contractId));
    }

    @Override
    public List<PaymentDto> findByContractIdOrderedByPaymentId(Long contractId) {
        return paymentMapper.toDto(paymentRepository.findByContractIdOrderedByPaymentId(contractId));
    }

    @Override
    @Transactional
    public void deleteByContractIdAndPaymentId(PaymentIdContractId paymentIdContractId) {
        paymentRepository.deleteByPaymentIdContractId(paymentIdContractId);
    }

    @Override
    @Transactional
    public void deleteByContractId(Long contractId) {
        paymentRepository.deleteByContractId(contractId);
    }
}
