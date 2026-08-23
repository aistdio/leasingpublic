package ru.bell.autoleasing.contract.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.bell.autoleasing.car.enums.CarStatus;
import ru.bell.autoleasing.car.model.Car;
import ru.bell.autoleasing.car.service.CarService;
import ru.bell.autoleasing.client.model.Client;
import ru.bell.autoleasing.client.model.mapper.ClientMapper;
import ru.bell.autoleasing.client.service.ClientService;
import ru.bell.autoleasing.contract.enums.ContractStatus;
import ru.bell.autoleasing.contract.model.Contract;
import ru.bell.autoleasing.contract.model.dto.ContractDto;
import ru.bell.autoleasing.contract.model.dto.CreateContractDto;
import ru.bell.autoleasing.contract.model.mapper.ContractMapper;
import ru.bell.autoleasing.contract.repository.InFileContractRepository;
import ru.bell.autoleasing.contract.repository.JpaContractRepository;
import ru.bell.autoleasing.exceptions.DataCreationException;
import ru.bell.autoleasing.exceptions.DataUpdateException;
import ru.bell.autoleasing.exceptions.NotFoundException;
import ru.bell.autoleasing.exceptions.ValidationException;
import ru.bell.autoleasing.payment.enums.PaymentStatus;
import ru.bell.autoleasing.payment.model.Payment;
import ru.bell.autoleasing.payment.model.PaymentIdContractId;
import ru.bell.autoleasing.payment.model.dto.PaymentDto;
import ru.bell.autoleasing.payment.model.mapper.PaymentMapper;
import ru.bell.autoleasing.payment.service.PaymentService;
import ru.bell.autoleasing.utilities.AnnuityUtilities;
import ru.bell.autoleasing.utilities.PriorityPair;
import ru.bell.autoleasing.utilities.validation.DataValidator;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

@Slf4j
@Service
@AllArgsConstructor
public class ContractServiceImpl implements ContractService {
    private final InFileContractRepository inFileContractRepository;
    private final JpaContractRepository jpaContractRepository;
    private final PaymentService paymentService;
    private final CarService carService;
    private final ClientService clientService;
    private final ClientMapper clientMapper;
    private final PaymentMapper paymentMapper;

    @Override
    @Transactional
    public ContractDto save(CreateContractDto createContractDto) {
        Contract contractFilled = validateAndFillContract(createContractDto);

        Contract contract = jpaContractRepository.save(contractFilled);

        try {
            contract.getCar().setStatus(CarStatus.LEASED);
            contract.setPayments(paymentService.createPaymentsForContract(contract));
        } catch (DataUpdateException | DataCreationException e) {
            throw new DataCreationException(String.format("Не удалось добавить %s. ", contract) + e.getMessage());
        }

        Optional<Contract> contractSavedToFile = inFileContractRepository.save(contract);
        if (contractSavedToFile.isEmpty()) {
            throw new DataCreationException(String.format("Не удалось добавить %s.", contractFilled));
        }

        return ContractMapper.toDto(contract);
    }

    @Override
    @Transactional
    public ContractDto update(ContractDto contractDto) {
        Contract contract = ContractMapper.toEntity(contractDto);

        Contract contractSavedToDb = jpaContractRepository.save(contract);

        inFileContractRepository.update(contract)
                .orElseThrow(() -> new DataUpdateException(
                        String.format("Не удалось обновить %s", contract)));

        return ContractMapper.toDto(contractSavedToDb);
    }

    @Override
    @Transactional
    public PaymentDto updateNearestPaymentStatusToPaid(Long contractId, BigDecimal paidAmount) {
        Contract contract = jpaContractRepository.findByIdEager(contractId).orElseThrow(() ->
                new DataUpdateException("При обновлении статуса платежа не удалось найти договор номер " + contractId));

        Payment payment = Contract.getCurrentMonthToPay(contract.getPayments()).orElseThrow(() ->
                new DataUpdateException("При обновлении статуса платежа не удалось найти неоплаченный платеж у договора номер " + contractId));

        if (!payment.getAmount().equals(paidAmount)) {
            throw new DataUpdateException(String.format("Сумма платежа (%s) не соответствует необходимой (%s) " +
                    "при изменении статуса платежа номер %s у договора номер %s", paidAmount, payment.getAmount(), payment.getPaymentIdContractId().getPaymentId(), contractId));
        }

        return paymentMapper.toDto(this.updatePaymentStatus(contract, payment, PaymentStatus.PAID));
    }

