package ru.bell.autoleasing.contract.repository;

import ru.bell.autoleasing.client.model.Client;
import ru.bell.autoleasing.contract.enums.ContractStatus;
import ru.bell.autoleasing.contract.model.Contract;
import ru.bell.autoleasing.payment.enums.PaymentStatus;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;

public interface InFileContractRepository {
    Optional<Contract> save(Contract contract);

    Optional<Contract> update(Contract contract);

    Optional<Contract> updatePaymentStatus(Long contractId, Long paymentId, PaymentStatus paymentStatus);

    List<Contract> findAll();

    Set<Contract> findAllAsSet();

    Optional<Contract> findById(Long id);

    List<Contract> findByClient(Client client);

    List<Contract> findByStatus(ContractStatus status);

    void deleteById(Long id);

    Future<?> updateCacheFromFile();

    boolean isFileReady();

    void updateFileFromCache(ConcurrentMap<Long, Contract> srcCache);
}
