package ru.bell.autoleasing.car.service;

import ru.bell.autoleasing.car.enums.CarStatus;
import ru.bell.autoleasing.car.model.Car;
import ru.bell.autoleasing.car.model.dto.CarDto;

import java.util.List;

public interface CarService {
    CarDto save(CarDto car);

    CarDto update(CarDto car);

    CarDto setStatusLeased(String vin);

    CarDto setStatusAvailable(String vin);

    List<CarDto> findAll();

    CarDto findByVin(String vin);

    Car findByVinInternalWithReadLock(String vin);

    List<CarDto> findByOptionalCriteria(CarDto carDto);

    List<CarDto> findAvailable();

    List<CarDto> findByStatus(CarStatus status);

    void deleteByVin(String vin);
}
