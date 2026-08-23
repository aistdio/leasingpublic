package ru.bell.autoleasing.contract.model.mapper;

import ru.bell.autoleasing.contract.model.Contract;
import ru.bell.autoleasing.contract.model.dto.ContractDto;

public class ContractMapper {
    public static ContractDto toDto(Contract contract) {
        return new ContractDto(
                contract.getId(),
                contract.getPayments(),
                contract.getCar(),
                contract.getClient(),
                contract.getDurationInMonths(),
                contract.getDownPayment(),
                contract.getInterestRate(),
                contract.getStatus()
        );
    }

    public static Contract toEntity(ContractDto dto) {
        return new Contract(
                dto.id(),
                dto.payments(),
                dto.car(),
                dto.client(),
                dto.durationInMonths(),
                dto.downPayment(),
                dto.interestRate(),
                dto.payments() != null && !dto.payments().isEmpty() ? dto.payments().getFirst().getAmount() : null,
                dto.status()
        );
    }
}
