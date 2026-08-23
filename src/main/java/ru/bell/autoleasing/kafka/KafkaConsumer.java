package ru.bell.autoleasing.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import ru.bell.autoleasing.car.model.dto.CarMessage;
import ru.bell.autoleasing.car.service.CarService;
import ru.bell.autoleasing.client.model.dto.ClientMessage;
import ru.bell.autoleasing.client.service.ClientService;
import ru.bell.autoleasing.contract.model.dto.ContractCreateMessage;
import ru.bell.autoleasing.contract.model.dto.CreateContractDto;
import ru.bell.autoleasing.contract.service.ContractService;
import ru.bell.autoleasing.exceptions.DataCreationException;
import ru.bell.autoleasing.exceptions.DataUpdateException;
import ru.bell.autoleasing.exceptions.NotFoundException;
import ru.bell.autoleasing.exceptions.ValidationException;
import ru.bell.autoleasing.payment.model.dto.CreatePaymentDto;
import ru.bell.autoleasing.payment.model.dto.PaymentCreateMessage;
import ru.bell.autoleasing.utilities.annotations.DefaultRetryableTopic;

import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaConsumer {
    private static final String MESSAGE_RECEIVED = "Получено сообщение из брокера";
    private static final String MESSAGE_SUCCESS = "Сообщение брокера успешно обработано";
    private static final String MESSAGE_FAILURE = "При обработке сообщения брокера было получено обработанное исключение";

    private final CarService carService;
    private final ClientService clientService;
    private final ContractService contractService;

    @DefaultRetryableTopic
    @KafkaListener(topics = "${kafka.topic-names.cars}", groupId = "${spring.kafka.consumer.group-id}")
    public void listen(@Payload CarMessage carMessage, @Headers Map<String, Object> kafkaHeaders, Acknowledgment ack) {
        log.info("{}: {}. Заголовки Kafka: {}", MESSAGE_RECEIVED, carMessage, kafkaHeaders);

        Consumer<CarMessage> messageConsumer = message ->  {
            switch (message.getCrudOperation()) {
                case CREATE -> carService.save(message.getCarDto());
                case UPDATE -> carService.update(message.getCarDto());
                case DELETE -> {
                    contractService.deleteContractsByCarVin(carMessage.getCarDto().vin());
                    carService.deleteByVin(carMessage.getCarDto().vin());
                }
            }};

        defaultTryCatch(carMessage, messageConsumer, kafkaHeaders, ack);
    }

    @DefaultRetryableTopic
    @KafkaListener(topics = "${kafka.topic-names.clients}", groupId = "${spring.kafka.consumer.group-id}")
    public void listen(@Payload ClientMessage clientMessage, @Headers Map<String, Object> kafkaHeaders, Acknowledgment ack) {
        log.info("{}: {}. Заголовки Kafka: {}", MESSAGE_RECEIVED, clientMessage, kafkaHeaders);

        Consumer<ClientMessage> messageConsumer = message ->  {
            switch (message.getCrudOperation()) {
                case CREATE -> clientService.save(message.getClientDto());
                case UPDATE -> clientService.update(message.getClientDto());
                case DELETE -> {
                    if (message.getClientDto().passport() != null) {
                        contractService.deleteContractsByClientPassport(message.getClientDto().passport());
                        clientService.deleteByPassport(message.getClientDto().passport());
                    } else {
                        contractService.deleteContractsByClientId(message.getClientDto().id());
                        clientService.deleteById(message.getClientDto().id());
                    }
                }
            }};

        defaultTryCatch(clientMessage, messageConsumer, kafkaHeaders, ack);
    }

    @DefaultRetryableTopic
    @KafkaListener(topics = "${kafka.topic-names.contracts}", groupId = "${spring.kafka.consumer.group-id}")
    public void listen(@Payload ContractCreateMessage contractCreateMessage, @Headers Map<String, Object> kafkaHeaders, Acknowledgment ack) {
        log.info("{}: {}. Заголовки Kafka: {}", MESSAGE_RECEIVED, contractCreateMessage, kafkaHeaders);

        CreateContractDto contractDto = contractCreateMessage.getCreateContractDto();
        defaultTryCatch(contractDto, contractService::save, kafkaHeaders, ack);
    }

    @DefaultRetryableTopic
    @KafkaListener(topics = "${kafka.topic-names.payments}", groupId = "${spring.kafka.consumer.group-id}")
    public void listen(@Payload PaymentCreateMessage paymentCreateMessage, @Headers Map<String, Object> kafkaHeaders, Acknowledgment ack) {
        log.info("{}: {}. Заголовки Kafka: {}", MESSAGE_RECEIVED, paymentCreateMessage, kafkaHeaders);

        CreatePaymentDto createPaymentDto = paymentCreateMessage.getCreatePaymentDto();
        defaultTryCatch(createPaymentDto,
                dto -> contractService.updateNearestPaymentStatusToPaid(dto.contractId(), dto.amount()),
                kafkaHeaders,
                ack);
    }

    private <T> void defaultTryCatch(T message, Consumer<T> function, Map<String, Object> kafkaHeaders, Acknowledgment ack) {
        try {
            function.accept(message);
            ack.acknowledge();
            log.info("{}: {}. Заголовки Kafka: {}", MESSAGE_SUCCESS, message, kafkaHeaders);
        } catch (ValidationException | DataCreationException | DataUpdateException | NotFoundException e) {
            ack.acknowledge();
            log.info("{}: {}. Сообщение: {}. Заголовки Kafka: {}", MESSAGE_FAILURE, e.getMessage(), message, kafkaHeaders);
        }
    }
}