    @Override
    @Transactional
    public void deleteContract(Long contractId) {
        jpaContractRepository.findByIdWithCar(contractId).ifPresent(contract -> this.deleteContractInternal(contractId, contract));
    }

    @Override
    @Transactional
    public void deleteContractsByCarVin(String vin) {
        List<Contract> contracts = jpaContractRepository.findByCarVinWithCar(vin);
        for (Contract contract : contracts) {
            this.deleteContractInternal(contract.getId(), contract);
        }
    }

    @Override
    @Transactional
    public void deleteContractsByClientId(Long clientId) {
        List<Contract> contracts = jpaContractRepository.findByClientIdEager(clientId);
        for (Contract contract : contracts) {
            this.deleteContractInternal(contract.getId(), contract);
        }
    }

    @Override
    @Transactional
    public void deleteContractsByClientPassport(String passport) {
        String passportCleaned = DataValidator.validateAndNormalizePassport(passport);

        List<Contract> contracts = jpaContractRepository.findByClientPassportWithCar(passportCleaned);
        for (Contract contract : contracts) {
            this.deleteContractInternal(contract.getId(), contract);
        }
    }

    @Override
    @Transactional
    public void closeContract(Long contractId) {
        jpaContractRepository.findByIdWithCar(contractId).ifPresent(
                this::closeContractInternal
        );
    }

    @Override
    public PriorityPair<Set<Contract>> findAllInDbAndFileRepositories() {
        Set<Contract> jpaSet = jpaContractRepository.findAllEagerAsSet();

        Future<?> updateRepositoryCache = updateRepositoryCache();
        try {
            updateRepositoryCache.get();
        } catch (InterruptedException e) {
            log.error("Присоединение Future 'обновления кэша репозитория договоров из файла' было прервано");
            Thread.currentThread().interrupt();
            throw new RuntimeException("Присоединение Future 'обновления кэша репозитория договоров из файла' было прервано", e.getCause());
        } catch (ExecutionException e) {
            log.error("Ошибка при 'обновлении кэша репозитория договоров из файла'");
            throw new RuntimeException("Ошибка при 'обновлении кэша репозитория договоров из файла'", e.getCause());
        }

        Set<Contract> inFileSet = inFileContractRepository.findAllAsSet();

        return new PriorityPair<>(jpaSet, inFileSet, true);
    }

    @Override
    public List<ContractDto> findAll() {
        return jpaContractRepository.findAllEager().stream()
                .map(ContractMapper::toDto)
                .toList();
    }

    @Override
    public List<ContractDto> findByStatus(ContractStatus status) {
        return jpaContractRepository.findByStatusEager(status).stream()
                .map(ContractMapper::toDto)
                .toList();
    }

    @Override
    public ContractDto findById(Long id) {
        return ContractMapper.toDto(jpaContractRepository.findByIdEager(id)
                .orElseThrow(() -> new NotFoundException(
                        "Договор с ID " + id + " не был найден")));
    }

    @Override
    public List<ContractDto> findByClient(Client client) {
        return jpaContractRepository.findByClientIdEager(client.getId()).stream()
                .map(ContractMapper::toDto)
                .toList();
    }

    @Override
    @Transactional
    public void deletePaymentByContractIdAndPaymentId(Long contractId, Long paymentId) {
        try {
            paymentService.findByContractIdAndPaymentId(contractId, paymentId);
        } catch (NotFoundException e) {
            throw new DataUpdateException(String.format("При удалении платежа не удалось найти платеж номером %s у договора номер %s", paymentId, contractId));
        }
        paymentService.deleteByContractIdAndPaymentId(new PaymentIdContractId(paymentId, contractId));

        Optional<Contract> optionalContract = jpaContractRepository.findByIdEager(contractId);
        if (optionalContract.isPresent()) {
            Contract contract = optionalContract.get();
            List<Payment> payments = contract.getPayments().stream()
                    .filter(existingPayment -> !existingPayment.getPaymentIdContractId().getPaymentId().equals(paymentId))
                    .toList();
            contract.setPayments(payments);
            contract.setStatus(Contract.determineStatus(contract.getPayments()));
            inFileContractRepository.update(contract);
        }
    }

