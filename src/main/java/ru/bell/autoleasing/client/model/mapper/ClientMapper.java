package ru.bell.autoleasing.client.model.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import ru.bell.autoleasing.client.model.Client;
import ru.bell.autoleasing.client.model.dto.ClientDto;
import ru.bell.autoleasing.utilities.mapper.EntityMapper;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ClientMapper extends EntityMapper<ClientDto, Client> {
}