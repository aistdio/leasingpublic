package ru.bell.autoleasing.contract.service;

import ru.bell.autoleasing.client.model.Client;
import ru.bell.autoleasing.contract.enums.ContractStatus;
import ru.bell.autoleasing.contract.model.Contract;
import ru.bell.autoleasing.contract.model.dto.ContractDto;
import ru.bell.autoleasing.contract.model.dto.CreateContractDto;
import ru.bell.autoleasing.payment.model.dto.PaymentDto;
import ru.bell.autoleasing.utilities.PriorityPair;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;

public interface ContractService {
    ContractDto save(CreateContractDto contractDto);

    ContractDto update(ContractDto contractDto);

    PaymentDto updateNearestPaymentStatusToPaid(Long contractId, BigDecimal paidAmount);

    void deleteContract(Long contractId);

    void deleteContractsByCarVin(String vin);

    void deleteContractsByClientId(Long clientId);

    void deleteContractsByClientPassport(String passport);

    void closeContract(Long contractId);

    PriorityPair<Set<Contract>> findAllInDbAndFileRepositories();

    List<ContractDto> findAll();

    List<ContractDto> findByStatus(ContractStatus status);

    ContractDto findById(Long id);

    List<ContractDto> findByClient(Client client);

    void deletePaymentByContractIdAndPaymentId(Long contractId, Long paymentId);

    Future<?> updateRepositoryCache();

    boolean isFilesReady();

    void updateFileFromConcurrentMap(ConcurrentMap<Long, Contract> map);
}
