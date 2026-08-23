package ru.bell.autoleasing.car.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import ru.bell.autoleasing.car.model.Car;
import ru.bell.autoleasing.car.model.dto.CarDto;
import ru.bell.autoleasing.utilities.mapper.EntityMapper;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface CarMapper extends EntityMapper<CarDto, Car> {
}