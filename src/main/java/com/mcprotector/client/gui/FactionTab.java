package com.mcprotector.client.gui;

public enum FactionTab {
    MEMBERS("Members"),
    INVITES("Invites"),
    PERMISSIONS("Permissions"),
    RELATIONS("Relations"),
    CLAIMS("Claims");

    private final String label;

    FactionTab(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
