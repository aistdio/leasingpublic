package ru.bell.autoleasing.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import ru.bell.autoleasing.car.model.dto.CarMessage;
import ru.bell.autoleasing.client.model.dto.ClientMessage;
import ru.bell.autoleasing.contract.model.dto.ContractCreateMessage;
import ru.bell.autoleasing.payment.model.dto.PaymentCreateMessage;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaProducerService {

    private final KafkaConfigData kafkaConfigData;

    private final KafkaTemplate<String, CarMessage> kafkaTemplateCar;
    private final KafkaTemplate<String, ClientMessage> kafkaTemplateClient;
    private final KafkaTemplate<Void, ContractCreateMessage> kafkaTemplateContractCreate;
    private final KafkaTemplate<Void, PaymentCreateMessage> kafkaTemplatePaymentCreate;

    public void sendMessage(CarMessage carMessage) {
        CompletableFuture<SendResult<String, CarMessage>> future =
                kafkaTemplateCar.send(kafkaConfigData.getCarsTopic(), carMessage.getCarDto().vin(), carMessage);

        setDefaultFeatureCallback(kafkaConfigData.getCarsTopic(), future);
    }

    public void sendMessage(ClientMessage clientMessage) {
        CompletableFuture<SendResult<String, ClientMessage>> future =
                kafkaTemplateClient.send(kafkaConfigData.getClientsTopic(), clientMessage.getClientDto().passport(), clientMessage);

        setDefaultFeatureCallback(kafkaConfigData.getClientsTopic(), future);
    }

    public void sendMessage(ContractCreateMessage contractCreateMessage) {
        CompletableFuture<SendResult<Void, ContractCreateMessage>> future =
                kafkaTemplateContractCreate.send(kafkaConfigData.getContractsTopic(), contractCreateMessage);

        setDefaultFeatureCallback(kafkaConfigData.getContractsTopic(), future);
    }

    public void sendMessage(PaymentCreateMessage paymentCreateMessage) {
        CompletableFuture<SendResult<Void, PaymentCreateMessage>> future =
                kafkaTemplatePaymentCreate.send(kafkaConfigData.getPaymentsTopic(), paymentCreateMessage);

        setDefaultFeatureCallback(kafkaConfigData.getPaymentsTopic(), future);
    }

    private <K, V> void setDefaultFeatureCallback(String topicName, CompletableFuture<SendResult<K, V>> future) {
        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.warn("Ошибка отправки сообщения в топик {}: {}.", topicName, ex.getMessage());
            } else {
                log.debug("Сообщение отправлено в топик {}: {}.", topicName, result.getRecordMetadata().offset());
            }
        });
    }
}
