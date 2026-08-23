package ru.bell.autoleasing.payment.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.bell.autoleasing.contract.service.ContractService;
import ru.bell.autoleasing.kafka.KafkaProducerService;
import ru.bell.autoleasing.payment.model.dto.CreatePaymentDto;
import ru.bell.autoleasing.payment.model.dto.PaymentCreateMessage;
import ru.bell.autoleasing.payment.model.dto.PaymentDto;
import ru.bell.autoleasing.payment.service.PaymentService;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/payments")
@AllArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;
    private final ContractService contractService;
    private final KafkaProducerService producerService;

    @PostMapping
    public ResponseEntity<PaymentDto> registerPayment(@RequestBody @Valid CreatePaymentDto createPaymentDto) {
        log.info("Регистрация платежа {}", createPaymentDto);

        PaymentCreateMessage paymentCreateMessage = new PaymentCreateMessage(createPaymentDto);
        producerService.sendMessage(paymentCreateMessage);

        log.info("Запрос на регистрацию платежа отправлен в брокер: {}", paymentCreateMessage);
        return ResponseEntity.accepted().build();
    }

    @GetMapping
    public ResponseEntity<List<PaymentDto>> getAllPayments() {
        log.info("Получение всех платежей");
        List<PaymentDto> dtoList = paymentService.findAll();
        log.info("Все платежи получены (количество: {})", dtoList.size());
        return ResponseEntity.ok(dtoList);
    }

    @GetMapping("/contract/{number}")
    public ResponseEntity<List<PaymentDto>> getPaymentsByContractId(@PathVariable("number") Long contractId) {
        log.info("Получение платежей с номером договора {}", contractId);
        List<PaymentDto> paymentsDto = paymentService.findByContractId(contractId);
        log.info("Платежи с номером договора {} получены", contractId);
        return ResponseEntity.ok(paymentsDto);
    }

    @DeleteMapping("/contract/{contractId}/payment/{paymentId}")
    public ResponseEntity<Void> deleteByContractIdAndPaymentId(@PathVariable("contractId") @NotNull Long contractId, @PathVariable("paymentId") Long paymentId) {
        log.info("Удаление у договора с номером {} платежа с номером {}", contractId, paymentId);
        contractService.deletePaymentByContractIdAndPaymentId(contractId, paymentId);
        log.info("У договора с номером {} удален платеж с номером {}", contractId, paymentId);
        return ResponseEntity.ok().build();
    }
}
