package ru.bell.autoleasing.contract.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import ru.bell.autoleasing.contract.enums.ContractStatus;
import ru.bell.autoleasing.contract.model.Contract;
import ru.bell.autoleasing.utilities.annotations.CacheHints;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface JpaContractRepository extends JpaRepository<Contract, Long> {
    @Modifying
    @Query("update Contract c " +
            "set c.status = :status " +
            "where c.id = :contractId")
    int updateStatus(ContractStatus status, Long contractId);

    @CacheHints
    @Query("select c from Contract c " +
            "left join fetch c.payments " +
            "where c.id = :contractId")
    Optional<Contract> findByIdWithPayments(Long contractId);

    @CacheHints
    @Query("select c from Contract c " +
            "left join fetch c.car " +
            "where c.id = :contractId")
    Optional<Contract> findByIdWithCar(Long contractId);

    @CacheHints
    @Query("select c from Contract c " +
            "left join fetch c.car " +
            "left join fetch c.payments " +
            "where c.id = :contractId")
    Optional<Contract> findByIdWithCarAndPayments(Long contractId);

    @CacheHints
    @Query("select c from Contract c " +
            "left join fetch c.client " +
            "left join fetch c.car " +
            "left join fetch c.payments " +
            "where c.id = :contractId")
    Optional<Contract> findByIdEager(Long contractId);

    @CacheHints
    @NotNull List<Contract> findAll();

    @CacheHints
    @Query("select c from Contract c " +
            "left join fetch c.client " +
            "left join fetch c.car " +
            "left join fetch c.payments " +
            "order by c.id")
    List<Contract> findAllEager();

    @CacheHints
    @Query("select c from Contract c " +
            "left join fetch c.client " +
            "left join fetch c.car " +
            "left join fetch c.payments " +
            "order by c.id")
    Set<Contract> findAllEagerAsSet();

    @CacheHints
    @Query("select c from Contract c " +
            "left join fetch c.client " +
            "left join fetch c.car " +
            "left join fetch c.payments " +
            "where c.status = :status " +
            "order by c.id")
    List<Contract> findByStatusEager(ContractStatus status);

    @CacheHints
    @Query("select c from Contract c " +
            "left join fetch c.client " +
            "left join fetch c.car " +
            "left join fetch c.payments " +
            "where c.client.id = :clientId " +
            "order by c.id")
    List<Contract> findByClientIdEager(Long clientId);

    @CacheHints
    @Query("select c from Contract c " +
            "left join fetch c.client " +
            "left join fetch c.car " +
            "where c.client.passport = :passport " +
            "order by c.id")
    List<Contract> findByClientPassportWithCar(String passport);

    @CacheHints
    @Query("select c from Contract c " +
            "left join fetch c.car " +
            "where c.car.vin = :vin " +
            "order by c.id")
    List<Contract> findByCarVinWithCar(String vin);

    void deleteById(@NotNull Long id);
}