    @Override
    public Future<?> updateRepositoryCache() {
        return inFileContractRepository.updateCacheFromFile();
    }

    @Override
    public boolean isFilesReady() { return inFileContractRepository.isFileReady(); }

    @Override
    public void updateFileFromConcurrentMap(ConcurrentMap<Long, Contract> map) {
        inFileContractRepository.updateFileFromCache(map);
    }

    private Contract validateAndFillContract(CreateContractDto createContractDto) {
        Car car;
        Client client;
        try {
            String vin = DataValidator.validateAndNormalizeVin(createContractDto.vin());
            car = carService.findByVinInternalWithReadLock(vin);
            if (car.getStatus().equals(CarStatus.LEASED)) {
                throw new DataCreationException(String.format("Не удалось создать договор, поскольку автомобиль с VIN %s уже находится в лизинге", vin));
            }

            List<Client> clients = clientMapper.toEntity(clientService.findByOptionalCriteria(createContractDto.client()));
            if (clients.isEmpty()) {
                throw new NotFoundException("Не удалось найти клиента по запросу " + createContractDto.client());
            } else if (clients.size() != 1) {
                throw new DataCreationException("При создании договора было найдено несколько клиентов, относящихся к запросу " + createContractDto.client());
            }
            client = clients.getFirst();
        } catch (NotFoundException e) {
            throw new DataCreationException("При создании договора не удалось найти сущность по переданным данным. " + e.getMessage());
        }

        BigDecimal creditAmount = car.getPrice().subtract(createContractDto.downPayment());
        if (creditAmount.signum() < 0) {
            throw new ValidationException("Сумма кредита не может быть меньше нуля. Cумма кредита: " + creditAmount);
        }

        BigDecimal monthlyPayment = AnnuityUtilities.calculateMonthlyAnnuity(creditAmount, createContractDto.interestRate(), createContractDto.durationInMonths());

        return new Contract(
                null,
                null,
                car,
                client,
                createContractDto.durationInMonths(),
                createContractDto.downPayment(),
                createContractDto.interestRate(),
                monthlyPayment,
                ContractStatus.ACTIVE
        );
    }

    private Payment updatePaymentStatus(Contract contract, Payment payment, PaymentStatus paymentStatus) {
        try {
            contract.getPayments().stream()
                    .filter(payment1 -> payment1.equals(payment))
                    .findFirst()
                    .orElseThrow(() -> new DataUpdateException(String.format("Не удалось обновить статус платежа с ID %s у договора с номером %s. ", payment.getPaymentIdContractId().getPaymentId(), contract.getId())))
                    .setStatus(paymentStatus);

            if (Contract.determineStatus(contract.getPayments()).equals(ContractStatus.CLOSED)) {
                this.closeContractInternal(contract);
            }
            inFileContractRepository.update(contract);

            return payment;
        }
        catch (NotFoundException | DataUpdateException e) {
            throw new DataUpdateException(String.format("Не удалось обновить статус платежа с ID %s у договора с номером %s. ", payment.getPaymentIdContractId().getPaymentId(), contract.getId()) + e.getMessage());
        }
    }

    private void closeContractInternal(Contract contract) {
        if (contract.getStatus().equals(ContractStatus.CLOSED)) {
            throw new DataUpdateException("Договор номер " + contract.getId() + " уже был закрыт");
        }

        try {
            contract.getCar().setStatus(CarStatus.AVAILABLE);
            contract.setStatus(ContractStatus.CLOSED);

            inFileContractRepository.update(contract).orElseThrow(() -> new DataUpdateException("Не удалось записать информацию о закрытии договора номер " + contract.getId()));
        } catch (DataUpdateException e) {
            throw new DataUpdateException("Не удалось закрыть договор номер " + contract.getId() + ". " + e.getMessage());
        }
    }

    private void deleteContractInternal(Long contractId, Contract contract) {
        paymentService.deleteByContractId(contract.getId());
        jpaContractRepository.deleteById(contractId);
        inFileContractRepository.deleteById(contractId);
    }
}
