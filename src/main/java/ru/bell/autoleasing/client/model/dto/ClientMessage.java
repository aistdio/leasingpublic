package ru.bell.autoleasing.client.model.dto;

import lombok.*;
import ru.bell.autoleasing.utilities.broker.CrudOperation;

import java.io.Serial;
import java.io.Serializable;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
public class ClientMessage implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private CrudOperation crudOperation;
    private ClientDto clientDto;
}
