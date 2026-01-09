package com.mcprotector.client.gui;

import com.mcprotector.client.FactionClientData;
import com.mcprotector.client.FactionMapClientData;
import com.mcprotector.data.FactionPermission;
import com.mcprotector.data.FactionRole;
import com.mcprotector.network.FactionActionPacket;
import com.mcprotector.network.FactionClaimMapActionPacket;
import com.mcprotector.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class FactionMainScreen extends Screen {
    private static final int TAB_BUTTON_HEIGHT = 18;
    private static final int TAB_BUTTON_WIDTH = 72;
    private static final int PANEL_PADDING = 16;
    private static final DateTimeFormatter INVITE_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
        .withZone(ZoneId.systemDefault());

    private FactionTab selectedTab = FactionTab.MEMBERS;
    private EditBox inviteNameField;
    private Button inviteButton;
    private Button roleButton;
    private Button permissionButton;
    private Button grantButton;
    private Button revokeButton;
    private Button refreshButton;
    private Button dynmapSyncButton;
    private int roleIndex;
    private int permissionIndex;
    private int panelTop;
    private int permissionsScrollOffset;
    private int mapClaimsScrollOffset;

    public FactionMainScreen() {
        super(Component.literal("Faction"));
    }

    @Override
    protected void init() {
        super.init();
        int startX = (this.width - (FactionTab.values().length * (TAB_BUTTON_WIDTH + 4))) / 2;
        int y = 34;
        for (FactionTab tab : FactionTab.values()) {
            int x = startX + tab.ordinal() * (TAB_BUTTON_WIDTH + 4);
            this.addRenderableWidget(Button.builder(Component.literal(tab.getLabel()), button -> {
                selectedTab = tab;
                updateVisibility();
            }).bounds(x, y, TAB_BUTTON_WIDTH, TAB_BUTTON_HEIGHT).build());
        }
        panelTop = y + TAB_BUTTON_HEIGHT + 10;
        inviteNameField = new EditBox(this.font, PANEL_PADDING, panelTop + 30, 140, 18, Component.literal("Player name"));
        inviteNameField.setMaxLength(32);
        this.addRenderableWidget(inviteNameField);
        inviteButton = this.addRenderableWidget(Button.builder(Component.literal("Send Invite"), button -> sendInvite())
            .bounds(PANEL_PADDING + 150, panelTop + 28, 100, 20)
            .build());

        roleButton = this.addRenderableWidget(Button.builder(Component.literal("Role: " + currentRole().name()), button -> {
            roleIndex = (roleIndex + 1) % FactionRole.values().length;
            updatePermissionLabels();
        }).bounds(PANEL_PADDING, panelTop + 30, 140, 20).build());
        permissionButton = this.addRenderableWidget(Button.builder(Component.literal("Perm: " + currentPermission().name()), button -> {
            permissionIndex = (permissionIndex + 1) % FactionPermission.values().length;
            updatePermissionLabels();
        }).bounds(PANEL_PADDING + 150, panelTop + 30, 140, 20).build());
        grantButton = this.addRenderableWidget(Button.builder(Component.literal("Grant"), button -> sendPermission(true))
            .bounds(PANEL_PADDING, panelTop + 55, 70, 20)
            .build());
        revokeButton = this.addRenderableWidget(Button.builder(Component.literal("Revoke"), button -> sendPermission(false))
            .bounds(PANEL_PADDING + 80, panelTop + 55, 70, 20)
            .build());

        refreshButton = this.addRenderableWidget(Button.builder(Component.literal("Refresh"), button -> {
            FactionClientData.requestUpdate();
            FactionMapClientData.requestUpdate();
        })
            .bounds(this.width - PANEL_PADDING - 80, this.height - PANEL_PADDING - 20, 80, 20)
            .build());
        dynmapSyncButton = this.addRenderableWidget(Button.builder(Component.literal("Sync Dynmap"), button -> sendDynmapSync())
            .bounds(PANEL_PADDING, this.height - PANEL_PADDING - 20, 110, 20)
            .build());

        updateVisibility();
        FactionClientData.requestUpdate();
        FactionMapClientData.requestUpdate();
    }

    @Override
    public void tick() {
        super.tick();
        if (inviteNameField != null) {
            inviteNameField.tick();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (inviteNameField != null && inviteNameField.isFocused()) {
            return inviteNameField.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (inviteNameField != null && inviteNameField.isFocused()) {
            return inviteNameField.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        FactionClientData.FactionSnapshot snapshot = FactionClientData.getSnapshot();
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 6, 0xFFFFFF);
        guiGraphics.drawString(this.font, snapshot.inFaction() ? "Faction: " + snapshot.factionName() + " (" + snapshot.roleName() + ")"
            : "No faction", PANEL_PADDING, 18, 0xCCCCCC);
        int contentStart = selectedTab == FactionTab.INVITES
            || selectedTab == FactionTab.PERMISSIONS
            || selectedTab == FactionTab.FACTION_MAP
            ? panelTop + 85
            : 80;
        switch (selectedTab) {
            case MEMBERS -> renderMembers(guiGraphics, snapshot.members(), contentStart);
            case INVITES -> renderInvites(guiGraphics, snapshot, contentStart);
            case PERMISSIONS -> renderPermissions(guiGraphics, snapshot.permissions(), contentStart);
            case RELATIONS -> renderRelations(guiGraphics, snapshot.relations(), contentStart);
            case FACTION_MAP -> renderFactionMap(guiGraphics, snapshot, contentStart, mouseX, mouseY);
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (selectedTab == FactionTab.PERMISSIONS) {
            int lineHeight = 10;
            int listStart = panelTop + 85 + 22;
            int availableHeight = Math.max(0, this.height - listStart - 30);
            int visibleLines = Math.max(1, availableHeight / lineHeight);
            int maxOffset = Math.max(0, getSelectedPermissions().size() - visibleLines);
            if (delta < 0) {
                permissionsScrollOffset = Math.min(maxOffset, permissionsScrollOffset + 1);
            } else if (delta > 0) {
                permissionsScrollOffset = Math.max(0, permissionsScrollOffset - 1);
            }
            return true;
        }
        if (selectedTab == FactionTab.FACTION_MAP) {
            FactionMapClientData.MapSnapshot mapSnapshot = FactionMapClientData.getSnapshot();
            FactionMapRenderer.MapRegion region = FactionMapRenderer.buildMapRegion(panelTop + 85, mapSnapshot.radius(),
                this.width, this.height, PANEL_PADDING);
            int listStart = FactionMapRenderer.getMapClaimsListStart(region);
            int lineHeight = 10;
            int availableHeight = Math.max(0, this.height - listStart - 30);
            int visibleLines = Math.max(1, availableHeight / lineHeight);
            int maxOffset = Math.max(0, FactionClientData.getSnapshot().claims().size() - visibleLines);
            if (delta < 0) {
                mapClaimsScrollOffset = Math.min(maxOffset, mapClaimsScrollOffset + 1);
            } else if (delta > 0) {
                mapClaimsScrollOffset = Math.max(0, mapClaimsScrollOffset - 1);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (selectedTab == FactionTab.FACTION_MAP && button == 0) {
            FactionMapClientData.MapSnapshot mapSnapshot = FactionMapClientData.getSnapshot();
            FactionMapRenderer.MapRegion region = FactionMapRenderer.buildMapRegion(panelTop + 85, mapSnapshot.radius(),
                this.width, this.height, PANEL_PADDING);
            ChunkPos clicked = FactionMapRenderer.getChunkFromMouse(region, mouseX, mouseY, mapSnapshot);
            if (clicked != null) {
                handleMapClick(mapSnapshot, clicked);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        super.onClose();
        Minecraft.getInstance().setScreen(null);
    }

    private void updateVisibility() {
        boolean invites = selectedTab == FactionTab.INVITES;
        boolean permissions = selectedTab == FactionTab.PERMISSIONS;
        inviteNameField.setVisible(invites);
        inviteButton.visible = invites;
        dynmapSyncButton.visible = selectedTab == FactionTab.FACTION_MAP;
        roleButton.visible = permissions;
        permissionButton.visible = permissions;
        grantButton.visible = permissions;
        revokeButton.visible = permissions;
        if (selectedTab == FactionTab.FACTION_MAP) {
            mapClaimsScrollOffset = 0;
            FactionMapClientData.requestUpdate();
        }
    }

    private void updatePermissionLabels() {
        roleButton.setMessage(Component.literal("Role: " + currentRole().name()));
        permissionButton.setMessage(Component.literal("Perm: " + currentPermission().name()));
        permissionsScrollOffset = 0;
    }

    private FactionRole currentRole() {
        return FactionRole.values()[roleIndex % FactionRole.values().length];
    }

    private FactionPermission currentPermission() {
        return FactionPermission.values()[permissionIndex % FactionPermission.values().length];
    }

    private void sendInvite() {
        String name = inviteNameField.getValue().trim();
        if (!name.isEmpty()) {
            NetworkHandler.CHANNEL.sendToServer(FactionActionPacket.invite(name));
            inviteNameField.setValue("");
        }
    }

    private void sendPermission(boolean grant) {
        NetworkHandler.CHANNEL.sendToServer(
            FactionActionPacket.setPermission(currentRole().name(), currentPermission().name(), grant)
        );
    }

    private void sendDynmapSync() {
        NetworkHandler.CHANNEL.sendToServer(FactionActionPacket.syncDynmap());
    }

    private void renderMembers(GuiGraphics guiGraphics, List<com.mcprotector.network.FactionStatePacket.MemberEntry> members, int startY) {
        guiGraphics.drawString(this.font, "Members:", PANEL_PADDING, startY, 0xFFFFFF);
        int y = startY + 12;
        for (var member : members) {
            guiGraphics.drawString(this.font, member.name() + " - " + member.role(), PANEL_PADDING, y, 0xCCCCCC);
            y += 10;
        }
    }

    private void renderInvites(GuiGraphics guiGraphics, FactionClientData.FactionSnapshot snapshot, int startY) {
        guiGraphics.drawString(this.font, "Invites:", PANEL_PADDING, startY, 0xFFFFFF);
        int y = startY + 12;
        if (!snapshot.inFaction()) {
            if (!snapshot.pendingInviteFaction().isEmpty()) {
                guiGraphics.drawString(this.font, "Pending invite to: " + snapshot.pendingInviteFaction(), PANEL_PADDING, y, 0xCCCCCC);
            } else {
                guiGraphics.drawString(this.font, "No pending invites.", PANEL_PADDING, y, 0x777777);
            }
            return;
        }
        if (snapshot.invites().isEmpty()) {
            guiGraphics.drawString(this.font, "No outgoing invites.", PANEL_PADDING, y, 0x777777);
            return;
        }
        for (var invite : snapshot.invites()) {
            String expiry = INVITE_TIME_FORMAT.format(Instant.ofEpochMilli(invite.expiresAt()));
            guiGraphics.drawString(this.font, invite.name() + " (expires " + expiry + ")", PANEL_PADDING, y, 0xCCCCCC);
            y += 10;
        }
    }

    private void renderPermissions(GuiGraphics guiGraphics, List<com.mcprotector.network.FactionStatePacket.PermissionEntry> permissions, int startY) {
        guiGraphics.drawString(this.font, "Permissions:", PANEL_PADDING, startY, 0xFFFFFF);
        String selectedRole = currentRole().name();
        com.mcprotector.network.FactionStatePacket.PermissionEntry selected = permissions.stream()
            .filter(entry -> entry.role().equalsIgnoreCase(selectedRole))
            .findFirst()
            .orElse(null);
        int y = startY + 12;
        if (selected == null) {
            guiGraphics.drawString(this.font, "No permissions found for " + selectedRole + ".", PANEL_PADDING, y, 0x777777);
            return;
        }
        guiGraphics.drawString(this.font, selectedRole + ":", PANEL_PADDING, y, 0xCCCCCC);
        y += 10;
        int lineHeight = 10;
        int availableHeight = Math.max(0, this.height - y - 30);
        int visibleLines = Math.max(1, availableHeight / lineHeight);
        int maxOffset = Math.max(0, selected.permissions().size() - visibleLines);
        permissionsScrollOffset = Math.min(permissionsScrollOffset, maxOffset);
        List<String> visiblePerms = selected.permissions()
            .subList(permissionsScrollOffset, Math.min(selected.permissions().size(), permissionsScrollOffset + visibleLines));
        for (String perm : visiblePerms) {
            guiGraphics.drawString(this.font, "- " + perm, PANEL_PADDING, y, 0xAAAAAA);
            y += lineHeight;
        }
        if (selected.permissions().size() > visibleLines) {
            guiGraphics.drawString(this.font, "Scroll to view more...", PANEL_PADDING, this.height - 25, 0x777777);
        }
    }

    private List<String> getSelectedPermissions() {
        return FactionClientData.getSnapshot().permissions().stream()
            .filter(entry -> entry.role().equalsIgnoreCase(currentRole().name()))
            .findFirst()
            .map(com.mcprotector.network.FactionStatePacket.PermissionEntry::permissions)
            .orElse(List.of());
    }

    private void renderRelations(GuiGraphics guiGraphics, List<com.mcprotector.network.FactionStatePacket.RelationEntry> relations, int startY) {
        guiGraphics.drawString(this.font, "Relations:", PANEL_PADDING, startY, 0xFFFFFF);
        int y = startY + 12;
        if (relations.isEmpty()) {
            guiGraphics.drawString(this.font, "No active relations.", PANEL_PADDING, y, 0x777777);
            return;
        }
        for (var relation : relations) {
            guiGraphics.drawString(this.font, relation.factionName() + " - " + relation.relation(), PANEL_PADDING, y, 0xCCCCCC);
            y += 10;
        }
    }

    private void renderFactionMap(GuiGraphics guiGraphics, FactionClientData.FactionSnapshot snapshot, int startY, int mouseX, int mouseY) {
        FactionMapClientData.MapSnapshot mapSnapshot = FactionMapClientData.getSnapshot();
        int radius = mapSnapshot.radius();
        guiGraphics.drawString(this.font, "Faction Map:", PANEL_PADDING, startY, 0xFFFFFF);
        if (radius <= 0) {
            guiGraphics.drawString(this.font, "Map data unavailable. Click Refresh.", PANEL_PADDING, startY + 14, 0x777777);
            return;
        }
        FactionMapRenderer.MapRegion region = FactionMapRenderer.buildMapRegion(startY, radius, this.width, this.height, PANEL_PADDING);
        FactionMapRenderer.renderMapGrid(guiGraphics, mapSnapshot, region);
        ChunkPos hovered = FactionMapRenderer.getChunkFromMouse(region, mouseX, mouseY, mapSnapshot);
        if (hovered != null) {
            FactionMapRenderer.renderMapTooltip(guiGraphics, mapSnapshot, hovered, mouseX, mouseY, this.font);
        }
        renderMapClaimsList(guiGraphics, snapshot.claims(), region, mapClaimsScrollOffset);
    }

    private void renderMapClaimsList(GuiGraphics guiGraphics, List<com.mcprotector.network.FactionStatePacket.ClaimEntry> claims,
                                     FactionMapRenderer.MapRegion region, int scrollOffset) {
        int startY = region.originY() + (region.cellSize() * (region.radius() * 2 + 1)) + 12;
        guiGraphics.drawString(this.font, "Claims:", PANEL_PADDING, startY, 0xFFFFFF);
        int y = startY + 12;
        if (claims.isEmpty()) {
            guiGraphics.drawString(this.font, "No claims.", PANEL_PADDING, y, 0x777777);
            return;
        }
        int lineHeight = 10;
        int availableHeight = Math.max(0, this.height - y - 30);
        int visibleLines = Math.max(1, availableHeight / lineHeight);
        int maxOffset = Math.max(0, claims.size() - visibleLines);
        mapClaimsScrollOffset = Math.min(scrollOffset, maxOffset);
        List<com.mcprotector.network.FactionStatePacket.ClaimEntry> visibleClaims = claims
            .subList(mapClaimsScrollOffset, Math.min(claims.size(), mapClaimsScrollOffset + visibleLines));
        for (var claim : visibleClaims) {
            guiGraphics.drawString(this.font, "Chunk " + claim.chunkX() + ", " + claim.chunkZ(), PANEL_PADDING, y, 0xCCCCCC);
            y += lineHeight;
        }
        if (claims.size() > visibleLines) {
            guiGraphics.drawString(this.font, "Scroll to view more...", PANEL_PADDING, this.height - 25, 0x777777);
        }
    }

    private void handleMapClick(FactionMapClientData.MapSnapshot mapSnapshot, ChunkPos clicked) {
        long key = clicked.toLong();
        com.mcprotector.network.FactionClaimMapPacket.ClaimEntry entry = mapSnapshot.claims().get(key);
        FactionClaimMapActionPacket.ActionType action = entry == null
            ? FactionClaimMapActionPacket.ActionType.CLAIM
            : "OWN".equals(entry.relation())
            ? FactionClaimMapActionPacket.ActionType.UNCLAIM
            : FactionClaimMapActionPacket.ActionType.OVERTAKE;
        NetworkHandler.CHANNEL.sendToServer(new FactionClaimMapActionPacket(clicked.x, clicked.z, action));
        FactionMapClientData.requestUpdate();
    }

    private void renderMapGrid(GuiGraphics guiGraphics, FactionMapClientData.MapSnapshot mapSnapshot, MapRegion region) {
        int radius = region.radius();
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int chunkX = mapSnapshot.centerChunkX() + dx;
                int chunkZ = mapSnapshot.centerChunkZ() + dz;
                int x = region.originX() + (dx + radius) * region.cellSize();
                int y = region.originY() + (dz + radius) * region.cellSize();
                long key = new ChunkPos(chunkX, chunkZ).toLong();
                com.mcprotector.network.FactionClaimMapPacket.ClaimEntry entry = mapSnapshot.claims().get(key);
                int color = getMapColor(entry);
                guiGraphics.fill(x, y, x + region.cellSize(), y + region.cellSize(), color);
            }
        }
        int centerX = region.originX() + radius * region.cellSize();
        int centerY = region.originY() + radius * region.cellSize();
        guiGraphics.renderOutline(centerX, centerY, region.cellSize(), region.cellSize(), 0xFFFFFFFF);
    }

    private int getMapColor(com.mcprotector.network.FactionClaimMapPacket.ClaimEntry entry) {
        if (entry == null) {
            return 0xFF3A3A3A;
        }
        return switch (entry.relation()) {
            case "OWN" -> 0xFF4CAF50;
            case "ALLY" -> 0xFF4FC3F7;
            case "WAR" -> 0xFFEF5350;
            default -> 0xFF8D8D8D;
        };
    }

    private void renderMapTooltip(GuiGraphics guiGraphics, FactionMapClientData.MapSnapshot mapSnapshot, ChunkPos hovered,
                                  int mouseX, int mouseY) {
        long key = hovered.toLong();
        com.mcprotector.network.FactionClaimMapPacket.ClaimEntry entry = mapSnapshot.claims().get(key);
        List<Component> lines;
        if (entry == null) {
            lines = List.of(Component.literal("Wilderness"));
        } else {
            String relation = entry.relation().equals("OWN") ? "Your faction" : entry.relation();
            lines = List.of(
                Component.literal(entry.factionName()),
                Component.literal(relation)
            );
        }
        List<net.minecraft.util.FormattedCharSequence> tooltip = lines.stream()
            .map(Component::getVisualOrderText)
            .toList();
        guiGraphics.renderTooltip(this.font, tooltip, mouseX, mouseY);
    }

    private void handleMapClick(FactionMapClientData.MapSnapshot mapSnapshot, ChunkPos clicked) {
        long key = clicked.toLong();
        com.mcprotector.network.FactionClaimMapPacket.ClaimEntry entry = mapSnapshot.claims().get(key);
        FactionClaimMapActionPacket.ActionType action = entry == null
            ? FactionClaimMapActionPacket.ActionType.CLAIM
            : "OWN".equals(entry.relation())
            ? FactionClaimMapActionPacket.ActionType.UNCLAIM
            : FactionClaimMapActionPacket.ActionType.OVERTAKE;
        NetworkHandler.CHANNEL.sendToServer(new FactionClaimMapActionPacket(clicked.x, clicked.z, action));
        FactionMapClientData.requestUpdate();
    }

    private MapRegion buildMapRegion(int startY, int radius) {
        int gridSize = radius * 2 + 1;
        int maxWidth = this.width - PANEL_PADDING * 2;
        int maxHeight = this.height - startY - 40;
        int cellSize = Math.max(6, Math.min(18, Math.min(maxWidth / gridSize, maxHeight / gridSize)));
        int mapWidth = cellSize * gridSize;
        int mapHeight = cellSize * gridSize;
        int originX = (this.width - mapWidth) / 2;
        int originY = startY + 16;
        if (originY + mapHeight > this.height - PANEL_PADDING - 30) {
            originY = Math.max(startY + 16, this.height - PANEL_PADDING - 30 - mapHeight);
        }
        return new MapRegion(originX, originY, cellSize, radius);
    }

    private ChunkPos getChunkFromMouse(MapRegion region, double mouseX, double mouseY) {
        if (region == null) {
            return null;
        }
        int size = region.cellSize() * (region.radius() * 2 + 1);
        if (mouseX < region.originX() || mouseY < region.originY()
            || mouseX >= region.originX() + size || mouseY >= region.originY() + size) {
            return null;
        }
        int dx = (int) ((mouseX - region.originX()) / region.cellSize()) - region.radius();
        int dz = (int) ((mouseY - region.originY()) / region.cellSize()) - region.radius();
        FactionMapClientData.MapSnapshot mapSnapshot = FactionMapClientData.getSnapshot();
        return new ChunkPos(mapSnapshot.centerChunkX() + dx, mapSnapshot.centerChunkZ() + dz);
    }

    private record MapRegion(int originX, int originY, int cellSize, int radius) {
    }
}
