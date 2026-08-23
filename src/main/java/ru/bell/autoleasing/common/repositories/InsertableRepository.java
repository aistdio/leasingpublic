package ru.bell.autoleasing.common.repositories;

import org.springframework.dao.DataIntegrityViolationException;

public interface InsertableRepository <T> {

    /**
     * Переводит сущность в Managed и Persistent.
     * <p>
     * Этот метод является обходом лишнего SELECT,
     * который возникает при сохранении Not Managed (New/Transient) сущности
     * с не пустым Id и отсутствующим полем Version
     * вследствие вызова метода JpaRepository/SimpleJpaRepository .save() -> .merge().
     * <p>
     * @param entity сущность
     * @return сущность
     * @throws DataIntegrityViolationException оборачивается в это исключение при нарушении ограничений
     * таблицы базы данных
     */
    T insert(T entity);
}
