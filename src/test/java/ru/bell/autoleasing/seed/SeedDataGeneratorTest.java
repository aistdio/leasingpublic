package ru.bell.autoleasing.seed;

import org.junit.jupiter.api.Test;
import ru.bell.autoleasing.car.enums.CarStatus;
import ru.bell.autoleasing.client.enums.ClientType;
import ru.bell.autoleasing.contract.enums.ContractStatus;
import ru.bell.autoleasing.payment.enums.PaymentStatus;
import ru.bell.autoleasing.user.UserRole;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class SeedDataGeneratorTest {
    private static final LocalDate AS_OF = LocalDate.of(2026, 10, 1);
    private final SeedData data = new SeedDataGenerator(20261014L, AS_OF).generate();

    @Test
    void sameSeedGivesIdenticalData() {
        assertThat(new SeedDataGenerator(20261014L, AS_OF).generate()).isEqualTo(data);
    }

    @Test
    void otherSeedGivesOtherData() {
        assertThat(new SeedDataGenerator(1L, AS_OF).generate()).isNotEqualTo(data);
    }

    @Test
    void sizesMatchTheSpecification() {
        assertThat(data.cars()).hasSize(70);
        assertThat(data.clients()).hasSize(30);
        assertThat(data.contracts()).hasSize(60);
        assertThat(data.payments()).hasSizeBetween(1500, 2800);
        assertThat(data.users()).hasSize(31);
        assertThat(data.users()).filteredOn(u -> u.role() == UserRole.MANAGER).hasSize(1);
    }

    @Test
    void edgeCasesArePresent() {
        assertThat(data.contracts()).noneMatch(c -> c.clientId() == 29);
        assertThat(data.contracts()).filteredOn(c -> c.clientId() == 30).singleElement()
                .satisfies(c -> {
                    assertThat(c.id()).isEqualTo(60);
                    assertThat(c.status()).isEqualTo(ContractStatus.CLOSED);
                });
        assertThat(data.payments()).filteredOn(p -> p.contractId() == 60).allMatch(p -> p.status() == PaymentStatus.PAID);
        assertThat(data.payments()).anyMatch(p -> p.contractId() == 1 && p.dueDate().equals(LocalDate.of(2024, 2, 29)));
        assertThat(contract(2).downPayment()).isEqualByComparingTo("0.00");
        assertThat(contract(3).startDate().getDayOfMonth()).isEqualTo(1);
        assertThat(contract(4).interestRate()).isEqualByComparingTo("0");
        assertThat(contract(59).startDate()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(data.payments()).filteredOn(p -> p.contractId() == 59).allMatch(p -> p.status() == PaymentStatus.UNPAID);
        assertThat(client(2).legalName()).contains("\"");
        assertThat(client(4).legalName().length()).isGreaterThan(80);
    }

    @Test
    void paymentsAreInternallyConsistent() {
        Map<Long, SeedData.ContractSeed> contracts = data.contracts().stream()
                .collect(Collectors.toMap(SeedData.ContractSeed::id, Function.identity()));
        data.payments().forEach(p -> {
            assertThat(p.principalPart().add(p.markup()).add(p.vat())).isEqualByComparingTo(p.amount());
            assertThat(p.status() == PaymentStatus.PAID).isEqualTo(p.paidDate() != null);
            if (p.dueDate().isAfter(AS_OF)) {
                assertThat(p.status()).isIn(PaymentStatus.UNPAID, PaymentStatus.CANCELLED);
            }
            if (p.penalty().signum() > 0) {
                assertThat(p.paidDate()).isAfter(p.dueDate());
            }
            if (p.status() == PaymentStatus.CANCELLED) {
                assertThat(contracts.get(p.contractId()).status()).isEqualTo(ContractStatus.CLOSED);
            }
        });
    }

    @Test
    void innsAreValidUniqueAndMatchClientType() {
        assertThat(data.clients()).allMatch(c -> Inn.isValid(c.inn()));
        assertThat(data.clients().stream().map(SeedData.ClientSeed::inn).collect(Collectors.toSet())).hasSize(30);
        assertThat(data.clients()).allMatch(c -> c.inn().length() == (c.type() == ClientType.LLC ? 10 : 12));
    }

    @Test
    void carStatusFollowsActiveContracts() {
        Set<String> leased = data.contracts().stream().filter(c -> c.status() == ContractStatus.ACTIVE)
                .map(SeedData.ContractSeed::carVin).collect(Collectors.toSet());
        data.cars().forEach(car -> assertThat(car.status())
                .isEqualTo(leased.contains(car.vin()) ? CarStatus.LEASED : CarStatus.AVAILABLE));
    }

    private SeedData.ContractSeed contract(long id) {
        return data.contracts().stream().filter(c -> c.id() == id).findFirst().orElseThrow();
    }

    private SeedData.ClientSeed client(long id) {
        return data.clients().stream().filter(c -> c.id() == id).findFirst().orElseThrow();
    }
}
