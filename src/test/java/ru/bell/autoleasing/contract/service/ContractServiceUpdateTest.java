package ru.bell.autoleasing.contract.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.bell.autoleasing.car.model.Car;
import ru.bell.autoleasing.client.model.Client;
import ru.bell.autoleasing.contract.enums.ContractStatus;
import ru.bell.autoleasing.contract.model.Contract;
import ru.bell.autoleasing.contract.model.dto.ContractDto;
import ru.bell.autoleasing.contract.repository.InFileContractRepository;
import ru.bell.autoleasing.contract.repository.JpaContractRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test with mocked repositories: the update method also writes the legacy contracts file,
 * so it is not exercised against the real database here.
 */
class ContractServiceUpdateTest {

    private JpaContractRepository jpaContractRepository;
    private InFileContractRepository inFileContractRepository;
    private ContractServiceImpl service;

    @BeforeEach
    void setUp() {
        jpaContractRepository = mock(JpaContractRepository.class);
        inFileContractRepository = mock(InFileContractRepository.class);
        service = new ContractServiceImpl(inFileContractRepository, jpaContractRepository,
                null, null, null, null, null);
        when(jpaContractRepository.save(any(Contract.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(inFileContractRepository.update(any(Contract.class))).thenAnswer(invocation -> Optional.of(invocation.getArgument(0)));
    }

    @Test
    void updateKeepsStoredContractNumberAndStartDate() {
        Contract stored = new Contract();
        stored.setId(7L);
        stored.setContractNumber("LS-2026-0007");
        stored.setStartDate(LocalDate.of(2026, 1, 15));
        when(jpaContractRepository.findById(7L)).thenReturn(Optional.of(stored));

        service.update(dto(7L));

        ArgumentCaptor<Contract> saved = ArgumentCaptor.forClass(Contract.class);
        verify(jpaContractRepository).save(saved.capture());
        assertThat(saved.getValue().getContractNumber()).isEqualTo("LS-2026-0007");
        assertThat(saved.getValue().getStartDate()).isEqualTo(LocalDate.of(2026, 1, 15));
    }

    @Test
    void updateOfUnknownContractLeavesNumberAndStartDateEmpty() {
        when(jpaContractRepository.findById(8L)).thenReturn(Optional.empty());

        service.update(dto(8L));

        ArgumentCaptor<Contract> saved = ArgumentCaptor.forClass(Contract.class);
        verify(jpaContractRepository).save(saved.capture());
        assertThat(saved.getValue().getContractNumber()).isNull();
        assertThat(saved.getValue().getStartDate()).isNull();
    }

    private static ContractDto dto(Long id) {
        return new ContractDto(id, List.of(), new Car(), new Client(), 12,
                new BigDecimal("100000.00"), new BigDecimal("10.00"), ContractStatus.ACTIVE);
    }
}
