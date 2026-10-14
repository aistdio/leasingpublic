package ru.bell.autoleasing.seed;

public final class Inn {
    private static final int[] W10 = {2, 4, 10, 3, 5, 9, 4, 6, 8};
    private static final int[] W11 = {7, 2, 4, 10, 3, 5, 9, 4, 6, 8};
    private static final int[] W12 = {3, 7, 2, 4, 10, 3, 5, 9, 4, 6, 8};

    private Inn() {
    }

    /** Adds check digits: 9 digits → 10-digit number of a company, 10 digits → 12-digit number of a person. */
    public static String complete(String digits) {
        if (digits.length() == 9) {
            return digits + check(digits, W10);
        }
        if (digits.length() == 10) {
            String eleven = digits + check(digits, W11);
            return eleven + check(eleven, W12);
        }
        throw new IllegalArgumentException("ожидалось 9 или 10 цифр: " + digits);
    }

    public static boolean isValid(String inn) {
        if (inn == null || !inn.matches("\\d{10}|\\d{12}")) {
            return false;
        }
        return complete(inn.substring(0, inn.length() == 10 ? 9 : 10)).equals(inn);
    }

    private static int check(String digits, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += (digits.charAt(i) - '0') * weights[i];
        }
        return sum % 11 % 10;
    }
}
