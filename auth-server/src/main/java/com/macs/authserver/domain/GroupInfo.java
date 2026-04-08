package com.macs.authserver.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("GROUP_INFO")
public class GroupInfo implements Persistable<String> {

    @Id
    @Column("GROUP_ID")
    private String groupId;

    @Column("APP_ID")
    private String appId;

    @Column("GROUP_NAME")
    private String groupName;

    @Transient
    private boolean newEntity;

    protected GroupInfo() {
    }

    public GroupInfo(String groupId, String appId, String groupName) {
        this.groupId = groupId;
        this.appId = appId;
        this.groupName = groupName;
        this.newEntity = true;
    }

    @Override
    public String getId() {
        return groupId;
    }

    @Override
    @Transient
    public boolean isNew() {
        return newEntity;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getAppId() {
        return appId;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }
}
