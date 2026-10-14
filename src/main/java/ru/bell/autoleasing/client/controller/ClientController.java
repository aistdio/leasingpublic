package ru.bell.autoleasing.client.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.bell.autoleasing.client.model.dto.ClientDto;
import ru.bell.autoleasing.client.model.dto.ClientMessage;
import ru.bell.autoleasing.client.service.ClientService;
import ru.bell.autoleasing.kafka.KafkaProducerService;
import ru.bell.autoleasing.utilities.broker.CrudOperation;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/clients")
@AllArgsConstructor
public class ClientController {
    private final ClientService clientService;
    private final KafkaProducerService producerService;

    @PostMapping
    public ResponseEntity<Void> save(@RequestBody @Valid ClientDto clientDto) {
        log.info("Сохранение клиента {}", clientDto);

        ClientMessage clientMessage = new ClientMessage(CrudOperation.CREATE, clientDto);
        producerService.sendMessage(clientMessage);

        log.info("Запрос на сохранение клиента отправлен в брокер: {}", clientMessage);
        return ResponseEntity.accepted().build();
    }

    @PutMapping
    public ResponseEntity<Void> update(@RequestBody @Valid ClientDto clientDto) {
        log.info("Обновление клиента {}", clientDto);

        ClientMessage clientMessage = new ClientMessage(CrudOperation.UPDATE, clientDto);
        producerService.sendMessage(clientMessage);

        log.info("Запрос на обновление клиента отправлен в брокер: {}", clientMessage);
        return ResponseEntity.accepted().build();
    }

    @GetMapping
    public ResponseEntity<List<ClientDto>> getAllClients() {
        log.info("Получение всех клиентов");
        List<ClientDto> dtoList = clientService.findAll();
        log.info("Все клиенты получены (количество: {})", dtoList.size());
        return ResponseEntity.ok(dtoList);
    }

    @GetMapping("/search")
    public ResponseEntity<List<ClientDto>> getAllClientsByNamePassportPhone(@RequestParam("value") @NotBlank String value) {
        log.info("Получение всех клиентов по запросу: {}", value);
        List<ClientDto> dtoList = clientService.findByOptionalCriteria(value);
        log.info("Получено клиентов в количестве {} по запросу {}", dtoList.size(), value);
        return ResponseEntity.ok(dtoList);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClientDto> getClientById(@PathVariable("id") Long id) {
        log.info("Получение клиента с paymentId {}", id);
        ClientDto clientDto = clientService.findById(id);
        log.info("Клиент с paymentId {} получен", id);
        return ResponseEntity.ok(clientDto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteClientById(@PathVariable("id") Long id) {
        log.info("Удаление клиента с paymentId {}", id);

        ClientDto clientDto = new ClientDto(id, null, null, null, null, null, null);
        ClientMessage clientMessage = new ClientMessage(CrudOperation.DELETE, clientDto);
        producerService.sendMessage(clientMessage);

        log.info("Запрос на удаление клиента отправлен в брокер: {}", clientMessage);
        return ResponseEntity.accepted().build();
    }

    @DeleteMapping("/passport/{passport}")
    public ResponseEntity<Void> deleteClientByPassport(@PathVariable("passport") String passport) {
        log.info("Удаление клиента с паспортом {}", passport);

        ClientDto clientDto = new ClientDto(null, null, passport, null, null, null, null);
        ClientMessage clientMessage = new ClientMessage(CrudOperation.DELETE, clientDto);
        producerService.sendMessage(clientMessage);

        log.info("Запрос на удаление клиента отправлен в брокер: {}", clientMessage);
        return ResponseEntity.accepted().build();
    }
}
