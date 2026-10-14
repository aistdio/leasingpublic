package ru.bell.autoleasing.seed;

import ru.bell.autoleasing.car.enums.CarStatus;
import ru.bell.autoleasing.client.enums.ClientType;
import ru.bell.autoleasing.contract.enums.ContractStatus;
import ru.bell.autoleasing.payment.enums.PaymentStatus;
import ru.bell.autoleasing.schedule.PaymentScheduleCalculator;
import ru.bell.autoleasing.schedule.ScheduledPayment;
import ru.bell.autoleasing.user.UserRole;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

public final class SeedDataGenerator {
    public static final int CARS = 70;
    public static final int CLIENTS = 30;
    public static final int CONTRACTS = 60;
    private static final int[] DURATIONS = {12, 24, 36, 48, 60};
    private static final LocalDate FIRST_START = LocalDate.of(2022, 1, 1);
    private static final LocalDate LAST_START = LocalDate.of(2026, 9, 30);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal PENALTY_PER_DAY = new BigDecimal("0.001");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final String VIN_CHARS = "ABCDEFGHJKLMNPRSTUVWXYZ0123456789";
    private static final String[][] MODELS = {
            {"Haval", "Jolion"}, {"Haval", "F7"}, {"Chery", "Tiggo 7 Pro"}, {"Geely", "Monjaro"},
            {"Geely", "Coolray"}, {"Lada", "Vesta"}, {"Lada", "Largus"}, {"Toyota", "Camry"}, {"Kia", "K5"},
            {"Hyundai", "Sonata"}, {"GAZ", "Gazelle Next"}, {"Exeed", "TXL"}, {"Tank", "300"},
            {"Changan", "UNI-K"}, {"Volkswagen", "Tiguan"}};
    private static final String[] LAST_NAMES = {"Иванов", "Смирнов", "Кузнецов", "Попов", "Васильев", "Петров",
            "Соколов", "Михайлов", "Новиков", "Фёдоров", "Морозов", "Волков", "Алексеев", "Лебедев", "Семёнов",
            "Егоров", "Павлов", "Козлов", "Степанов", "Николаев"};
    private static final String[] FIRST_NAMES = {"Александр", "Дмитрий", "Максим", "Сергей", "Андрей", "Алексей",
            "Артём", "Илья", "Кирилл", "Михаил", "Никита", "Роман"};
    private static final String[] PATRONYMICS = {"Александрович", "Дмитриевич", "Сергеевич", "Андреевич",
            "Алексеевич", "Игоревич", "Владимирович", "Николаевич"};
    private static final String[] COMPANIES = {"Северный Транзит", "Вектор Логистик", "СтройМонтаж", "Альфа Сервис",
            "ТехноПарк", "Гранит", "Меридиан", "Восток Трейд", "Профи Карго", "Сокол Групп", "Энергия Плюс",
            "Байкал Авто", "Орион", "Кристалл", "Прогресс", "Дельта Строй", "Уралтранс", "Регион Снаб", "Импульс",
            "Партнёр"};
    private static final String QUOTED_NAME = "ООО «Торговый дом \"Рога и Копыта\"»";
    private static final String LONG_NAME = "ООО «Межрегиональная транспортно-экспедиционная компания "
            + "\"Северо-Западный логистический альянс\"»";

    private final Random rnd;
    private final LocalDate asOf;
    private final Set<String> usedVins = new HashSet<>();
    private final Set<String> usedInns = new HashSet<>();
    private final Set<String> usedPassports = new HashSet<>();
    private final Set<String> usedPhones = new HashSet<>();

    public SeedDataGenerator(long seed, LocalDate asOf) {
        this.rnd = new Random(seed);
        this.asOf = asOf;
    }

