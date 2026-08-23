package ru.bell.autoleasing.car.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.hibernate.validator.constraints.Range;
import ru.bell.autoleasing.car.enums.CarStatus;

import java.math.BigDecimal;

public record CarDto (
        @NotNull
        String vin,
        @NotBlank
        String brand,
        @NotBlank
        String model,
        @Range(min = 2000L, max = 2025L)
        Integer releaseYear,
        @Positive
        BigDecimal price,
        CarStatus status
) {}