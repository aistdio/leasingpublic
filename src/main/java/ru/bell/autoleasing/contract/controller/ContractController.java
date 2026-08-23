package ru.bell.autoleasing.contract.controller;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.bell.autoleasing.client.model.Client;
import ru.bell.autoleasing.client.model.mapper.ClientMapper;
import ru.bell.autoleasing.client.service.ClientService;
import ru.bell.autoleasing.contract.enums.ContractStatus;
import ru.bell.autoleasing.contract.model.dto.ContractCreateMessage;
import ru.bell.autoleasing.contract.model.dto.ContractDto;
import ru.bell.autoleasing.contract.model.dto.CreateContractDto;
import ru.bell.autoleasing.contract.service.ContractService;
import ru.bell.autoleasing.kafka.KafkaProducerService;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/contracts")
@AllArgsConstructor
public class ContractController {
    private final ClientMapper clientMapper;
    private final ContractService contractService;
    private final ClientService clientService;
    private final KafkaProducerService producerService;

    @PostMapping
    public ResponseEntity<ContractDto> save(@RequestBody @Valid CreateContractDto createContractDto) {
        log.info("Сохранение договора {}", createContractDto);

        ContractCreateMessage contractCreateMessage = new ContractCreateMessage(createContractDto);
        producerService.sendMessage(contractCreateMessage);

        log.info("Запрос на сохранение договора отправлен в брокер: {}", contractCreateMessage);
        return ResponseEntity.accepted().build();
    }

    @GetMapping
    public ResponseEntity<List<ContractDto>> getAllContracts() {
        log.info("Получение всех договоров");
        List<ContractDto> contractDtoList = contractService.findAll();
        log.info("Все договоры получены (количество: {})", contractDtoList.size());
        return ResponseEntity.ok(contractDtoList);
    }

    @GetMapping("/active")
    public ResponseEntity<List<ContractDto>> getActiveContracts() {
        log.info("Получение всех активных договоров");
        List<ContractDto> contractDtoList = contractService.findByStatus(ContractStatus.ACTIVE);
        log.info("Все активные договоры получены (количество: {})", contractDtoList.size());
        return ResponseEntity.ok(contractDtoList);
    }

    @GetMapping("/client/{clientId}")
    public ResponseEntity<List<ContractDto>> getContractsByClientId(@PathVariable("clientId") Long clientId) {
        log.info("Получение всех договоров клиента с id {}", clientId);
        Client client = clientMapper.toEntity(clientService.findById(clientId));
        List<ContractDto> contractDtoList = contractService.findByClient(client);
        log.info("Все договоры клиента с id {} получены (количество: {})", clientId, contractDtoList.size());
        return ResponseEntity.ok(contractDtoList);
    }

    @PatchMapping("/{number}/close")
    public ResponseEntity<Void> closeContract(@PathVariable("number") Long contractId) {
        log.info("Закрытие договора с id {}", contractId);
        contractService.closeContract(contractId);
        log.info("Договор с id {} закрыт", contractId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{number}/delete")
    public ResponseEntity<Void> deleteContract(@PathVariable("number") Long contractId) {
        log.info("Удаление договора с id {}", contractId);
        contractService.deleteContract(contractId);
        log.info("Договор с id {} удален", contractId);
        return ResponseEntity.ok().build();
    }
}