    public SeedData generate() {
        List<SeedData.CarSeed> cars = generateCars();
        List<SeedData.ClientSeed> clients = generateClients();
        int[] perClient = distributeContracts();
        List<SeedData.ContractSeed> contracts = new ArrayList<>(CONTRACTS);
        List<SeedData.PaymentSeed> payments = new ArrayList<>();
        long contractId = 1;
        for (int clientId = 1; clientId <= CLIENTS; clientId++) {
            for (int k = 0; k < perClient[clientId]; k++) {
                SeedData.CarSeed car = cars.get((int) contractId - 1);
                contracts.add(generateContract(contractId, clientId, car, payments));
                contractId++;
            }
        }
        Set<String> leased = new HashSet<>();
        contracts.stream().filter(c -> c.status() == ContractStatus.ACTIVE).forEach(c -> leased.add(c.carVin()));
        List<SeedData.CarSeed> finalCars = cars.stream()
                .map(c -> new SeedData.CarSeed(c.vin(), c.brand(), c.model(), c.releaseYear(), c.price(),
                        leased.contains(c.vin()) ? CarStatus.LEASED : CarStatus.AVAILABLE))
                .toList();
        List<SeedData.UserSeed> users = new ArrayList<>();
        users.add(new SeedData.UserSeed("manager", UserRole.MANAGER, null));
        clients.forEach(c -> users.add(new SeedData.UserSeed(String.format("client%02d", c.id()), UserRole.CLIENT, c.id())));
        return new SeedData(finalCars, clients, contracts, payments, users);
    }

    private List<SeedData.CarSeed> generateCars() {
        List<SeedData.CarSeed> cars = new ArrayList<>(CARS);
        for (int i = 0; i < CARS; i++) {
            String[] model = MODELS[rnd.nextInt(MODELS.length)];
            int year = 2019 + rnd.nextInt(8);
            BigDecimal price = BigDecimal.valueOf(200 + rnd.nextInt(701)).multiply(BigDecimal.valueOf(10_000)).setScale(2);
            cars.add(new SeedData.CarSeed(nextVin(), model[0], model[1], year, price, CarStatus.AVAILABLE));
        }
        return cars;
    }

    private List<SeedData.ClientSeed> generateClients() {
        List<SeedData.ClientSeed> clients = new ArrayList<>(CLIENTS);
        int companyIndex = 0;
        for (long id = 1; id <= CLIENTS; id++) {
            String last = LAST_NAMES[rnd.nextInt(LAST_NAMES.length)];
            String first = FIRST_NAMES[rnd.nextInt(FIRST_NAMES.length)];
            String patronymic = PATRONYMICS[rnd.nextInt(PATRONYMICS.length)];
            ClientType type = id % 3 == 0 ? ClientType.INDIVIDUAL_ENTREPRENEUR : ClientType.LLC;
            String inn = type == ClientType.LLC ? uniqueInn("77", 7) : uniqueInn("50", 8);
            String legalName;
            if (type == ClientType.INDIVIDUAL_ENTREPRENEUR) {
                legalName = "ИП " + last + " " + first.charAt(0) + ". " + patronymic.charAt(0) + ".";
            } else if (id == 2) {
                legalName = QUOTED_NAME;
            } else if (id == 4) {
                legalName = LONG_NAME;
            } else {
                legalName = "ООО «" + COMPANIES[companyIndex++ % COMPANIES.length] + "»";
            }
            String passport = uniqueDigits(10, usedPassports);
            String phone = "+79" + uniqueDigits(9, usedPhones);
            clients.add(new SeedData.ClientSeed(id, last + " " + first + " " + patronymic, passport, phone, type, inn, legalName));
        }
        return clients;
    }

    /** Index = client id. Client 29 gets none, client 30 gets exactly one, clients 1–28 share the rest (1–4 each). */
    private int[] distributeContracts() {
        int[] perClient = new int[CLIENTS + 1];
        for (int c = 1; c <= 28; c++) {
            perClient[c] = 1;
        }
        int remaining = CONTRACTS - 28 - 1;
        while (remaining > 0) {
            int c = 1 + rnd.nextInt(28);
            if (perClient[c] < 4) {
                perClient[c]++;
                remaining--;
            }
        }
        perClient[30] = 1;
        return perClient;
    }

