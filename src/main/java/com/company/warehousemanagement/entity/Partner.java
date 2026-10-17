package com.company.warehousemanagement.entity;

import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Email;

@JmixEntity
@Table(name = "PARTNER", uniqueConstraints = {
        @UniqueConstraint(name = "IDX_PARTNER_UNQ_CODE", columnNames = "CODE")
}, indexes = {
        @Index(name = "IDX_PARTNER_NAME", columnList = "NAME")
})
@Entity
public class Partner extends BaseUuidEntity {

    @Column(name = "CODE", nullable = false, length = 50)
    private String code;

    @InstanceName
    @Column(name = "NAME", nullable = false)
    private String name;

    @Column(name = "PARTNER_TYPE", nullable = false, length = 20)
    private String partnerType;

    @Column(name = "ACTIVE", nullable = false)
    private Boolean active = true;

    @Email
    @Column(name = "EMAIL", length = 255)
    private String email;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public PartnerType getPartnerType() {
        return partnerType == null ? null : PartnerType.fromId(partnerType);
    }

    public void setPartnerType(PartnerType partnerType) {
        this.partnerType = partnerType == null ? null : partnerType.getId();
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}

