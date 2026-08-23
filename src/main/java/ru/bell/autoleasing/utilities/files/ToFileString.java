package ru.bell.autoleasing.utilities.files;

@FunctionalInterface
public interface ToFileString<T> {
    String apply(T t);
}
