package ru.bell.autoleasing.car.repository;

import jakarta.persistence.LockModeType;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.bell.autoleasing.car.enums.CarStatus;
import ru.bell.autoleasing.car.model.Car;
import ru.bell.autoleasing.common.repositories.InsertableRepository;
import ru.bell.autoleasing.utilities.annotations.CacheHints;

import java.util.List;
import java.util.Optional;

@Repository
public interface CarRepository extends InsertableRepository<Car>, JpaRepository<Car, String> {
    @CacheHints
    @NotNull List<Car> findAll();

    @CacheHints
    Optional<Car> findByVin(String vin);

    @Lock(LockModeType.PESSIMISTIC_READ)
    Optional<Car> findWithReadLockByVin(String vin);

    @CacheHints
    @Query("select c from Car c " +
            "where (:brand is null or c.brand = :brand) " +
            "and (:model is null or c.model = :model) " +
            "and (:releaseYear is null or c.releaseYear = :releaseYear)")
    List<Car> findByBrandAndModelAndReleaseYear(String brand, String model, Integer releaseYear);

    @CacheHints
    List<Car> findByStatus(CarStatus status);

    void deleteByVin(String vin);
}
