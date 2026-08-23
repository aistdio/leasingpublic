package ru.bell.autoleasing.payment.model.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import ru.bell.autoleasing.payment.model.Payment;
import ru.bell.autoleasing.payment.model.dto.PaymentDto;
import ru.bell.autoleasing.utilities.mapper.EntityMapper;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface PaymentMapper extends EntityMapper<PaymentDto, Payment> {
}