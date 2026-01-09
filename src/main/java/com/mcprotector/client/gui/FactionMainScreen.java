package com.mcprotector.client.gui;

import com.mcprotector.client.FactionClientData;
import com.mcprotector.data.FactionPermission;
import com.mcprotector.data.FactionRole;
import com.mcprotector.network.FactionActionPacket;
import com.mcprotector.network.NetworkHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class FactionMainScreen extends Screen {
    private static final int TAB_BUTTON_HEIGHT = 20;
    private static final int TAB_BUTTON_WIDTH = 90;
    private static final int PANEL_PADDING = 16;
    private static final DateTimeFormatter INVITE_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
        .withZone(ZoneId.systemDefault());

    private FactionTab selectedTab = FactionTab.MEMBERS;
    private EditBox inviteNameField;
    private Button inviteButton;
    private Button claimButton;
    private Button unclaimButton;
    private Button roleButton;
    private Button permissionButton;
    private Button grantButton;
    private Button revokeButton;
    private Button refreshButton;
    private int roleIndex;
    private int permissionIndex;
    private int panelTop;

    public FactionMainScreen() {
        super(Component.literal("Faction"));
    }

    @Override
    protected void init() {
        super.init();
        int startX = (this.width - (FactionTab.values().length * (TAB_BUTTON_WIDTH + 4))) / 2;
        int y = 20;
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

        claimButton = this.addRenderableWidget(Button.builder(Component.literal("Claim Chunk"), button -> sendClaim())
            .bounds(PANEL_PADDING, panelTop + 30, 110, 20)
            .build());
        unclaimButton = this.addRenderableWidget(Button.builder(Component.literal("Unclaim Chunk"), button -> sendUnclaim())
            .bounds(PANEL_PADDING + 120, panelTop + 30, 120, 20)
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

        refreshButton = this.addRenderableWidget(Button.builder(Component.literal("Refresh"), button -> FactionClientData.requestUpdate())
            .bounds(this.width - PANEL_PADDING - 80, this.height - PANEL_PADDING - 20, 80, 20)
            .build());

        updateVisibility();
        FactionClientData.requestUpdate();
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
            : "No faction", PANEL_PADDING, 48, 0xCCCCCC);
        int contentStart = selectedTab == FactionTab.INVITES
            || selectedTab == FactionTab.PERMISSIONS
            || selectedTab == FactionTab.CLAIMS
            ? panelTop + 85
            : 80;
        switch (selectedTab) {
            case MEMBERS -> renderMembers(guiGraphics, snapshot.members(), contentStart);
            case INVITES -> renderInvites(guiGraphics, snapshot, contentStart);
            case PERMISSIONS -> renderPermissions(guiGraphics, snapshot.permissions(), contentStart);
            case RELATIONS -> renderRelations(guiGraphics, snapshot.relations(), contentStart);
            case CLAIMS -> renderClaims(guiGraphics, snapshot.claims(), contentStart);
        }
        super.render(guiGraphics, mouseX, mouseY, partialTick);
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
        boolean claims = selectedTab == FactionTab.CLAIMS;
        inviteNameField.setVisible(invites);
        inviteButton.visible = invites;
        claimButton.visible = claims;
        unclaimButton.visible = claims;
        roleButton.visible = permissions;
        permissionButton.visible = permissions;
        grantButton.visible = permissions;
        revokeButton.visible = permissions;
    }

    private void updatePermissionLabels() {
        roleButton.setMessage(Component.literal("Role: " + currentRole().name()));
        permissionButton.setMessage(Component.literal("Perm: " + currentPermission().name()));
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

    private void sendClaim() {
        NetworkHandler.CHANNEL.sendToServer(FactionActionPacket.claim());
    }

    private void sendUnclaim() {
        NetworkHandler.CHANNEL.sendToServer(FactionActionPacket.unclaim());
    }

    private void sendPermission(boolean grant) {
        NetworkHandler.CHANNEL.sendToServer(
            FactionActionPacket.setPermission(currentRole().name(), currentPermission().name(), grant)
        );
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
        int y = startY + 12;
        String selectedRole = currentRole().name();
        com.mcprotector.network.FactionStatePacket.PermissionEntry selected = permissions.stream()
            .filter(entry -> entry.role().equalsIgnoreCase(selectedRole))
            .findFirst()
            .orElse(null);
        if (selected == null) {
            guiGraphics.drawString(this.font, "No permissions found for " + selectedRole + ".", PANEL_PADDING, y, 0x777777);
            return;
        }
        guiGraphics.drawString(this.font, selectedRole + ":", PANEL_PADDING, y, 0xCCCCCC);
        y += 10;
        for (String perm : selected.permissions()) {
            guiGraphics.drawString(this.font, "- " + perm, PANEL_PADDING, y, 0xAAAAAA);
            y += 10;
        }
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

    private void renderClaims(GuiGraphics guiGraphics, List<com.mcprotector.network.FactionStatePacket.ClaimEntry> claims, int startY) {
        guiGraphics.drawString(this.font, "Claims:", PANEL_PADDING, startY, 0xFFFFFF);
        int y = startY + 12;
        if (claims.isEmpty()) {
            guiGraphics.drawString(this.font, "No claims.", PANEL_PADDING, y, 0x777777);
            return;
        }
        int count = 0;
        for (var claim : claims) {
            if (count >= 12) {
                guiGraphics.drawString(this.font, "...and " + (claims.size() - count) + " more", PANEL_PADDING, y, 0x777777);
                break;
            }
            guiGraphics.drawString(this.font, "Chunk " + claim.chunkX() + ", " + claim.chunkZ(), PANEL_PADDING, y, 0xCCCCCC);
            y += 10;
            count++;
        }
    }
}
