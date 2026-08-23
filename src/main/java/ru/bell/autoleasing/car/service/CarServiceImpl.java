package ru.bell.autoleasing.car.service;

import lombok.AllArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.bell.autoleasing.car.enums.CarStatus;
import ru.bell.autoleasing.car.mapper.CarMapper;
import ru.bell.autoleasing.car.model.Car;
import ru.bell.autoleasing.car.model.dto.CarDto;
import ru.bell.autoleasing.car.repository.CarRepository;
import ru.bell.autoleasing.exceptions.DataCreationException;
import ru.bell.autoleasing.exceptions.DataUpdateException;
import ru.bell.autoleasing.exceptions.NotFoundException;
import ru.bell.autoleasing.exceptions.ValidationException;
import ru.bell.autoleasing.utilities.annotations.DefaultRetryable;

import java.util.List;

@Service
@AllArgsConstructor
public class CarServiceImpl implements CarService {
    private final CarRepository carRepository;
    private final CarMapper carMapper;

    @Override
    public CarDto save(CarDto carDto) {
        Car car = carMapper.toEntity(carDto);
        this.validateCarUniqueness(car.getVin());

        if (car.getStatus() == null) {
            car.setStatus(CarStatus.AVAILABLE);
        }

        try {
            car = carRepository.save(car);
        } catch (DataIntegrityViolationException e) {
            throw new DataCreationException(String.format("Автомобиль с VIN %s ранее уже был сохранен.", car.getVin()));
        }

        return carMapper.toDto(car);
    }

    @Override
    @Transactional
    @DefaultRetryable(retryFor = ObjectOptimisticLockingFailureException.class)
    public CarDto update(CarDto carDto) {
        Car car = carMapper.toEntity(carDto);

        Car carManaged = carRepository.findByVin(car.getVin()).orElseThrow(() ->
                new DataUpdateException(String.format("Автомобиль с VIN %s не был найден.", car.getVin())));

        carManaged.setAll(car);

        return carMapper.toDto(carManaged);
    }

    @Override
    @Transactional
    @DefaultRetryable(retryFor = ObjectOptimisticLockingFailureException.class)
    public CarDto setStatusLeased(String vin) {
        Car car = carRepository.findByVin(vin).orElseThrow(() ->
                new DataUpdateException(String.format("При обновлении статуса автомобиля не был найден соответствующий VIN %s.", vin)));

        car.setStatus(CarStatus.LEASED);

        return carMapper.toDto(car);
    }

    @Override
    @Transactional
    @DefaultRetryable(retryFor = ObjectOptimisticLockingFailureException.class)
    public CarDto setStatusAvailable(String vin) {
        Car car = carRepository.findByVin(vin).orElseThrow(() ->
                new DataUpdateException(String.format("При обновлении статуса автомобиля не был найден соответствующий VIN %s.", vin)));

        car.setStatus(CarStatus.AVAILABLE);

        return carMapper.toDto(car);
    }

    @Override
    public List<CarDto> findAll() {
        return carMapper.toDto(carRepository.findAll());
    }

    @Override
    public CarDto findByVin(String vin) {
        return carMapper.toDto(carRepository.findByVin(vin)
                .orElseThrow(() -> new NotFoundException(
                        "Автомобиль с VIN " + vin + " не был найден.")));
    }

    @Override
    public Car findByVinInternalWithReadLock(String vin) {
        return carRepository.findWithReadLockByVin(vin)
                .orElseThrow(() -> new NotFoundException(
                        "Автомобиль с VIN " + vin + " не был найден."));
    }

    @Override
    public List<CarDto> findByOptionalCriteria(CarDto carDto) {
        if (carDto.brand() == null && carDto.model() == null && carDto.releaseYear() == null) {
            throw new ValidationException("Хотя бы одно из полей должно быть заполнено (brand, model, releaseYear)");
        }
        List<Car> carList = carRepository.findByBrandAndModelAndReleaseYear(carDto.brand(), carDto.model(), carDto.releaseYear());
        return carMapper.toDto(carList);
    }

    @Override
    public List<CarDto> findAvailable() {
        return findByStatus(CarStatus.AVAILABLE);
    }

    @Override
    public List<CarDto> findByStatus(CarStatus status) {
        return carMapper.toDto(carRepository.findByStatus(status));
    }

    @Override
    @Transactional
    @DefaultRetryable(retryFor = ObjectOptimisticLockingFailureException.class)
    public void deleteByVin(String vin) {
        if (carRepository.findByVin(vin).isEmpty()) {
            throw new DataUpdateException("Автомобиль с VIN " + vin + " не был найден.");
        }
        carRepository.deleteByVin(vin);
    }

    private void validateCarUniqueness(String vin) {
        if (carRepository.findByVin(vin).isPresent()) {
            throw new DataCreationException(String.format("Автомобиль с VIN %s ранее уже был сохранен.", vin));
        }
    }
}
