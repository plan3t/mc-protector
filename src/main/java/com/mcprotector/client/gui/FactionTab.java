package com.mcprotector.client.gui;

public enum FactionTab {
    MEMBERS("Members"),
    INVITES("Invites"),
    PERMISSIONS("Permissions"),
    RULES("Rules"),
    RELATIONS("Relations"),
    FACTION_MAP("Faction Map");

    private final String label;

    FactionTab(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
