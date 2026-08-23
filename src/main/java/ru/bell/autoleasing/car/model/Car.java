package ru.bell.autoleasing.car.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.envers.AuditTable;
import org.hibernate.envers.Audited;
import ru.bell.autoleasing.car.enums.CarStatus;
import ru.bell.autoleasing.car.enums.CarStatusConverter;

import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "cars", schema = "autoleasing_core")
@Audited
@AuditTable(value = "cars_aud", schema = "autoleasing_core_history")
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Car {
    @Id
    private String vin;

    private String brand;

    private String model;

    @Column(name = "release_year")
    private Integer releaseYear;

    private BigDecimal price;

    @Convert(converter = CarStatusConverter.class)
    private CarStatus status;

    @Version
    @JsonIgnore
    private Integer version;

    public void setAll(Car other) {
        this.vin = other.vin;
        this.brand = other.brand;
        this.model = other.model;
        this.releaseYear = other.releaseYear;
        this.price = other.price;
        this.status = other.status;
        this.version = other.version;
    }

    public String toFileString() {
        return String.join(
                "|",
                vin,
                brand,
                model,
                releaseYear.toString(),
                price.toPlainString(),
                status.toString()
        );
    }

    public static Car fromFileString(String line) {
        String[] p = line.split("\\|", -1);
        if (p.length != 6) {
            throw new IllegalArgumentException("Неверный формат строки для получения автомобиля: " + line);
        }
        return new Car(
                p[0],
                p[1],
                p[2],
                Integer.parseInt(p[3]),
                new BigDecimal(p[4]),
                CarStatus.fromString(p[5]),
                null
        );
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Car car = (Car) o;
        return Objects.equals(vin, car.vin) && Objects.equals(status, car.status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(vin, status);
    }
}
