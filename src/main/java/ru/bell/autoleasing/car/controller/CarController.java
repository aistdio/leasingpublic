package ru.bell.autoleasing.car.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.bell.autoleasing.car.enums.CarStatus;
import ru.bell.autoleasing.car.model.dto.CarDto;
import ru.bell.autoleasing.car.model.dto.CarMessage;
import ru.bell.autoleasing.car.service.CarService;
import ru.bell.autoleasing.kafka.KafkaProducerService;
import ru.bell.autoleasing.utilities.broker.CrudOperation;
import ru.bell.autoleasing.utilities.validation.DataValidator;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/cars")
@AllArgsConstructor
public class CarController {
    private final CarService carService;
    private final KafkaProducerService producerService;

    @PostMapping
    public ResponseEntity<Void> save(@RequestBody @Valid CarDto carDto) {
        log.info("Сохранение автомобиля {}", carDto);
        CarDto cleanedCarDto = validateFieldsAndNormalizeCar(carDto);

        CarMessage carMessage = new CarMessage(CrudOperation.CREATE, cleanedCarDto);
        producerService.sendMessage(carMessage);

        log.info("Запрос на сохранение автомобиля отправлен в брокер: {}", carMessage);
        return ResponseEntity.accepted().build();
    }

    @PutMapping
    public ResponseEntity<Void> update(@RequestBody @Valid CarDto carDto) {
        log.info("Обновление автомобиля {}", carDto);
        CarDto cleanedCarDto = validateFieldsAndNormalizeCar(carDto);

        CarMessage carMessage = new CarMessage(CrudOperation.UPDATE, cleanedCarDto);
        producerService.sendMessage(carMessage);

        log.info("Запрос на обновление автомобиля отправлен в брокер: {}", carMessage);
        return ResponseEntity.accepted().build();
    }

    @PatchMapping("/{vin}/available")
    public ResponseEntity<CarDto> updateStatusAvailable(@PathVariable @NotBlank String vin) {
        log.info("Обновление статуса автомобиля с VIN {} на '{}'", vin, CarStatus.AVAILABLE);
        CarDto updated = carService.setStatusAvailable(DataValidator.validateAndNormalizeVin(vin));
        log.info("Статус автомобиль с VIN {} обновлен на '{}'", vin, CarStatus.AVAILABLE);
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{vin}/leased")
    public ResponseEntity<CarDto> updateStatusLeased(@PathVariable @NotBlank String vin) {
        log.info("Обновление статуса автомобиля с VIN {} на '{}'", vin, CarStatus.LEASED);
        CarDto updated = carService.setStatusLeased(DataValidator.validateAndNormalizeVin(vin));
        log.info("Статус автомобиль с VIN {} обновлен на '{}'", vin, CarStatus.LEASED);
        return ResponseEntity.ok(updated);
    }

    @GetMapping
    public ResponseEntity<List<CarDto>> getAllCars() {
        log.info("Получение всех автомобилей");
        List<CarDto> dtoList = carService.findAll();
        log.info("Все автомобили получены ({} автомобилей)", dtoList.size());
        return ResponseEntity.ok(dtoList);
    }

    @GetMapping("/search")
    public ResponseEntity<List<CarDto>> getAllCarsByBrandModelYear(@RequestBody CarDto carDto) {
        log.info("Получение всех автомобилей по критериям: {}", carDto);
        List<CarDto> dtoList = carService.findByOptionalCriteria(carDto);
        log.info("Получено {} автомобилей с критериями {}", dtoList.size(), carDto);
        return ResponseEntity.ok(dtoList);
    }

    @GetMapping("/available")
    public ResponseEntity<List<CarDto>> getAllCarsAvailable() {
        log.info("Получение всех доступных автомобилей");
        List<CarDto> dtoList = carService.findAvailable();
        log.info("Получено {} доступных автомобилей", dtoList.size());
        return ResponseEntity.ok(dtoList);
    }

    @GetMapping("/{vin}")
    public ResponseEntity<CarDto> getCarByVin(@PathVariable("vin") String vin) {
        log.info("Получение автомобиля с vin {}", vin);
        CarDto carDto = carService.findByVin(DataValidator.validateAndNormalizeVin(vin));
        log.info("Автомобиль с vin {} получен", vin);
        return ResponseEntity.ok(carDto);
    }

    @DeleteMapping("/{vin}")
    public ResponseEntity<Void> deleteCarByVin(@PathVariable("vin") String vin) {
        log.info("Удаление автомобиля с vin {}", vin);
        String vinCleaned = DataValidator.validateAndNormalizeVin(vin);

        CarDto carDto = new CarDto(vinCleaned, null, null, null, null, null);

        CarMessage carMessage = new CarMessage(CrudOperation.DELETE, carDto);
        producerService.sendMessage(carMessage);

        log.info("Запрос на удаление автомобиля отправлен в брокер: {}", carMessage);
        return ResponseEntity.accepted().build();
    }

    private CarDto validateFieldsAndNormalizeCar(CarDto carRaw) {
        DataValidator.validateReleaseYear(carRaw.releaseYear());
        DataValidator.validatePrice(carRaw.price());

        String vin = DataValidator.validateAndNormalizeVin(carRaw.vin());
        String brand = DataValidator.validateAndNormalizeUppercase(carRaw.brand(), "Марка");
        String model = DataValidator.validateAndNormalizeUppercase(carRaw.model(), "Модель");

        return new CarDto(
                vin,
                brand,
                model,
                carRaw.releaseYear(),
                carRaw.price(),
                carRaw.status()
        );
    }
}
