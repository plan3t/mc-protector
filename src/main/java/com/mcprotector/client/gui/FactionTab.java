package com.mcprotector.client.gui;

public enum FactionTab {
    FACTION("Faction", "Faction"),
    SERVER_RANKING("Server Ranking", "Ranking"),
    FACTION_MAP("Map", "Map"),
    SETTINGS("Settings", "Settings");

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
