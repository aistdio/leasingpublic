package ru.bell.autoleasing.client.service;

import ru.bell.autoleasing.client.model.dto.ClientDto;

import java.util.List;

public interface ClientService {

    ClientDto save(ClientDto clientDto);

    ClientDto update(ClientDto clientDto);

    List<ClientDto> findAll();

    ClientDto findById(Long id);

    List<ClientDto> findByName(String name);

    ClientDto findByPassport(String passport);

    ClientDto findByPhone(String phone);

    List<ClientDto> findByOptionalCriteria(String value);

    void deleteById(Long id);

    void deleteByPassport(String passport);
}
