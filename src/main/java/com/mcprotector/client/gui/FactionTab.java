package com.mcprotector.client.gui;

public enum FactionTab {
    MEMBERS("Members", "Members"),
    PERMISSIONS("Permissions", "Perms"),
    RULES("Rules", "Rules"),
    RELATIONS("Relations", "Relations"),
    FACTION_LIST("Factions", "List"),
    FACTION_MAP("Faction Map", "Map");

    private final String label;
    private final String compactLabel;

    FactionTab(String label, String compactLabel) {
        this.label = label;
        this.compactLabel = compactLabel;
    }

    public String getLabel() {
        return label;
    }

    public String getCompactLabel() {
        return compactLabel;
    }
}
