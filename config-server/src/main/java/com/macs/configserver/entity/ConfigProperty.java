package com.macs.configserver.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "PROPERTIES", schema = "MACS")
public class ConfigProperty {

    @EmbeddedId
    private ConfigPropertyId id;

    @Column(name = "PROP_VALUE", length = 4000)
    private String propValue;

    protected ConfigProperty() {
    }

    public ConfigProperty(ConfigPropertyId id, String propValue) {
        this.id = id;
        this.propValue = propValue;
    }

    public ConfigPropertyId getId() {
        return id;
    }

    public String getPropValue() {
        return propValue;
    }

    public void setPropValue(String propValue) {
        this.propValue = propValue;
    }
}
