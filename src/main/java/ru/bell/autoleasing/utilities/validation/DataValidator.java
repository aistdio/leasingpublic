package ru.bell.autoleasing.utilities.validation;

import org.apache.commons.validator.routines.InetAddressValidator;
import ru.bell.autoleasing.exceptions.ValidationException;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.regex.Pattern;

public class DataValidator {
    private static final Pattern VIN_PATTERN = Pattern.compile("^[A-Z0-9]{17}$");
    private static final Pattern FULLNAME_PATTERN = Pattern.compile("^[\\p{L}']+(?:\\s+[\\p{L}']+){2}$");
    private static final Pattern PASSPORT_PATTERN = Pattern.compile("^\\d{4}\\s\\d{6}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+7\\d{10}$");
    private static final Pattern CONTRACT_NUMBER_PATTERN = Pattern.compile("^\\d+$");

    private static final int MIN_RELEASE_YEAR = 2000;
    private static final int MAX_RELEASE_YEAR = 2025;

    private static final InetAddressValidator IP_VALIDATOR = InetAddressValidator.getInstance();
    private static final int maxIpAddressLength = 100;

    public static <E> void validateCollectionIndex(Collection<E> c, int index) {
        if (index < 0 || index >= c.size()) {
            throw new ValidationException("Индекс " + (index + 1) + " не найден");
        }
    }

    public static String validateAndNormalizeIpAddress(String ipAddr) throws ValidationException {
        String trimmedIpAddr = validateAndTrimString(ipAddr, "IP адрес");

        if (trimmedIpAddr.length() > maxIpAddressLength) {
            throw new ValidationException("IP адрес содержит более " + maxIpAddressLength + "символов");
        }

        if (!IP_VALIDATOR.isValid(trimmedIpAddr)) {
            throw new ValidationException("IP адрес не прошел валидацию");
        }

        return trimmedIpAddr;
    }

    public static String validateAndNormalizeVin(String vin) throws ValidationException {
        String fieldName = "VIN";
        String normalized = normalizeVin(validateAndTrimString(vin, fieldName));

        if (!VIN_PATTERN.matcher(normalized).matches()) {
            throw new ValidationException("VIN не содержит 17 символов, состоящих из цифр и латинских букв", fieldName, vin);
        }

        return normalized;
    }

    public static String validateAndNormalizeName(String name) throws ValidationException {
        String fieldName = "ФИО";
        String trimmed = validateAndTrimString(name, fieldName);

        String[] parts = trimmed.split("\\s+");
        if (parts.length != 3) {
            throw new ValidationException("Не соответствует формату 'Фамилия Имя Отчество'", fieldName, name);
        }

        String normalized = normalizeFullName(parts);

        if (!FULLNAME_PATTERN.matcher(normalized).matches()) {
            throw new ValidationException("Не соответствует формату 'Фамилия Имя Отчество'", fieldName, name);
        }

        return normalized;
    }

    public static String validateAndNormalizePassport(String passport) throws ValidationException {
        String fieldName = "Паспорт";
        String trimmedPassport = validateAndTrimString(passport, fieldName);

        if (trimmedPassport.length() < 10) {
            throw new ValidationException("Номер паспорта не содержит 10 цифр", fieldName, passport);
        }

        String normalized = normalizePassport(trimmedPassport);

        if (!PASSPORT_PATTERN.matcher(normalized).matches()) {
            throw new ValidationException("Номер паспорта не содержит 10 цифр", fieldName, passport);
        }

        return normalized;
    }

    public static String validateAndNormalizePhone(String phone) throws ValidationException {
        String fieldName = "Номер телефона";
        String normalized = normalizePhone(validateAndTrimString(phone, fieldName));

        if (!PHONE_PATTERN.matcher(normalized).matches()) {
            throw new ValidationException("Номер телефона не соответствует формату +71234567890", fieldName, phone);
        }

        return normalized;
    }

    public static Long validateAndNormalizeContractId(String contractNumber) throws ValidationException {
        String fieldName = "Номер контракта";
        String normalizedString = normalizeContractNumber(validateAndTrimString(contractNumber, fieldName));

        if (!CONTRACT_NUMBER_PATTERN.matcher(normalizedString).matches()) {
            throw new ValidationException("Номер контракта не содержит только цифры", fieldName, contractNumber);
        }

        long normalized;
        try {
            normalized = Long.parseLong(normalizedString);
        } catch (NumberFormatException e) {
            throw new ValidationException("Неверный формат числа.");
        }

        return normalized;
    }

    public static void validateReleaseYear(Integer releaseYear) throws ValidationException {
        if (releaseYear == null) {
            throw new ValidationException("Поле \"Год выпуска\" не может быть пустым", "Год выпуска", null);
        }

        if (releaseYear < MIN_RELEASE_YEAR || releaseYear > MAX_RELEASE_YEAR) {
            throw new ValidationException("Год выпуска меньше возможного", "Год выпуска", String.valueOf(releaseYear));
        }
    }

    public static void validatePrice(BigDecimal price) throws ValidationException {
        if (price == null) {
            throw new ValidationException("Поле Цена не может быть пустым", "Цена", null);
        }

        if (price.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("Цена не может быть меньше нуля", "Цена", String.valueOf(price));
        }
    }

    public static void validatePaymentAmount(BigDecimal total) throws ValidationException {
        if (total == null) {
            throw new ValidationException("Поле Сумма не может быть пустым", "Цена", null);
        }

        if (total.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("Сумма не может быть меньше нуля", "Цена", String.valueOf(total));
        }
    }

    public static String validateAndNormalizeUppercase(String value, String fieldName) throws ValidationException {
        return normalizeFirstLetterUpperCase(validateAndTrimString(value, fieldName));
    }

    private static String validateAndTrimString(String value, String fieldName) throws ValidationException {
        if (value == null || value.isBlank()) {
            throw new ValidationException(String.format("Поле %s не может быть пустым", fieldName), fieldName, null);
        }

        return value.trim();
    }

    private static String normalizeVin(String vin) {
        return vin.toUpperCase().replaceAll("\\s", "");
    }

    private static String normalizeFullName(String[] parts) {
        StringBuilder name = new StringBuilder();
        for (String part : parts) {
            name.append(part.substring(0, 1).toUpperCase()).append(part.substring(1).toLowerCase())
                    .append(" ");
        }
        return name.toString().trim();
    }

    private static String normalizePassport(String passport) {
        String clean = passport.replaceAll("\\s", "");
        return clean.substring(0, 4) + " " + clean.substring(4);
    }

    private static String normalizePhone(String phone) {
        String normalized = phone.replaceAll("[\\s\\-()]", "");
        if (phone.startsWith("8")) {
            normalized = "+7" + normalized.substring(1);
        }
        return normalized;
    }

    private static String normalizeContractNumber(String contractNumber) {
        return contractNumber.replaceAll("\\s", "");
    }

    private static String normalizeFirstLetterUpperCase(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
