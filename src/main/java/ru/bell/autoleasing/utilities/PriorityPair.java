package ru.bell.autoleasing.utilities;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public class PriorityPair<T> {
    private final T left;
    private final T right;
    private final boolean isLeftPrimary;

    public T getPrimary() {
        if (isLeftPrimary) {
            return left;
        }
        return right;
    }

    public T getSecondary() {
        if (isLeftPrimary) {
            return right;
        }
        return left;
    }
}
