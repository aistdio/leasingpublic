package ru.bell.autoleasing.contract.repository;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Repository;
import ru.bell.autoleasing.client.model.Client;
import ru.bell.autoleasing.contract.enums.ContractStatus;
import ru.bell.autoleasing.contract.model.Contract;
import ru.bell.autoleasing.payment.enums.PaymentStatus;
import ru.bell.autoleasing.payment.model.Payment;
import ru.bell.autoleasing.utilities.files.FileManager;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;

@Repository
public class InFileContractRepositoryImpl implements InFileContractRepository {
    private static final String FILE_PATH = "data/contracts.txt";
    private final ConcurrentHashMap<Long, Contract> cache;

    public InFileContractRepositoryImpl() {
        cache = new ConcurrentHashMap<>();
    }

    @PostConstruct
    public void init() {
        FileManager.ensureFileExists(FILE_PATH);
    }

    @Override
    public Optional<Contract> save(Contract contract) {
        cache.put(contract.getId(), contract);
        updateFileFromCache(cache);
        return Optional.of(contract);
    }

    @Override
    public Optional<Contract> update(Contract contract) {
        Contract updated = cache.replace(contract.getId(), contract);
        if (updated != null) {
            updateFileFromCache(cache);
            return Optional.of(contract);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Contract> updatePaymentStatus(Long contractId, Long paymentId, PaymentStatus paymentStatus) {
        Optional<Contract> updated = Optional.ofNullable(cache.compute(contractId, (id, existingContract) -> {
            if (existingContract == null) {
                return null;
            }
            Optional<Payment> payment = existingContract.getPayments().stream()
                    .filter(p -> p.getPaymentIdContractId().getPaymentId().equals(paymentId))
                    .findFirst();
            if (payment.isEmpty()) {
                return null;
            }
            payment.get().setStatus(paymentStatus);

            return existingContract;
        }));

        if (updated.isPresent()) {
            updateFileFromCache(cache);
        }
        return updated;
    }

    @Override
    public List<Contract> findAll() {
        return cache.values().stream().toList();
    }

    @Override
    public Set<Contract> findAllAsSet() {
        return new HashSet<>(cache.values());
    }

    @Override
    public Optional<Contract> findById(Long id) {
        return Optional.ofNullable(cache.get(id));
    }

    @Override
    public List<Contract> findByClient(Client client) {
        return cache.values().stream()
                .filter((contract) -> contract.getClient().equals(client))
                .toList();
    }

    @Override
    public List<Contract> findByStatus(ContractStatus status) {
        return cache.values().stream()
                .filter(contract -> contract.getStatus().equals(status))
                .toList();
    }

    @Override
    public void deleteById(Long id) {
        if (cache.remove(id) != null) {
            updateFileFromCache(cache);
        }
    }

    @Override
    public Future<?> updateCacheFromFile() {
        return FileManager.updateMapFromFileInBackground(FILE_PATH, cache, Contract::fromFileString, Contract::getId);
    }

    @Override
    public boolean isFileReady() {
        return FileManager.isFileReady(FILE_PATH);
    }

    @Override
    public void updateFileFromCache(ConcurrentMap<Long, Contract> srcCache) {
        String header = "# Номер договора|||" +
                "Номер платежа|Номер договора лизинга|Сумма платежа|Статус платежа|||" +
                "VIN|Марка|Модель|Год выпуска|Стоимость|Статус автомобиля|||" +
                "ID клиента|ФИО|Паспорт|Телефон|||" +
                "Срок (мес.)|||" +
                "Первоначальный взнос|||" +
                "Процентная ставка|||" +
                "Аннуитетный платеж|||" +
                "Статус договора";
        FileManager.updateFileInBackground(FILE_PATH, header, srcCache.values().stream().toList(), Contract::toFileString);
    }
}