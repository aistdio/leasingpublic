package ru.bell.autoleasing.audit;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;
import org.hibernate.validator.constraints.Length;

import java.util.Date;
import java.util.Objects;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "revinfo", schema = "autoleasing_core_history")
@RevisionEntity(RestRevisionListener.class)
public class RestRevisionEntity {

    @Id
    @RevisionNumber
    @SequenceGenerator(name = "RevInfoSeq", sequenceName = "revinfo_seq", schema = "autoleasing_core_history", allocationSize = 1)
    @GeneratedValue(generator = "RevInfoSeq", strategy = GenerationType.SEQUENCE)
    private int rev;

    @RevisionTimestamp
    @Temporal(TemporalType.TIMESTAMP)
    private Date revtstmp;

    @Column(name = "client_ip")
    @NotBlank
    @Length(max = 100)
    private String clientIp;

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        RestRevisionEntity that = (RestRevisionEntity) o;
        return rev == that.rev && revtstmp == that.revtstmp;
    }

    @Override
    public int hashCode() {
        return Objects.hash(rev, revtstmp);
    }
}
