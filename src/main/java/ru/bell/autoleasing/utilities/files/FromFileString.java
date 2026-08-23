package ru.bell.autoleasing.utilities.files;

@FunctionalInterface
public interface FromFileString<T> {
    T apply(String s);
}
