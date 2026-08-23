package ru.bell.autoleasing.client.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.envers.AuditTable;
import org.hibernate.envers.Audited;

import java.util.Objects;

@Entity
@Table(name = "clients", schema = "autoleasing_core")
@Audited
@AuditTable(value = "clients_aud", schema = "autoleasing_core_history")
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Client {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    private String name;

    @Size(max = 11)
    @NotNull
    private String passport;

    @Size(max = 18)
    @NotNull
    private String phone;

    public String toFileString() {
        return String.join("|", id.toString(), name, passport, phone);
    }

    public static Client fromFileString(String line) {
        String[] p = line.split("\\|", -1);
        if (p.length != 4) {
            throw new IllegalArgumentException("Неверный формат строки для получения сущности: " + line);
        }

        return new Client(Long.parseLong(p[0]), p[1], p[2], p[3]);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Client client = (Client) o;
        return Objects.equals(id, client.id) && Objects.equals(name, client.name) && Objects.equals(passport, client.passport) && Objects.equals(phone, client.phone);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, passport, phone);
    }
}