    private SeedData.ContractSeed generateContract(long id, long clientId, SeedData.CarSeed car,
                                                   List<SeedData.PaymentSeed> payments) {
        int duration = DURATIONS[rnd.nextInt(DURATIONS.length)];
        LocalDate start = FIRST_START.plusDays(rnd.nextInt((int) ChronoUnit.DAYS.between(FIRST_START, LAST_START) + 1));
        int downPercent = rnd.nextInt(31);
        BigDecimal rate = BigDecimal.valueOf(80 + rnd.nextInt(101), 1);
        boolean earlyBuyout = rnd.nextInt(100) < 8;
        switch ((int) id) {
            case 1 -> { start = LocalDate.of(2024, 1, 31); duration = 36; }
            case 2 -> downPercent = 0;
            case 3 -> start = LocalDate.of(2025, 12, 1);
            case 4 -> rate = BigDecimal.valueOf(0, 1);
            case 59 -> { start = LocalDate.of(2026, 9, 30); duration = 12; earlyBuyout = false; }
            case 60 -> { start = LocalDate.of(2022, 3, 15); duration = 24; earlyBuyout = false; }
            default -> { }
        }
        BigDecimal down = car.price().multiply(BigDecimal.valueOf(downPercent)).divide(HUNDRED, 0, RoundingMode.HALF_UP).setScale(2);
        List<ScheduledPayment> schedule = PaymentScheduleCalculator.calculate(car.price().subtract(down), rate, duration, start);
        int dueByAsOf = (int) schedule.stream().filter(p -> !p.dueDate().isAfter(asOf)).count();
        int buyoutAfter = earlyBuyout && dueByAsOf >= 6 ? 3 + rnd.nextInt(dueByAsOf - 4) : -1;

        boolean allPaid = true;
        for (ScheduledPayment p : schedule) {
            PaymentStatus status;
            LocalDate paidDate = null;
            BigDecimal penalty = ZERO;
            if (buyoutAfter > 0) {
                if (p.number() > buyoutAfter) {
                    status = PaymentStatus.CANCELLED;
                } else {
                    status = PaymentStatus.PAID;
                    paidDate = p.dueDate().minusDays(rnd.nextInt(6));
                }
            } else if (p.dueDate().isAfter(asOf)) {
                status = PaymentStatus.UNPAID;
            } else if (id == 60) {
                status = PaymentStatus.PAID;
                paidDate = p.dueDate();
            } else {
                int roll = rnd.nextInt(100);
                boolean recent = p.dueDate().isAfter(asOf.minusMonths(3));
                if (roll < 80) {
                    status = PaymentStatus.PAID;
                    paidDate = p.dueDate().minusDays(rnd.nextInt(6));
                } else if (roll < 94 || !recent) {
                    int daysLate = 1 + rnd.nextInt(20);
                    LocalDate late = p.dueDate().plusDays(daysLate);
                    if (late.isAfter(asOf)) {
                        status = PaymentStatus.UNPAID;
                    } else {
                        status = PaymentStatus.PAID;
                        paidDate = late;
                        penalty = p.amount().multiply(PENALTY_PER_DAY).multiply(BigDecimal.valueOf(daysLate))
                                .setScale(2, RoundingMode.HALF_UP);
                    }
                } else {
                    status = PaymentStatus.UNPAID;
                }
            }
            if (status != PaymentStatus.PAID) {
                allPaid = false;
            }
            payments.add(new SeedData.PaymentSeed(id, p.number(), p.dueDate(), paidDate, p.amount(),
                    p.principalPart(), p.markup(), p.vat(), penalty, status));
        }
        ContractStatus status = buyoutAfter > 0 || allPaid ? ContractStatus.CLOSED : ContractStatus.ACTIVE;
        return new SeedData.ContractSeed(id, String.format("AL-%d-%05d", start.getYear(), id), car.vin(), clientId,
                duration, down, rate, schedule.get(0).amount(), status, start);
    }

    private String nextVin() {
        while (true) {
            StringBuilder vin = new StringBuilder(17);
            for (int i = 0; i < 17; i++) {
                vin.append(VIN_CHARS.charAt(rnd.nextInt(VIN_CHARS.length())));
            }
            if (usedVins.add(vin.toString())) {
                return vin.toString();
            }
        }
    }

    private String uniqueInn(String prefix, int randomDigits) {
        while (true) {
            String inn = Inn.complete(prefix + digits(randomDigits));
            if (usedInns.add(inn)) {
                return inn;
            }
        }
    }

    private String uniqueDigits(int length, Set<String> used) {
        while (true) {
            String value = digits(length);
            if (used.add(value)) {
                return value;
            }
        }
    }

    private String digits(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(rnd.nextInt(10));
        }
        return sb.toString();
    }
}
