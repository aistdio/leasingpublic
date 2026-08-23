package ru.bell.autoleasing.payment.repository;

import jakarta.persistence.LockModeType;
import jakarta.validation.Valid;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.bell.autoleasing.common.repositories.InsertableRepository;
import ru.bell.autoleasing.payment.enums.PaymentStatus;
import ru.bell.autoleasing.payment.model.Payment;
import ru.bell.autoleasing.payment.model.PaymentIdContractId;
import ru.bell.autoleasing.utilities.annotations.CacheHints;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends InsertableRepository<Payment>, JpaRepository<Payment, PaymentIdContractId> {
    @Modifying
    @Query("update Payment p set p.status = :status where p.paymentIdContractId = :paymentIdContractId")
    int updateStatus(PaymentStatus status, PaymentIdContractId paymentIdContractId);

    @CacheHints
    @NotNull List<Payment> findAll();

    @CacheHints
    List<Payment> findAllByOrderByPaymentIdContractId();

    @Transactional(propagation = Propagation.MANDATORY)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p.paymentIdContractId.paymentId FROM Payment p " +
            "WHERE p.paymentIdContractId.contractId = :contractId " +
            "ORDER BY p.paymentIdContractId.paymentId DESC LIMIT 1")
    Optional<Long> findLatestPaymentIdByContractIdWithPessimisticWriteLock(Long contractId);

    @CacheHints
    Optional<Payment> findByPaymentIdContractId(@Valid PaymentIdContractId paymentIdContractId);

    @CacheHints
    @Query("select p from Payment p where p.paymentIdContractId.contractId = :contractId")
    List<Payment> findByContractId(Long contractId);

    @CacheHints
    @Query("select p from Payment p where p.paymentIdContractId.contractId = :contractId order by p.paymentIdContractId.paymentId")
    List<Payment> findByContractIdOrderedByPaymentId(Long contractId);

    void deleteByPaymentIdContractId(@Valid PaymentIdContractId paymentIdContractId);

    @Modifying
    @Query("delete from Payment p where p.paymentIdContractId.contractId = :contractId")
    void deleteByContractId(Long contractId);
}
