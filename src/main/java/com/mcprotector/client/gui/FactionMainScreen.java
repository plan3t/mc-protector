package com.mcprotector.client.gui;

import com.mcprotector.client.FactionClientData;
import com.mcprotector.client.FactionMapClientData;
import com.mcprotector.config.FactionConfig;
import com.mcprotector.data.FactionPermission;
import com.mcprotector.data.FactionRole;
import com.mcprotector.network.FactionActionPacket;
import com.mcprotector.network.FactionClaimSelectionPacket;
import com.mcprotector.client.ClientNetworkSender;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class FactionMainScreen extends Screen {
    private static final int TAB_BUTTON_HEIGHT = 18;
    private static final int TAB_BUTTON_WIDTH = 72;
    private static final int PANEL_PADDING = 16;
    private static final int CONTROL_TOP_OFFSET = 6;
    private static final int CONTROL_ROW_SPACING = 24;
    private static final int CONTENT_START_OFFSET = 54;
    private static final int BACKDROP_COLOR = 0x80000000;
    private static final int PANEL_BG = 0xD01B1B1B;
    private static final int PANEL_BORDER = 0xFF3B3B3B;
    private static final int PANEL_HIGHLIGHT = 0xFF4A4A4A;
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
    private Button claimTypeButton;
    private Button submitClaimsButton;
    private int roleIndex;
    private int permissionIndex;
    private int panelTop;
    private int permissionsScrollOffset;
    private int mapClaimsScrollOffset;
    private ClaimType selectedClaimType = ClaimType.FACTION;
    private boolean selectionActive;
    private ChunkPos selectionAnchor;
    private final Set<ChunkPos> selectedChunks = new LinkedHashSet<>();

    public FactionMainScreen() {
        super(Component.literal("Faction"));
    }

    @Override
    protected void init() {
        super.init();
        int startX = (this.width - (FactionTab.values().length * (TAB_BUTTON_WIDTH + 4))) / 2;
        int y = 42;
        for (FactionTab tab : FactionTab.values()) {
            int x = startX + tab.ordinal() * (TAB_BUTTON_WIDTH + 4);
            this.addRenderableWidget(Button.builder(Component.literal(tab.getLabel()), button -> {
                selectedTab = tab;
                updateVisibility();
            }).bounds(x, y, TAB_BUTTON_WIDTH, TAB_BUTTON_HEIGHT).build());
        }
        panelTop = y + TAB_BUTTON_HEIGHT + 10;
        int controlRowOne = panelTop + CONTROL_TOP_OFFSET;
        int controlRowTwo = controlRowOne + CONTROL_ROW_SPACING;
        inviteNameField = new EditBox(this.font, PANEL_PADDING, controlRowOne, 140, 18, Component.literal("Player name"));
        inviteNameField.setMaxLength(32);
        this.addRenderableWidget(inviteNameField);
        inviteButton = this.addRenderableWidget(Button.builder(Component.literal("Send Invite"), button -> sendInvite())
            .bounds(PANEL_PADDING + 150, controlRowOne - 2, 100, 20)
            .build());
        joinInviteButton = this.addRenderableWidget(Button.builder(Component.literal("Join Faction"), button -> acceptInvite())
            .bounds(PANEL_PADDING, controlRowTwo, 110, 20)
            .build());
        declineInviteButton = this.addRenderableWidget(Button.builder(Component.literal("Decline"), button -> declineInvite())
            .bounds(PANEL_PADDING + 120, controlRowTwo, 80, 20)
            .build());

        roleButton = this.addRenderableWidget(Button.builder(Component.literal("Role: " + currentRole().name()), button -> {
            roleIndex = (roleIndex + 1) % FactionRole.values().length;
            updatePermissionLabels();
        }).bounds(PANEL_PADDING, controlRowOne, 140, 20).build());
        permissionButton = this.addRenderableWidget(Button.builder(Component.literal("Perm: " + currentPermission().name()), button -> {
            permissionIndex = (permissionIndex + 1) % FactionPermission.values().length;
            updatePermissionLabels();
        }).bounds(PANEL_PADDING + 150, controlRowOne, 140, 20).build());
        grantButton = this.addRenderableWidget(Button.builder(Component.literal("Grant"), button -> sendPermission(true))
            .bounds(PANEL_PADDING, controlRowTwo, 70, 20)
            .build());
        revokeButton = this.addRenderableWidget(Button.builder(Component.literal("Revoke"), button -> sendPermission(false))
            .bounds(PANEL_PADDING + 80, controlRowTwo, 70, 20)
            .build());

        memberNameField = new EditBox(this.font, PANEL_PADDING, controlRowOne + 10, 140, 18, Component.literal("Member name"));
        memberNameField.setMaxLength(32);
        this.addRenderableWidget(memberNameField);
        kickMemberButton = this.addRenderableWidget(Button.builder(Component.literal("Kick"), button -> sendMemberAction(MemberAction.KICK))
            .bounds(PANEL_PADDING + 150, controlRowOne + 8, 60, 20)
            .build());
        promoteMemberButton = this.addRenderableWidget(Button.builder(Component.literal("Promote"), button -> sendMemberAction(MemberAction.PROMOTE))
            .bounds(PANEL_PADDING + 215, controlRowOne + 8, 70, 20)
            .build());
        demoteMemberButton = this.addRenderableWidget(Button.builder(Component.literal("Demote"), button -> sendMemberAction(MemberAction.DEMOTE))
            .bounds(PANEL_PADDING + 290, controlRowOne + 8, 70, 20)
            .build());

        int bottomRowY = this.height - PANEL_PADDING - 20;
        refreshButton = this.addRenderableWidget(Button.builder(Component.literal("Refresh"), button -> {
            FactionClientData.requestUpdate();
            FactionMapClientData.requestUpdate();
        })
            .bounds(this.width - PANEL_PADDING - 80, bottomRowY, 80, 20)
            .build());
        dynmapSyncButton = this.addRenderableWidget(Button.builder(Component.literal("Sync Dynmap"), button -> sendDynmapSync())
            .bounds(PANEL_PADDING, bottomRowY, 110, 20)
            .build());
        leaveFactionButton = this.addRenderableWidget(Button.builder(Component.literal("Leave Faction"), button -> leaveFaction())
            .bounds(PANEL_PADDING, bottomRowY, 110, 20)
            .build());
        safeZoneFactionField = new EditBox(this.font, PANEL_PADDING, controlRowOne, 120, 16,
            Component.literal("Safe zone faction"));
        safeZoneFactionField.setMaxLength(32);
        this.addRenderableWidget(safeZoneFactionField);
        claimTypeButton = this.addRenderableWidget(Button.builder(Component.literal("Claim: " + selectedClaimType.getLabel()),
                button -> cycleClaimType())
            .bounds(PANEL_PADDING + 130, controlRowOne - 2, 120, 16)
            .build());
        submitClaimsButton = this.addRenderableWidget(Button.builder(Component.literal("✓"), button -> promptClaimConfirm())
            .bounds(PANEL_PADDING + 255, controlRowOne - 2, 16, 16)
            .build());

        updateVisibility();
        FactionClientData.requestUpdate();
        FactionMapClientData.requestUpdate();
    }

    @Override
    public void tick() {
        super.tick();
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
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        FactionClientData.FactionSnapshot snapshot = FactionClientData.getSnapshot();
        updateDynamicVisibility(snapshot);
        updateClaimTypeOptions(snapshot);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 6, 0xFFFFFF);
        String headline = snapshot.inFaction()
            ? "Faction: " + snapshot.factionName() + " (" + snapshot.roleName() + ")"
            : "No faction";
        guiGraphics.drawString(this.font, headline, PANEL_PADDING, 18, 0xE0E0E0);
        if (snapshot.inFaction()) {
            String stats = "Level " + snapshot.factionLevel()
                + " | Claims " + snapshot.claimCount() + "/" + snapshot.maxClaims()
                + " | Protection " + snapshot.protectionTier();
            guiGraphics.drawString(this.font, stats, PANEL_PADDING, 28, 0xBDBDBD);
        }
        int contentStart = getContentStart(snapshot);
        switch (selectedTab) {
            case MEMBERS -> renderMembers(guiGraphics, snapshot.members(), contentStart);
            case INVITES -> renderInvites(guiGraphics, snapshot, contentStart);
            case PERMISSIONS -> renderPermissions(guiGraphics, snapshot.permissions(), contentStart);
            case RELATIONS -> renderRelations(guiGraphics, snapshot.relations(), contentStart);
            case FACTION_MAP -> renderFactionMap(guiGraphics, snapshot, contentStart, mouseX, mouseY);
        }
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackdrop(guiGraphics);
        renderPanels(guiGraphics);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (selectedTab == FactionTab.PERMISSIONS) {
            int lineHeight = 10;
            int listStart = panelTop + CONTENT_START_OFFSET + 22;
            int availableHeight = Math.max(0, this.height - listStart - 30);
            int visibleLines = Math.max(1, availableHeight / lineHeight);
            int maxOffset = Math.max(0, getSelectedPermissions().size() - visibleLines);
            if (scrollY < 0) {
                permissionsScrollOffset = Math.min(maxOffset, permissionsScrollOffset + 1);
            } else if (scrollY > 0) {
                permissionsScrollOffset = Math.max(0, permissionsScrollOffset - 1);
            }
            return true;
        }
        if (selectedTab == FactionTab.FACTION_MAP) {
            FactionMapClientData.MapSnapshot mapSnapshot = FactionMapClientData.getSnapshot();
            FactionMapRenderer.MapRegion region = FactionMapRenderer.buildMapRegion(getContentStart(FactionClientData.getSnapshot()), mapSnapshot.radius(),
                this.width, this.height, PANEL_PADDING);
            int listStart = FactionMapRenderer.getMapClaimsListStart(region, MAP_CONTROL_OFFSET);
            if (mouseY >= listStart) {
                int lineHeight = 10;
                int availableHeight = Math.max(0, getMapClaimsBottomRow() - listStart - 6);
                int visibleLines = Math.max(1, availableHeight / lineHeight);
                int maxOffset = Math.max(0, FactionClientData.getSnapshot().claims().size() - visibleLines);
                if (scrollY < 0) {
                    mapClaimsScrollOffset = Math.min(maxOffset, mapClaimsScrollOffset + 1);
                } else if (scrollY > 0) {
                    mapClaimsScrollOffset = Math.max(0, mapClaimsScrollOffset - 1);
                }
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (selectedTab == FactionTab.FACTION_MAP && button == 0) {
            FactionMapClientData.MapSnapshot mapSnapshot = FactionMapClientData.getSnapshot();
            FactionMapRenderer.MapRegion region = FactionMapRenderer.buildMapRegion(getContentStart(FactionClientData.getSnapshot()), mapSnapshot.radius(),
                this.width, this.height, PANEL_PADDING);
            ChunkPos clicked = FactionMapRenderer.getChunkFromMouse(region, mouseX, mouseY, mapSnapshot);
            if (clicked != null) {
                startSelection(clicked);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (selectedTab == FactionTab.FACTION_MAP && button == 0 && selectionActive) {
            FactionMapClientData.MapSnapshot mapSnapshot = FactionMapClientData.getSnapshot();
            FactionMapRenderer.MapRegion region = FactionMapRenderer.buildMapRegion(getContentStart(FactionClientData.getSnapshot()), mapSnapshot.radius(),
                this.width, this.height, PANEL_PADDING);
            ChunkPos hovered = FactionMapRenderer.getChunkFromMouse(region, mouseX, mouseY, mapSnapshot);
            if (hovered != null) {
                updateSelectionRectangle(hovered);
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (selectedTab == FactionTab.FACTION_MAP && button == 0 && selectionActive) {
            selectionActive = false;
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
        claimTypeButton.visible = mapTab;
        submitClaimsButton.visible = mapTab;
        safeZoneFactionField.setVisible(mapTab && selectedClaimType == ClaimType.SAFEZONE && isOperator());
        if (!mapTab) {
            selectionActive = false;
            selectionAnchor = null;
            selectedChunks.clear();
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
            ClientNetworkSender.sendToServer(FactionActionPacket.invite(name));
            inviteNameField.setValue("");
        }
    }

    private void acceptInvite() {
        String factionName = FactionClientData.getSnapshot().pendingInviteFaction();
        if (!factionName.isEmpty()) {
            ClientNetworkSender.sendToServer(FactionActionPacket.joinFaction(factionName));
        }
    }

    private void declineInvite() {
        ClientNetworkSender.sendToServer(FactionActionPacket.declineInvite());
    }

    private void sendPermission(boolean grant) {
        ClientNetworkSender.sendToServer(
            FactionActionPacket.setPermission(currentRole().name(), currentPermission().name(), grant)
        );
    }

    private void sendDynmapSync() {
        ClientNetworkSender.sendToServer(FactionActionPacket.syncDynmap());
    }

    private void sendMemberAction(MemberAction action) {
        String name = memberNameField.getValue().trim();
        if (name.isEmpty()) {
            return;
        }
        switch (action) {
            case KICK -> ClientNetworkSender.sendToServer(FactionActionPacket.kickMember(name));
            case PROMOTE -> ClientNetworkSender.sendToServer(FactionActionPacket.promoteMember(name));
            case DEMOTE -> ClientNetworkSender.sendToServer(FactionActionPacket.demoteMember(name));
        }
        memberNameField.setValue("");
    }

    private void leaveFaction() {
        ClientNetworkSender.sendToServer(FactionActionPacket.leaveFaction());
    }

    private void cycleClaimType() {
        List<ClaimType> options = getClaimTypeOptions();
        int index = options.indexOf(selectedClaimType);
        if (index < 0) {
            selectedClaimType = options.get(0);
        } else {
            selectedClaimType = options.get((index + 1) % options.size());
        }
        selectedChunks.clear();
        updateClaimTypeButtonLabel();
        updateVisibility();
    }

    private void startSelection(ChunkPos chunk) {
        selectionAnchor = chunk;
        selectionActive = true;
        updateSelectionRectangle(chunk);
    }

    private void updateSelectionRectangle(ChunkPos current) {
        if (selectionAnchor == null) {
            return;
        }
        selectedChunks.clear();
        int minX = Math.min(selectionAnchor.x, current.x);
        int maxX = Math.max(selectionAnchor.x, current.x);
        int minZ = Math.min(selectionAnchor.z, current.z);
        int maxZ = Math.max(selectionAnchor.z, current.z);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                selectedChunks.add(new ChunkPos(x, z));
                if (selectedChunks.size() >= 9) {
                    return;
                }
            }
        }
    }

    private void promptClaimConfirm() {
        if (selectedChunks.isEmpty()) {
            return;
        }
        int count = selectedChunks.size();
        String targetFaction = safeZoneFactionField.getValue().trim();
        Component title = Component.literal("Confirm Claims");
        Component message = switch (selectedClaimType) {
            case SAFEZONE -> Component.literal("Toggle " + count + " safe zone chunk(s) for " + targetFaction + "?");
            case PERSONAL -> Component.literal("Toggle " + count + " personal chunk(s)?");
            case FACTION -> Component.literal("Toggle " + count + " chunk(s) for " + getFactionLabel(FactionClientData.getSnapshot()) + "?");
        };
        Minecraft.getInstance().setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(result -> {
            Minecraft.getInstance().setScreen(this);
            if (result) {
                sendClaimSelection(targetFaction);
            }
        }, title, message));
    }

    private void sendClaimSelection(String factionName) {
        List<ChunkPos> chunks = new ArrayList<>(selectedChunks);
        if (chunks.isEmpty()) {
            return;
        }
        ClientNetworkSender.sendToServer(new FactionClaimSelectionPacket(chunks, toPacketClaimType(), factionName));
        selectedChunks.clear();
        FactionMapClientData.requestUpdate();
    }

    private void updateClaimTypeOptions(FactionClientData.FactionSnapshot snapshot) {
        List<ClaimType> options = getClaimTypeOptions();
        if (!options.contains(selectedClaimType)) {
            selectedClaimType = options.get(0);
        }
        updateClaimTypeButtonLabel(snapshot);
    }

    private void updateClaimTypeButtonLabel() {
        updateClaimTypeButtonLabel(FactionClientData.getSnapshot());
    }

    private void updateClaimTypeButtonLabel(FactionClientData.FactionSnapshot snapshot) {
        if (claimTypeButton != null) {
            claimTypeButton.setMessage(Component.literal("Claim: " + selectedClaimType.getLabel(getFactionLabel(snapshot))));
        }
    }

    private void renderPanels(GuiGraphics guiGraphics) {
        int panelLeft = Math.max(6, PANEL_PADDING - 8);
        int panelRight = Math.min(this.width - 6, this.width - PANEL_PADDING + 8);
        int headerTop = 4;
        int headerBottom = Math.max(headerTop + 18, panelTop - 6);
        int contentTop = Math.max(headerBottom + 4, panelTop - 2);
        int contentBottom = this.height - 6;
        drawPanel(guiGraphics, panelLeft, headerTop, panelRight, headerBottom);
        drawPanel(guiGraphics, panelLeft, contentTop, panelRight, contentBottom);
    }

    private void renderBackdrop(GuiGraphics guiGraphics) {
        guiGraphics.fill(0, 0, this.width, this.height, BACKDROP_COLOR);
    }

    private void drawPanel(GuiGraphics guiGraphics, int left, int top, int right, int bottom) {
        if (right <= left || bottom <= top) {
            return;
        }
        guiGraphics.fill(left, top, right, bottom, PANEL_BG);
        guiGraphics.fill(left, top, right, top + 1, PANEL_HIGHLIGHT);
        guiGraphics.fill(left, bottom - 1, right, bottom, PANEL_BORDER);
        guiGraphics.fill(left, top, left + 1, bottom, PANEL_BORDER);
        guiGraphics.fill(right - 1, top, right, bottom, PANEL_BORDER);
    }

    private List<ClaimType> getClaimTypeOptions() {
        if (isOperator()) {
            return List.of(ClaimType.FACTION, ClaimType.PERSONAL, ClaimType.SAFEZONE);
        }
        return List.of(ClaimType.FACTION, ClaimType.PERSONAL);
    }

    private String getFactionLabel(FactionClientData.FactionSnapshot snapshot) {
        if (snapshot.inFaction()) {
            return snapshot.factionName();
        }
        return "Faction";
    }

    private FactionClaimSelectionPacket.ClaimType toPacketClaimType() {
        return switch (selectedClaimType) {
            case FACTION -> FactionClaimSelectionPacket.ClaimType.FACTION;
            case PERSONAL -> FactionClaimSelectionPacket.ClaimType.PERSONAL;
            case SAFEZONE -> FactionClaimSelectionPacket.ClaimType.SAFEZONE;
        };
    }

    private boolean isOperator() {
        if (Minecraft.getInstance().player == null) {
            return false;
        }
        return Minecraft.getInstance().player.hasPermissions(FactionConfig.SERVER.adminBypassPermissionLevel.get());
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
        updateMapControlLayout();
        FactionMapRenderer.renderMapGrid(guiGraphics, mapSnapshot, region);
        if (!selectedChunks.isEmpty()) {
            FactionMapRenderer.renderSelectionOverlay(guiGraphics, mapSnapshot, region, selectedChunks);
        } else {
            guiGraphics.drawString(this.font, "Drag to select up to 9 chunks", PANEL_PADDING, startY + 14, 0x777777);
        }
        ChunkPos hovered = FactionMapRenderer.getChunkFromMouse(region, mouseX, mouseY, mapSnapshot);
        if (hovered != null) {
            FactionMapRenderer.renderMapTooltip(guiGraphics, mapSnapshot, hovered, mouseX, mouseY, this.font);
        }
        mapClaimsScrollOffset = renderMapClaimsList(guiGraphics, snapshot.claims(), region, mapClaimsScrollOffset);
        if (!selectedChunks.isEmpty()) {
            guiGraphics.drawString(this.font, "Selected " + selectedChunks.size() + " chunk(s)",
                PANEL_PADDING, startY + 14, 0xF9A825);
        }
    }

    private int renderMapClaimsList(GuiGraphics guiGraphics,
                                    List<com.mcprotector.network.FactionStatePacket.ClaimEntry> claims,
                                    FactionMapRenderer.MapRegion region,
                                    int scrollOffset) {
        int startY = FactionMapRenderer.getMapClaimsListStart(region);
        guiGraphics.drawString(this.font, "Claims:", PANEL_PADDING, startY, 0xFFFFFF);
        int y = startY + 12;
        if (claims.isEmpty()) {
            guiGraphics.drawString(this.font, "No claims.", PANEL_PADDING, y, 0x777777);
            return 0;
        }
        int lineHeight = 10;
        int availableHeight = Math.max(0, getMapClaimsBottomRow() - y - 6);
        int visibleLines = Math.max(1, availableHeight / lineHeight);
        int maxOffset = Math.max(0, claims.size() - visibleLines);
        int clampedOffset = Math.min(scrollOffset, maxOffset);
        List<com.mcprotector.network.FactionStatePacket.ClaimEntry> visibleClaims = claims
            .subList(clampedOffset, Math.min(claims.size(), clampedOffset + visibleLines));
        for (var claim : visibleClaims) {
            guiGraphics.drawString(this.font, "Chunk " + claim.chunkX() + ", " + claim.chunkZ(), PANEL_PADDING, y, 0xCCCCCC);
            y += lineHeight;
        }
        return clampedOffset;
    }

    private int getContentStart(FactionClientData.FactionSnapshot snapshot) {
        boolean hasControls = selectedTab == FactionTab.INVITES
            || selectedTab == FactionTab.PERMISSIONS
            || (selectedTab == FactionTab.MEMBERS && snapshot.inFaction());
        return panelTop + (hasControls ? CONTENT_START_OFFSET : CONTROL_TOP_OFFSET);
    }

    private int getMapClaimsBottomRow() {
        return this.height - PANEL_PADDING - 20;
    }

    private void updateMapControlLayout() {
        int controlsY = getMapClaimsBottomRow();
        int claimBlockWidth = 120 + 4 + 16;
        int leftLimit = PANEL_PADDING + 120;
        int rightLimit = this.width - PANEL_PADDING - 80 - 8;
        int claimStart = Math.max(leftLimit, (leftLimit + rightLimit - claimBlockWidth) / 2);
        claimTypeButton.setX(claimStart);
        claimTypeButton.setY(controlsY);
        submitClaimsButton.setX(claimStart + 124);
        submitClaimsButton.setY(controlsY);
        safeZoneFactionField.setX(claimStart);
        safeZoneFactionField.setY(controlsY - 22);
    }

    private enum MemberAction {
        KICK,
        PROMOTE,
        DEMOTE
    }

    private enum ClaimType {
        FACTION("Faction"),
        PERSONAL("Personal"),
        SAFEZONE("Safezone");

        private final String label;

        ClaimType(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        public String getLabel(String factionLabel) {
            if (this == FACTION) {
                return factionLabel;
            }
            return label;
        }
    }

}
