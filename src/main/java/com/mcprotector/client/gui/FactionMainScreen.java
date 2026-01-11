package com.mcprotector.client.gui;

import com.mcprotector.client.FactionClientData;
import com.mcprotector.client.FactionMapClientData;
import com.mcprotector.config.FactionConfig;
import com.mcprotector.data.FactionPermission;
import com.mcprotector.data.FactionRole;
import com.mcprotector.network.FactionActionPacket;
import com.mcprotector.network.FactionClaimMapActionPacket;
import com.mcprotector.network.FactionSafeZoneMapActionPacket;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FactionMainScreen extends Screen {
    private static final int TAB_BUTTON_HEIGHT = 18;
    private static final int TAB_BUTTON_WIDTH = 72;
    private static final int PANEL_PADDING = 16;
    private static final DateTimeFormatter INVITE_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
        .withZone(ZoneId.systemDefault());

    private FactionTab selectedTab = FactionTab.MEMBERS;
    private EditBox inviteNameField;
    private Button inviteButton;
    private Button joinInviteButton;
    private Button declineInviteButton;
    private Button roleButton;
    private Button permissionButton;
    private Button grantButton;
    private Button revokeButton;
    private EditBox memberNameField;
    private Button kickMemberButton;
    private Button promoteMemberButton;
    private Button demoteMemberButton;
    private Button leaveFactionButton;
    private Button refreshButton;
    private Button dynmapSyncButton;
    private EditBox safeZoneFactionField;
    private Button safeZoneClaimButton;
    private Button safeZoneUnclaimButton;
    private int roleIndex;
    private int permissionIndex;
    private int panelTop;
    private int permissionsScrollOffset;
    private int mapClaimsScrollOffset;
    private boolean safeZoneSelectionMode;
    private boolean safeZoneUnclaimMode;
    private boolean safeZoneSelecting;
    private final Set<ChunkPos> safeZoneSelection = new HashSet<>();

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
        joinInviteButton = this.addRenderableWidget(Button.builder(Component.literal("Join Faction"), button -> acceptInvite())
            .bounds(PANEL_PADDING, panelTop + 55, 110, 20)
            .build());
        declineInviteButton = this.addRenderableWidget(Button.builder(Component.literal("Decline"), button -> declineInvite())
            .bounds(PANEL_PADDING + 120, panelTop + 55, 80, 20)
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

        memberNameField = new EditBox(this.font, PANEL_PADDING, panelTop + 40, 140, 18, Component.literal("Member name"));
        memberNameField.setMaxLength(32);
        this.addRenderableWidget(memberNameField);
        kickMemberButton = this.addRenderableWidget(Button.builder(Component.literal("Kick"), button -> sendMemberAction(MemberAction.KICK))
            .bounds(PANEL_PADDING + 150, panelTop + 40, 60, 20)
            .build());
        promoteMemberButton = this.addRenderableWidget(Button.builder(Component.literal("Promote"), button -> sendMemberAction(MemberAction.PROMOTE))
            .bounds(PANEL_PADDING + 215, panelTop + 40, 70, 20)
            .build());
        demoteMemberButton = this.addRenderableWidget(Button.builder(Component.literal("Demote"), button -> sendMemberAction(MemberAction.DEMOTE))
            .bounds(PANEL_PADDING + 290, panelTop + 40, 70, 20)
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
        leaveFactionButton = this.addRenderableWidget(Button.builder(Component.literal("Leave Faction"), button -> leaveFaction())
            .bounds(PANEL_PADDING, this.height - PANEL_PADDING - 20, 110, 20)
            .build());
        safeZoneFactionField = new EditBox(this.font, PANEL_PADDING, panelTop + 10, 140, 18,
            Component.literal("Safe zone faction"));
        safeZoneFactionField.setMaxLength(32);
        this.addRenderableWidget(safeZoneFactionField);
        safeZoneClaimButton = this.addRenderableWidget(Button.builder(Component.literal("Safe Zone Claim"), button -> toggleSafeZoneMode(false))
            .bounds(PANEL_PADDING + 150, panelTop + 8, 130, 20)
            .build());
        safeZoneUnclaimButton = this.addRenderableWidget(Button.builder(Component.literal("Safe Zone Unclaim"), button -> toggleSafeZoneMode(true))
            .bounds(PANEL_PADDING + 285, panelTop + 8, 150, 20)
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
        if (memberNameField != null) {
            memberNameField.tick();
        }
        if (safeZoneFactionField != null) {
            safeZoneFactionField.tick();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (inviteNameField != null && inviteNameField.isFocused()) {
            return inviteNameField.keyPressed(keyCode, scanCode, modifiers);
        }
        if (memberNameField != null && memberNameField.isFocused()) {
            return memberNameField.keyPressed(keyCode, scanCode, modifiers);
        }
        if (safeZoneFactionField != null && safeZoneFactionField.isFocused()) {
            return safeZoneFactionField.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (inviteNameField != null && inviteNameField.isFocused()) {
            return inviteNameField.charTyped(codePoint, modifiers);
        }
        if (memberNameField != null && memberNameField.isFocused()) {
            return memberNameField.charTyped(codePoint, modifiers);
        }
        if (safeZoneFactionField != null && safeZoneFactionField.isFocused()) {
            return safeZoneFactionField.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        FactionClientData.FactionSnapshot snapshot = FactionClientData.getSnapshot();
        updateDynamicVisibility(snapshot);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 6, 0xFFFFFF);
        String headline = snapshot.inFaction()
            ? "Faction: " + snapshot.factionName() + " (" + snapshot.roleName() + ")"
            : "No faction";
        guiGraphics.drawString(this.font, headline, PANEL_PADDING, 18, 0xCCCCCC);
        if (snapshot.inFaction()) {
            String stats = "Level " + snapshot.factionLevel()
                + " | Claims " + snapshot.claimCount() + "/" + snapshot.maxClaims()
                + " | Protection " + snapshot.protectionTier();
            guiGraphics.drawString(this.font, stats, PANEL_PADDING, 28, 0xAAAAAA);
        }
        int contentStart = selectedTab == FactionTab.INVITES
            || selectedTab == FactionTab.PERMISSIONS
            ? panelTop + 85
            : selectedTab == FactionTab.FACTION_MAP
            ? panelTop + 40
            : selectedTab == FactionTab.MEMBERS && snapshot.inFaction()
            ? panelTop + 70
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
            FactionMapRenderer.MapRegion region = FactionMapRenderer.buildMapRegion(panelTop + 40, mapSnapshot.radius(),
                this.width, this.height, PANEL_PADDING);
            int listStart = FactionMapRenderer.getMapClaimsListStart(region);
            if (mouseY >= listStart) {
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
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (selectedTab == FactionTab.FACTION_MAP && button == 0) {
            FactionMapClientData.MapSnapshot mapSnapshot = FactionMapClientData.getSnapshot();
            FactionMapRenderer.MapRegion region = FactionMapRenderer.buildMapRegion(panelTop + 40, mapSnapshot.radius(),
                this.width, this.height, PANEL_PADDING);
            ChunkPos clicked = FactionMapRenderer.getChunkFromMouse(region, mouseX, mouseY, mapSnapshot);
            if (clicked != null) {
                if (safeZoneSelectionMode) {
                    startSafeZoneSelection(clicked);
                    return true;
                }
                long key = clicked.toLong();
                com.mcprotector.network.FactionClaimMapPacket.ClaimEntry entry = mapSnapshot.claims().get(key);
                if (entry != null && entry.safeZone()) {
                    return true;
                }
                FactionClaimMapActionPacket.ActionType action = entry == null
                    ? FactionClaimMapActionPacket.ActionType.CLAIM
                    : "OWN".equals(entry.relation())
                    ? FactionClaimMapActionPacket.ActionType.UNCLAIM
                    : FactionClaimMapActionPacket.ActionType.OVERTAKE;
                NetworkHandler.CHANNEL.sendToServer(new FactionClaimMapActionPacket(clicked.x, clicked.z, action));
                FactionMapClientData.requestUpdate();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (selectedTab == FactionTab.FACTION_MAP && button == 0 && safeZoneSelecting) {
            FactionMapClientData.MapSnapshot mapSnapshot = FactionMapClientData.getSnapshot();
            FactionMapRenderer.MapRegion region = FactionMapRenderer.buildMapRegion(panelTop + 40, mapSnapshot.radius(),
                this.width, this.height, PANEL_PADDING);
            ChunkPos hovered = FactionMapRenderer.getChunkFromMouse(region, mouseX, mouseY, mapSnapshot);
            if (hovered != null) {
                addSafeZoneSelection(hovered);
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (selectedTab == FactionTab.FACTION_MAP && button == 0 && safeZoneSelecting) {
            safeZoneSelecting = false;
            if (!safeZoneSelection.isEmpty()) {
                promptSafeZoneConfirm();
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
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
        boolean members = selectedTab == FactionTab.MEMBERS;
        inviteNameField.setVisible(invites);
        inviteButton.visible = invites;
        joinInviteButton.visible = invites;
        declineInviteButton.visible = invites;
        dynmapSyncButton.visible = selectedTab == FactionTab.FACTION_MAP;
        roleButton.visible = permissions;
        permissionButton.visible = permissions;
        grantButton.visible = permissions;
        revokeButton.visible = permissions;
        memberNameField.setVisible(members);
        kickMemberButton.visible = members;
        promoteMemberButton.visible = members;
        demoteMemberButton.visible = members;
        leaveFactionButton.visible = members;
        boolean mapTab = selectedTab == FactionTab.FACTION_MAP;
        safeZoneFactionField.setVisible(mapTab);
        safeZoneClaimButton.visible = mapTab;
        safeZoneUnclaimButton.visible = mapTab;
        if (!mapTab) {
            safeZoneSelectionMode = false;
            safeZoneSelecting = false;
            safeZoneSelection.clear();
        }
        if (selectedTab == FactionTab.FACTION_MAP) {
            mapClaimsScrollOffset = 0;
            FactionMapClientData.requestUpdate();
        }
    }

    private void updateDynamicVisibility(FactionClientData.FactionSnapshot snapshot) {
        boolean invites = selectedTab == FactionTab.INVITES;
        boolean inFaction = snapshot.inFaction();
        inviteNameField.setVisible(invites && inFaction);
        inviteButton.visible = invites && inFaction;
        boolean hasInvite = invites && !snapshot.pendingInviteFaction().isEmpty() && !snapshot.inFaction();
        joinInviteButton.visible = hasInvite;
        declineInviteButton.visible = hasInvite;
        boolean members = selectedTab == FactionTab.MEMBERS;
        memberNameField.setVisible(members && inFaction);
        kickMemberButton.visible = members && inFaction;
        promoteMemberButton.visible = members && inFaction;
        demoteMemberButton.visible = members && inFaction;
        leaveFactionButton.visible = members && inFaction;
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

    private void acceptInvite() {
        String factionName = FactionClientData.getSnapshot().pendingInviteFaction();
        if (!factionName.isEmpty()) {
            NetworkHandler.CHANNEL.sendToServer(FactionActionPacket.joinFaction(factionName));
        }
    }

    private void declineInvite() {
        NetworkHandler.CHANNEL.sendToServer(FactionActionPacket.declineInvite());
    }

    private void sendPermission(boolean grant) {
        NetworkHandler.CHANNEL.sendToServer(
            FactionActionPacket.setPermission(currentRole().name(), currentPermission().name(), grant)
        );
    }

    private void sendDynmapSync() {
        NetworkHandler.CHANNEL.sendToServer(FactionActionPacket.syncDynmap());
    }

    private void sendMemberAction(MemberAction action) {
        String name = memberNameField.getValue().trim();
        if (name.isEmpty()) {
            return;
        }
        switch (action) {
            case KICK -> NetworkHandler.CHANNEL.sendToServer(FactionActionPacket.kickMember(name));
            case PROMOTE -> NetworkHandler.CHANNEL.sendToServer(FactionActionPacket.promoteMember(name));
            case DEMOTE -> NetworkHandler.CHANNEL.sendToServer(FactionActionPacket.demoteMember(name));
        }
        memberNameField.setValue("");
    }

    private void leaveFaction() {
        NetworkHandler.CHANNEL.sendToServer(FactionActionPacket.leaveFaction());
    }

    private void toggleSafeZoneMode(boolean unclaimMode) {
        if (safeZoneSelectionMode && safeZoneUnclaimMode == unclaimMode) {
            safeZoneSelectionMode = false;
        } else {
            safeZoneSelectionMode = true;
            safeZoneUnclaimMode = unclaimMode;
        }
        safeZoneSelection.clear();
    }

    private void startSafeZoneSelection(ChunkPos chunk) {
        safeZoneSelection.clear();
        safeZoneSelecting = true;
        addSafeZoneSelection(chunk);
    }

    private void addSafeZoneSelection(ChunkPos chunk) {
        if (safeZoneSelection.size() >= 9) {
            return;
        }
        safeZoneSelection.add(chunk);
    }

    private void promptSafeZoneConfirm() {
        int count = safeZoneSelection.size();
        String factionName = safeZoneFactionField.getValue().trim();
        Component title = Component.literal("Confirm Safe Zone");
        Component message = safeZoneUnclaimMode
            ? Component.literal("Remove " + count + " safe zone chunk(s)?")
            : Component.literal("Claim " + count + " safe zone chunk(s) for " + factionName + "?");
        Minecraft.getInstance().setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(result -> {
            Minecraft.getInstance().setScreen(this);
            if (result) {
                sendSafeZoneSelection(factionName);
            } else {
                safeZoneSelection.clear();
            }
        }, title, message));
    }

    private void sendSafeZoneSelection(String factionName) {
        List<ChunkPos> chunks = new ArrayList<>(safeZoneSelection);
        if (chunks.isEmpty()) {
            return;
        }
        NetworkHandler.CHANNEL.sendToServer(new FactionSafeZoneMapActionPacket(chunks, factionName,
            safeZoneUnclaimMode ? FactionSafeZoneMapActionPacket.ActionType.UNCLAIM : FactionSafeZoneMapActionPacket.ActionType.CLAIM));
        safeZoneSelection.clear();
        FactionMapClientData.requestUpdate();
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
                guiGraphics.drawString(this.font, "Use the buttons above to respond.", PANEL_PADDING, y + 12, 0x777777);
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
        if (!safeZoneSelection.isEmpty()) {
            FactionMapRenderer.renderSelectionOverlay(guiGraphics, mapSnapshot, region, safeZoneSelection);
        }
        ChunkPos hovered = FactionMapRenderer.getChunkFromMouse(region, mouseX, mouseY, mapSnapshot);
        if (hovered != null) {
            FactionMapRenderer.renderMapTooltip(guiGraphics, mapSnapshot, hovered, mouseX, mouseY, this.font);
        }
        mapClaimsScrollOffset = FactionMapRenderer.renderMapClaimsList(guiGraphics, snapshot.claims(), region,
            mapClaimsScrollOffset, this.height, PANEL_PADDING, this.font);
        if (safeZoneSelectionMode) {
            String modeLabel = safeZoneUnclaimMode ? "Safe Zone Unclaim" : "Safe Zone Claim";
            guiGraphics.drawString(this.font, modeLabel + " (drag up to 9 chunks)", PANEL_PADDING, startY + 14, 0xF9A825);
        }
    }

    private enum MemberAction {
        KICK,
        PROMOTE,
        DEMOTE
    }

}
