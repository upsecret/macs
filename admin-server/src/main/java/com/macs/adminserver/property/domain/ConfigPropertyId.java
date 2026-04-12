package com.macs.adminserver.property.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ConfigPropertyId implements Serializable {

    @Column(name = "APPLICATION", length = 128, nullable = false)
    private String application;

    @Column(name = "PROFILE", length = 128, nullable = false)
    private String profile;

    @Column(name = "LABEL", length = 128, nullable = false)
    private String label;

    @Column(name = "PROP_KEY", length = 256, nullable = false)
    private String propKey;

    protected ConfigPropertyId() {
    }

    public ConfigPropertyId(String application, String profile, String label, String propKey) {
        this.application = application;
        this.profile = profile;
        this.label = label;
        this.propKey = propKey;
    }

    public String getApplication() {
        return application;
    }

    public String getProfile() {
        return profile;
    }

    public String getLabel() {
        return label;
    }

    public String getPropKey() {
        return propKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConfigPropertyId that)) return false;
        return Objects.equals(application, that.application)
                && Objects.equals(profile, that.profile)
                && Objects.equals(label, that.label)
                && Objects.equals(propKey, that.propKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(application, profile, label, propKey);
    }
}
