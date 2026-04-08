package com.macs.authserver.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("GROUP_INFO")
public class GroupInfo {

    @Id
    @Column("GROUP_ID")
    private Long groupId;

    @Column("SYSTEM_NAME")
    private String systemName;

    @Column("GROUP_NAME")
    private String groupName;

    protected GroupInfo() {}

    public GroupInfo(String systemName, String groupName) {
        this.systemName = systemName;
        this.groupName = groupName;
    }

    public Long getGroupId() { return groupId; }
    public String getSystemName() { return systemName; }
    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
}
