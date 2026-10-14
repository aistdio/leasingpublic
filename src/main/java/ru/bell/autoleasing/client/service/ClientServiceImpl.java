package ru.bell.autoleasing.client.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.bell.autoleasing.client.model.Client;
import ru.bell.autoleasing.client.model.dto.ClientDto;
import ru.bell.autoleasing.client.model.mapper.ClientMapper;
import ru.bell.autoleasing.client.repository.ClientRepository;
import ru.bell.autoleasing.exceptions.DataUpdateException;
import ru.bell.autoleasing.exceptions.NotFoundException;
import ru.bell.autoleasing.exceptions.ValidationException;
import ru.bell.autoleasing.utilities.validation.DataValidator;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ClientServiceImpl implements ClientService {
    private final ClientRepository clientRepository;
    private final ClientMapper clientMapper;

    @Override
    @Transactional
    public ClientDto save(ClientDto clientDto) {
        Client clientRaw = clientMapper.toEntity(clientDto);
        Client client = validateAndNormalizeClient(clientRaw);
        validateClientUniqueness(client);

        return clientMapper.toDto(clientRepository.save(client));
    }

    @Override
    @Transactional
    public ClientDto update(ClientDto clientDto) {
        Client clientRaw = clientMapper.toEntity(clientDto);
        Client client = validateAndNormalizeClient(clientRaw);

        if (clientRepository.findById(client.getId()).isEmpty()) {
            throw new DataUpdateException("Клиент с id " + client.getId() + " не был найден.");
        }

        return clientMapper.toDto(clientRepository.save(client));
    }

    @Override
    public List<ClientDto> findAll() {
        return clientMapper.toDto(clientRepository.findAllByOrderById());
    }

    @Override
    public ClientDto findById(Long id) {
        return clientMapper.toDto(clientRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        "Клиент с id " + id + " не был найден")));
    }

    @Override
    public List<ClientDto> findByName(String name) {
        return clientMapper.toDto(clientRepository.findByName(name));
    }

    @Override
    public ClientDto findByPassport(String passport) {
        return clientMapper.toDto(clientRepository.findByPassport(passport)
                .orElseThrow(() -> new NotFoundException(
                        "Клиент с паспортом " + passport + " не был найден")));
    }

    @Override
    public ClientDto findByPhone(String phone) {
        return clientMapper.toDto(clientRepository.findByPhone(phone)
                .orElseThrow(() -> new NotFoundException(
                        "Клиент с телефоном " + phone + " не был найден")));
    }

    @Override
    public List<ClientDto> findByOptionalCriteria(String value) {
        List<ClientDto> clientDtoList = new ArrayList<>(1);

        if (Character.isLetter(value.charAt(0))) {
            String name = DataValidator.validateAndNormalizeName(value);
            clientDtoList.addAll(this.findByName(name));
        } else if (value.charAt(0) == '+') {
            String phone = DataValidator.validateAndNormalizePhone(value);
            clientDtoList.add(this.findByPhone(phone));
        } else {
            String passport = DataValidator.validateAndNormalizePassport(value);
            clientDtoList.add(this.findByPassport(passport));
        }

        return clientDtoList;
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        clientRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void deleteByPassport(String passportRaw) {
        String passport = DataValidator.validateAndNormalizePassport(passportRaw);
        clientRepository.deleteByPassport(passport);
    }

    private Client validateAndNormalizeClient(Client clientRaw) {
        String name = DataValidator.validateAndNormalizeName(clientRaw.getName());
        String passport = DataValidator.validateAndNormalizePassport(clientRaw.getPassport());
        String phone = DataValidator.validateAndNormalizePhone(clientRaw.getPhone());

        return new Client(clientRaw.getId(), name, passport, phone,
                clientRaw.getClientType(), clientRaw.getInn(), clientRaw.getLegalName());
    }

    private void validateClientUniqueness(Client client) {
        if (clientRepository.findByPassport(client.getPassport()).isPresent()) {
            throw new ValidationException("Клиент с паспортом " + client.getPassport() + " уже существует");
        }
        if (clientRepository.findByPhone(client.getPhone()).isPresent()) {
            throw new ValidationException("Клиент с номером телефона " + client.getPhone() + " уже существует");
        }
    }
}
