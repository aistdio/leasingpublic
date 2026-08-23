package ru.bell.autoleasing.client.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.bell.autoleasing.client.model.Client;
import ru.bell.autoleasing.utilities.annotations.CacheHints;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {
    @CacheHints
    @NotNull List<Client> findAll();

    @CacheHints
    List<Client> findAllByOrderById();

    @CacheHints
    List<Client> findByName(String name);

    @CacheHints
    Optional<Client> findByPassport(String passport);

    @CacheHints
    Optional<Client> findByPhone(String phone);

    void deleteByPassport(String passport);
}
