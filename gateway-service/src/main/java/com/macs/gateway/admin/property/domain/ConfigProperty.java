package com.macs.gateway.admin.property.domain;

public class ConfigProperty {

    private final ConfigPropertyId id;
    private String propValue;

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
