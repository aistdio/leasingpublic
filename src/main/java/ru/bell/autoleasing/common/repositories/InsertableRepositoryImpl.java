package ru.bell.autoleasing.common.repositories;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

@Repository
@AllArgsConstructor
public class InsertableRepositoryImpl<T> implements InsertableRepository<T> {

    @PersistenceContext
    private final EntityManager entityManager;

    @Override
    @Transactional
    public T insert(T entity) {
        Assert.notNull(entity, "Id не может быть null");

        entityManager.persist(entity);
        return entity;
    }
}