package ru.bell.autoleasing.threads.schedulers.contract;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.bell.autoleasing.contract.model.Contract;
import ru.bell.autoleasing.contract.service.ContractService;
import ru.bell.autoleasing.utilities.PriorityPair;

import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@AllArgsConstructor
public class DatabaseConsistencyScheduler {
    private final ContractService contractService;

    @Scheduled(fixedDelay = 2, timeUnit = TimeUnit.MINUTES)
    public void compareDatabaseToFileForContracts() {
        log.info("Начало согласования файла договоров с базой данных");

        PriorityPair<Set<Contract>> pair = contractService.findAllInDbAndFileRepositories();

        Set<Contract> jpaSet = pair.getPrimary();
        Set<Contract> inFileSet = pair.getSecondary();

        if (!jpaSet.equals(inFileSet)) {
            log.info("Файл договоров не соответствовал базе данных, отправление задачи на перезапись файла договоров...");

            ConcurrentMap<Long, Contract> contractMap = jpaSet.stream()
                    .collect(Collectors.toConcurrentMap(Contract::getId, Function.identity()));

            contractService.updateFileFromConcurrentMap(contractMap);
        }

        log.info("Файл договоров согласован с базой данных");
    }
}
