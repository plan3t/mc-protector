package com.mcprotector.client.gui;

import com.mcprotector.client.FactionClientData;
import com.mcprotector.client.FactionMapClientData;
import com.mcprotector.config.FactionConfig;
import com.mcprotector.data.FactionPermission;
import com.mcprotector.network.FactionActionPacket;
import com.mcprotector.network.FactionClaimSelectionPacket;
import com.mcprotector.network.FactionStatePacket;
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
    private static final int TAB_BUTTON_HEIGHT = 14;
    private static final int TAB_BUTTON_WIDTH = 58;
    private static final int PANEL_PADDING = 16;
    private static final int CONTROL_TOP_OFFSET = 6;
    private static final int CONTROL_ROW_SPACING = 24;
    private static final int CONTENT_START_OFFSET = 54;
    private static final int BACKDROP_COLOR = 0x80000000;
    private static final int PANEL_BG = 0xD01B1B1B;
    private static final int PANEL_BORDER = 0xFF3B3B3B;
    private static final int PANEL_HIGHLIGHT = 0xFF4A4A4A;
    private static final int MAP_COLOR_OWN = 0xFF4CAF50;
    private static final int MAP_COLOR_ALLY = 0xFF4FC3F7;
    private static final int MAP_COLOR_WAR = 0xFFEF5350;
    private static final int MAP_COLOR_NEUTRAL = 0xFF8D8D8D;
    private static final int MAP_COLOR_SAFE = 0xFFF9A825;
    private static final int MAP_COLOR_PERSONAL = 0xFF9C27B0;
    private static final int SAFEZONE_FIELD_WIDTH = 90;
    private static final int CLAIM_CONTROL_GAP = 6;
    private static final int MIN_TAB_BUTTON_WIDTH = 38;
    private static final int TAB_BUTTON_GAP = 2;
    private static final int PANEL_CONTENT_WIDTH = 360;
    private static final int TAB_COMPACT_LABEL_THRESHOLD = 54;
    private static final int MEMBER_SECTION_BUTTON_WIDTH = 72;
    private static final int MEMBER_SECTION_BUTTON_HEIGHT = 16;
    private static final int MEMBER_SECTION_BUTTON_GAP = 4;
    private static final int MIN_PANEL_CONTENT_WIDTH = 280;
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
    private EditBox roleNameField;
    private Button createRoleButton;
    private Button deleteRoleButton;
    private EditBox memberNameField;
    private Button kickMemberButton;
    private Button memberRoleButton;
    private Button setRoleButton;
    private EditBox ruleField;
    private Button addRuleButton;
    private Button removeRuleButton;
    private Button relationTypeButton;
    private Button relationPermissionButton;
    private Button relationGrantButton;
    private Button relationRevokeButton;
    private Button leaveFactionButton;
    private Button refreshButton;
    private Button dynmapSyncButton;
    private EditBox safeZoneFactionField;
    private Button claimTypeButton;
    private Button submitClaimsButton;
    private Button membersSectionButton;
    private Button invitesSectionButton;
    private int roleIndex;
    private int permissionIndex;
    private int memberRoleIndex;
    private int relationTypeIndex;
    private int relationPermissionIndex;
    private int panelTop;
    private int panelContentWidth = PANEL_CONTENT_WIDTH;
    private float horizontalScale = 1.0F;
    private int layoutPadding = PANEL_PADDING;
    private int tabButtonWidth = TAB_BUTTON_WIDTH;
    private int controlRowSpacing = CONTROL_ROW_SPACING;
    private int contentStartOffset = CONTENT_START_OFFSET;
    private int permissionsScrollOffset;
    private int mapClaimsScrollOffset;
    private int factionListScrollOffset;
    private int selectedRuleIndex = -1;
    private int rulesScrollOffset;
    private ClaimType selectedClaimType = ClaimType.FACTION;
    private boolean selectionActive;
    private ChunkPos selectionAnchor;
    private final Set<ChunkPos> selectedChunks = new LinkedHashSet<>();
    private MemberSection selectedMemberSection = MemberSection.MEMBERS;

    public FactionMainScreen() {
        super(Component.literal("Faction"));
    }

    @Override
    protected void init() {
        super.init();
        recalculateLayout();
        int panelLeft = getPanelLeft();
        int tabCount = FactionTab.values().length;
        int totalTabWidth = tabCount * tabButtonWidth + (tabCount - 1) * TAB_BUTTON_GAP;
        int startX = (this.width - totalTabWidth) / 2;
        int y = 42;
        for (FactionTab tab : FactionTab.values()) {
            int x = startX + tab.ordinal() * (tabButtonWidth + TAB_BUTTON_GAP);
            String tabLabel = tabButtonWidth < TAB_COMPACT_LABEL_THRESHOLD ? tab.getCompactLabel() : tab.getLabel();
            this.addRenderableWidget(Button.builder(Component.literal(tabLabel), button -> {
                selectedTab = tab;
                updateVisibility();
            }).bounds(x, y, tabButtonWidth, TAB_BUTTON_HEIGHT).build());
        }
        panelTop = y + TAB_BUTTON_HEIGHT + 10;
        int controlRowOne = panelTop + CONTROL_TOP_OFFSET;
        int controlRowTwo = controlRowOne + controlRowSpacing;
        int controlRowThree = controlRowTwo + controlRowSpacing;
        inviteNameField = new EditBox(this.font, panelLeft, controlRowOne, scaledWidth(140), 18, Component.literal("Player name"));
        inviteNameField.setMaxLength(32);
        this.addRenderableWidget(inviteNameField);
        inviteButton = this.addRenderableWidget(Button.builder(Component.literal("Send Invite"), button -> sendInvite())
            .bounds(panelX(150), controlRowOne, scaledWidth(100), 20)
            .build());
        joinInviteButton = this.addRenderableWidget(Button.builder(Component.literal("Join Faction"), button -> acceptInvite())
            .bounds(panelLeft, controlRowTwo, scaledWidth(110), 20)
            .build());
        declineInviteButton = this.addRenderableWidget(Button.builder(Component.literal("Decline"), button -> declineInvite())
            .bounds(panelX(120), controlRowTwo, scaledWidth(80), 20)
            .build());

        roleButton = this.addRenderableWidget(Button.builder(Component.literal("Role: " + currentRoleDisplay()), button -> {
            int roleCount = getRoleOptions().size();
            if (roleCount > 0) {
                roleIndex = (roleIndex + 1) % roleCount;
                updatePermissionLabels();
            }
        }).bounds(panelLeft, controlRowOne, scaledWidth(140), 20).build());
        permissionButton = this.addRenderableWidget(Button.builder(Component.literal("Perm: " + currentPermission().name()), button -> {
            permissionIndex = (permissionIndex + 1) % FactionPermission.values().length;
            updatePermissionLabels();
        }).bounds(panelX(150), controlRowOne, scaledWidth(140), 20).build());
        grantButton = this.addRenderableWidget(Button.builder(Component.literal("Grant"), button -> sendPermission(true))
            .bounds(panelLeft, controlRowTwo, scaledWidth(70), 20)
            .build());
        revokeButton = this.addRenderableWidget(Button.builder(Component.literal("Revoke"), button -> sendPermission(false))
            .bounds(panelX(80), controlRowTwo, scaledWidth(70), 20)
            .build());
        roleNameField = new EditBox(this.font, panelLeft, controlRowThree, scaledWidth(140), 18, Component.literal("Role name"));
        roleNameField.setMaxLength(32);
        this.addRenderableWidget(roleNameField);
        createRoleButton = this.addRenderableWidget(Button.builder(Component.literal("Create Role"), button -> sendCreateRole())
            .bounds(panelX(150), controlRowThree, scaledWidth(100), 20)
            .build());
        deleteRoleButton = this.addRenderableWidget(Button.builder(Component.literal("Delete Role"), button -> sendDeleteRole())
            .bounds(panelX(260), controlRowThree, scaledWidth(100), 20)
            .build());

        memberNameField = new EditBox(this.font, panelLeft, controlRowOne, scaledWidth(140), 18, Component.literal("Member name"));
        memberNameField.setMaxLength(32);
        this.addRenderableWidget(memberNameField);
        kickMemberButton = this.addRenderableWidget(Button.builder(Component.literal("Kick"), button -> sendMemberAction())
            .bounds(panelX(150), controlRowOne, scaledWidth(60), 20)
            .build());
        memberRoleButton = this.addRenderableWidget(Button.builder(Component.literal("Role: " + currentMemberRoleDisplay()), button -> {
            int roleCount = getRoleOptions().size();
            if (roleCount > 0) {
                memberRoleIndex = (memberRoleIndex + 1) % roleCount;
                updateMemberRoleLabel();
            }
        }).bounds(panelX(215), controlRowOne, scaledWidth(90), 20).build());
        setRoleButton = this.addRenderableWidget(Button.builder(Component.literal("Set Role"), button -> sendMemberRole())
            .bounds(panelX(310), controlRowOne, scaledWidth(70), 20)
            .build());

        ruleField = new EditBox(this.font, panelLeft, controlRowOne, scaledWidth(200), 18, Component.literal("New rule"));
        ruleField.setMaxLength(120);
        this.addRenderableWidget(ruleField);
        addRuleButton = this.addRenderableWidget(Button.builder(Component.literal("Add"), button -> sendAddRule())
            .bounds(panelX(210), controlRowOne, scaledWidth(60), 20)
            .build());
        removeRuleButton = this.addRenderableWidget(Button.builder(Component.literal("Remove selected"), button -> sendRemoveSelectedRule())
            .bounds(panelLeft, controlRowOne, scaledWidth(130), 20)
            .build());

        relationTypeButton = this.addRenderableWidget(Button.builder(Component.literal("Relation: " + currentRelation().name()), button -> {
            relationTypeIndex = (relationTypeIndex + 1) % relationOptions().size();
            updateRelationLabels();
        }).bounds(panelLeft, controlRowOne, scaledWidth(140), 20).build());
        relationPermissionButton = this.addRenderableWidget(Button.builder(Component.literal("Perm: " + currentRelationPermission().name()), button -> {
            relationPermissionIndex = (relationPermissionIndex + 1) % FactionPermission.values().length;
            updateRelationLabels();
        }).bounds(panelX(150), controlRowOne, scaledWidth(140), 20).build());
        relationGrantButton = this.addRenderableWidget(Button.builder(Component.literal("Grant"), button -> sendRelationPermission(true))
            .bounds(panelLeft, controlRowTwo, scaledWidth(70), 20)
            .build());
        relationRevokeButton = this.addRenderableWidget(Button.builder(Component.literal("Revoke"), button -> sendRelationPermission(false))
            .bounds(panelX(80), controlRowTwo, scaledWidth(70), 20)
            .build());

        int bottomRowY = this.height - layoutPadding - 20;
        refreshButton = this.addRenderableWidget(Button.builder(Component.literal("Refresh"), button -> {
            FactionClientData.requestUpdate();
            FactionMapClientData.requestUpdate();
        })
            .bounds(this.width - layoutPadding - 80, bottomRowY, 80, 20)
            .build());
        dynmapSyncButton = this.addRenderableWidget(Button.builder(Component.literal("Sync Dynmap"), button -> sendDynmapSync())
            .bounds(panelLeft, bottomRowY, scaledWidth(110), 20)
            .build());
        leaveFactionButton = this.addRenderableWidget(Button.builder(Component.literal("Leave Faction"), button -> leaveFaction())
            .bounds(panelLeft, bottomRowY, scaledWidth(110), 20)
            .build());
        safeZoneFactionField = new EditBox(this.font, panelLeft, controlRowOne, scaledWidth(SAFEZONE_FIELD_WIDTH), 16,
            Component.literal("Safe zone faction"));
        safeZoneFactionField.setMaxLength(32);
        this.addRenderableWidget(safeZoneFactionField);
        claimTypeButton = this.addRenderableWidget(Button.builder(Component.literal("Claim: " + selectedClaimType.getLabel()),
                button -> cycleClaimType())
            .bounds(panelX(SAFEZONE_FIELD_WIDTH + CLAIM_CONTROL_GAP), controlRowOne - 2, scaledWidth(120), 16)
            .build());
        submitClaimsButton = this.addRenderableWidget(Button.builder(Component.literal("✓"), button -> promptClaimConfirm())
            .bounds(panelX(SAFEZONE_FIELD_WIDTH + CLAIM_CONTROL_GAP + 124), controlRowOne - 2, scaledWidth(16), 16)
            .build());
        membersSectionButton = this.addRenderableWidget(Button.builder(Component.literal("Members"), button -> {
            selectedMemberSection = MemberSection.MEMBERS;
            updateVisibility();
        }).bounds(panelLeft, panelTop, MEMBER_SECTION_BUTTON_WIDTH, MEMBER_SECTION_BUTTON_HEIGHT).build());
        invitesSectionButton = this.addRenderableWidget(Button.builder(Component.literal("Invites"), button -> {
            selectedMemberSection = MemberSection.INVITES;
            updateVisibility();
        }).bounds(panelLeft, panelTop, MEMBER_SECTION_BUTTON_WIDTH, MEMBER_SECTION_BUTTON_HEIGHT).build());
        layoutMemberSectionButtons();

        updateVisibility();
        FactionClientData.requestUpdate();
        FactionMapClientData.requestUpdate();
    }

    @Override
    public void tick() {
        super.tick();
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        recalculateLayout();
        super.resize(minecraft, width, height);
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
        if (ruleField != null && ruleField.isFocused()) {
            return ruleField.keyPressed(keyCode, scanCode, modifiers);
        }
        if (roleNameField != null && roleNameField.isFocused()) {
            return roleNameField.keyPressed(keyCode, scanCode, modifiers);
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
        if (ruleField != null && ruleField.isFocused()) {
            return ruleField.charTyped(codePoint, modifiers);
        }
        if (roleNameField != null && roleNameField.isFocused()) {
            return roleNameField.charTyped(codePoint, modifiers);
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
            ? "Faction: " + snapshot.factionName() + " (" + getRoleDisplayName(snapshot, snapshot.roleName()) + ")"
            : "No faction";
        guiGraphics.drawString(this.font, headline, getPanelLeft(), 18, 0xE0E0E0);
        if (snapshot.inFaction()) {
            String stats = "Level " + snapshot.factionLevel()
                + " | Claims " + snapshot.claimCount() + "/" + snapshot.maxClaims()
                + " | Protection " + snapshot.protectionTier();
            guiGraphics.drawString(this.font, stats, getPanelLeft(), 28, 0xBDBDBD);
        }
        int contentStart = getContentStart(snapshot);
        switch (selectedTab) {
            case MEMBERS -> renderMembers(guiGraphics, snapshot, contentStart);
            case PERMISSIONS -> renderPermissions(guiGraphics, snapshot.permissions(), contentStart);
            case RULES -> renderRules(guiGraphics, snapshot.rules(), contentStart);
            case RELATIONS -> renderRelations(guiGraphics, snapshot.relations(), snapshot.relationPermissions(), contentStart);
            case FACTION_LIST -> renderFactionList(guiGraphics, snapshot.factionList(), contentStart);
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
            int listStart = panelTop + contentStartOffset + 22;
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
        if (selectedTab == FactionTab.RULES) {
            int lineHeight = 10;
            int listStart = getRulesListStart(getContentStart(FactionClientData.getSnapshot()));
            int listBottom = getRulesListBottom(selectedRuleIndex >= 0);
            if (mouseY >= listStart && mouseY <= listBottom) {
                int availableHeight = Math.max(0, listBottom - listStart);
                int visibleLines = Math.max(1, availableHeight / lineHeight);
                int maxOffset = Math.max(0, FactionClientData.getSnapshot().rules().size() - visibleLines);
                if (scrollY < 0) {
                    rulesScrollOffset = Math.min(maxOffset, rulesScrollOffset + 1);
                } else if (scrollY > 0) {
                    rulesScrollOffset = Math.max(0, rulesScrollOffset - 1);
                }
                return true;
            }
        }
        if (selectedTab == FactionTab.FACTION_LIST) {
            int lineHeight = 10;
            int listStart = getFactionListStart(getContentStart(FactionClientData.getSnapshot()));
            int listBottom = getFactionListBottom();
            if (mouseY >= listStart && mouseY <= listBottom) {
                int availableHeight = Math.max(0, listBottom - listStart);
                int visibleLines = Math.max(1, availableHeight / lineHeight);
                int maxOffset = Math.max(0, FactionClientData.getSnapshot().factionList().size() - visibleLines);
                if (scrollY < 0) {
                    factionListScrollOffset = Math.min(maxOffset, factionListScrollOffset + 1);
                } else if (scrollY > 0) {
                    factionListScrollOffset = Math.max(0, factionListScrollOffset - 1);
                }
                return true;
            }
        }
        if (selectedTab == FactionTab.FACTION_MAP) {
            FactionMapClientData.MapSnapshot mapSnapshot = FactionMapClientData.getSnapshot();
            FactionMapRenderer.MapRegion region = FactionMapRenderer.buildMapRegion(getContentStart(FactionClientData.getSnapshot()), mapSnapshot.radius(),
                this.width, this.height, layoutPadding);
            int listStart = FactionMapRenderer.getMapClaimsListStart(region);
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
        if (selectedTab == FactionTab.RULES && button == 0) {
            FactionClientData.FactionSnapshot snapshot = FactionClientData.getSnapshot();
            int listStart = getRulesListStart(getContentStart(snapshot));
            int listBottom = getRulesListBottom(selectedRuleIndex >= 0);
            if (mouseY >= listStart && mouseY <= listBottom && mouseX >= getPanelLeft() && mouseX <= this.width - getPanelLeft()) {
                int lineHeight = 10;
                int availableHeight = Math.max(0, listBottom - listStart);
                int visibleLines = Math.max(1, availableHeight / lineHeight);
                int row = (int) ((mouseY - listStart) / lineHeight);
                int index = rulesScrollOffset + row;
                if (row >= 0 && row < visibleLines && index < snapshot.rules().size()) {
                    selectedRuleIndex = index;
                    return true;
                }
            }
        }
        if (selectedTab == FactionTab.FACTION_MAP && button == 0) {
            FactionMapClientData.MapSnapshot mapSnapshot = FactionMapClientData.getSnapshot();
            FactionMapRenderer.MapRegion region = FactionMapRenderer.buildMapRegion(getContentStart(FactionClientData.getSnapshot()), mapSnapshot.radius(),
                this.width, this.height, layoutPadding);
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
                this.width, this.height, layoutPadding);
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

    public boolean isMapTabSelected() {
        return selectedTab == FactionTab.FACTION_MAP;
    }

    @Override
    public void onClose() {
        super.onClose();
        Minecraft.getInstance().setScreen(null);
    }

    private void updateVisibility() {
        boolean membersTab = selectedTab == FactionTab.MEMBERS;
        boolean invites = membersTab && selectedMemberSection == MemberSection.INVITES;
        boolean permissions = selectedTab == FactionTab.PERMISSIONS;
        boolean members = membersTab && selectedMemberSection == MemberSection.MEMBERS;
        boolean rules = selectedTab == FactionTab.RULES;
        boolean relations = selectedTab == FactionTab.RELATIONS;
        setEditBoxVisible(inviteNameField, invites);
        setButtonVisible(inviteButton, invites);
        setButtonVisible(joinInviteButton, invites);
        setButtonVisible(declineInviteButton, invites);
        setButtonVisible(dynmapSyncButton, selectedTab == FactionTab.FACTION_MAP);
        setButtonVisible(roleButton, permissions);
        setButtonVisible(permissionButton, permissions);
        setButtonVisible(grantButton, permissions);
        setButtonVisible(revokeButton, permissions);
        setEditBoxVisible(roleNameField, permissions);
        setButtonVisible(createRoleButton, permissions);
        setButtonVisible(deleteRoleButton, permissions);
        setEditBoxVisible(memberNameField, members);
        setButtonVisible(kickMemberButton, members);
        setButtonVisible(memberRoleButton, members);
        setButtonVisible(setRoleButton, members);
        setEditBoxVisible(ruleField, rules);
        setButtonVisible(addRuleButton, rules);
        setButtonVisible(removeRuleButton, rules);
        setButtonVisible(relationTypeButton, relations);
        setButtonVisible(relationPermissionButton, relations);
        setButtonVisible(relationGrantButton, relations);
        setButtonVisible(relationRevokeButton, relations);
        setButtonVisible(leaveFactionButton, membersTab);
        setButtonVisible(membersSectionButton, membersTab);
        setButtonVisible(invitesSectionButton, membersTab);
        updateMemberSectionButtonState();
        boolean mapTab = selectedTab == FactionTab.FACTION_MAP;
        setButtonVisible(claimTypeButton, mapTab);
        setButtonVisible(submitClaimsButton, mapTab);
        setEditBoxVisible(safeZoneFactionField, mapTab && selectedClaimType == ClaimType.SAFEZONE && isOperator());
        if (!mapTab) {
            selectionActive = false;
            selectionAnchor = null;
            selectedChunks.clear();
        }
        if (!rules) {
            selectedRuleIndex = -1;
            rulesScrollOffset = 0;
        }
        if (selectedTab == FactionTab.FACTION_LIST) {
            factionListScrollOffset = 0;
        }
        if (selectedTab == FactionTab.FACTION_MAP) {
            mapClaimsScrollOffset = 0;
            FactionMapClientData.requestUpdate();
        }
    }

    private void updateDynamicVisibility(FactionClientData.FactionSnapshot snapshot) {
        boolean invites = selectedTab == FactionTab.MEMBERS && selectedMemberSection == MemberSection.INVITES;
        boolean inFaction = snapshot.inFaction();
        updateRoleIndices(snapshot);
        setEditBoxVisible(inviteNameField, invites && inFaction);
        setButtonVisible(inviteButton, invites && inFaction);
        boolean hasInvite = invites && !snapshot.pendingInviteFaction().isEmpty() && !snapshot.inFaction();
        setButtonVisible(joinInviteButton, hasInvite);
        setButtonVisible(declineInviteButton, hasInvite);
        boolean permissions = selectedTab == FactionTab.PERMISSIONS;
        setButtonVisible(roleButton, permissions && inFaction);
        setButtonVisible(permissionButton, permissions && inFaction);
        setButtonVisible(grantButton, permissions && inFaction);
        setButtonVisible(revokeButton, permissions && inFaction);
        setEditBoxVisible(roleNameField, permissions && inFaction);
        setButtonVisible(createRoleButton, permissions && inFaction);
        setButtonVisible(deleteRoleButton, permissions && inFaction);
        if (permissions && inFaction) {
            roleButton.setMessage(Component.literal("Role: " + currentRoleDisplay()));
            permissionButton.setMessage(Component.literal("Perm: " + currentPermission().name()));
        }
        boolean members = selectedTab == FactionTab.MEMBERS && selectedMemberSection == MemberSection.MEMBERS;
        setEditBoxVisible(memberNameField, members && inFaction);
        setButtonVisible(kickMemberButton, members && inFaction);
        setButtonVisible(memberRoleButton, members && inFaction);
        setButtonVisible(setRoleButton, members && inFaction);
        if (members && inFaction) {
            memberRoleButton.setMessage(Component.literal("Role: " + currentMemberRoleDisplay()));
        }
        setButtonVisible(leaveFactionButton, members && inFaction);
        boolean rules = selectedTab == FactionTab.RULES;
        setEditBoxVisible(ruleField, rules && inFaction);
        setButtonVisible(addRuleButton, rules && inFaction);
        setButtonVisible(removeRuleButton, rules && inFaction && selectedRuleIndex >= 0);
        boolean relations = selectedTab == FactionTab.RELATIONS;
        setButtonVisible(relationTypeButton, relations && inFaction);
        setButtonVisible(relationPermissionButton, relations && inFaction);
        setButtonVisible(relationGrantButton, relations && inFaction);
        setButtonVisible(relationRevokeButton, relations && inFaction);
    }

    private void layoutMemberSectionButtons() {
        if (membersSectionButton == null || invitesSectionButton == null) {
            return;
        }
        int buttonWidth = scaledWidth(MEMBER_SECTION_BUTTON_WIDTH);
        int buttonHeight = MEMBER_SECTION_BUTTON_HEIGHT;
        int gap = scaledWidth(MEMBER_SECTION_BUTTON_GAP);
        int startX = getPanelLeft();
        int y = panelTop + CONTROL_TOP_OFFSET + controlRowSpacing + 2;

        membersSectionButton.setWidth(buttonWidth);
        membersSectionButton.setHeight(buttonHeight);
        membersSectionButton.setX(startX);
        membersSectionButton.setY(y);

        invitesSectionButton.setWidth(buttonWidth);
        invitesSectionButton.setHeight(buttonHeight);
        invitesSectionButton.setX(startX + buttonWidth + gap);
        invitesSectionButton.setY(y);
    }

    private void updateMemberSectionButtonState() {
        if (membersSectionButton != null) {
            membersSectionButton.active = selectedMemberSection != MemberSection.MEMBERS;
        }
        if (invitesSectionButton != null) {
            invitesSectionButton.active = selectedMemberSection != MemberSection.INVITES;
        }
    }

    private void setButtonVisible(Button button, boolean visible) {
        if (button != null) {
            button.visible = visible;
        }
    }

    private void setEditBoxVisible(EditBox field, boolean visible) {
        if (field != null) {
            field.setVisible(visible);
        }
    }

    private void updatePermissionLabels() {
        roleButton.setMessage(Component.literal("Role: " + currentRoleDisplay()));
        permissionButton.setMessage(Component.literal("Perm: " + currentPermission().name()));
        permissionsScrollOffset = 0;
    }

    private void updateMemberRoleLabel() {
        memberRoleButton.setMessage(Component.literal("Role: " + currentMemberRoleDisplay()));
    }

    private void updateRelationLabels() {
        relationTypeButton.setMessage(Component.literal("Relation: " + currentRelation().name()));
        relationPermissionButton.setMessage(Component.literal("Perm: " + currentRelationPermission().name()));
    }

    private FactionPermission currentPermission() {
        return FactionPermission.values()[permissionIndex % FactionPermission.values().length];
    }

    private List<FactionStatePacket.RoleEntry> getRoleOptions() {
        return getRoleOptions(FactionClientData.getSnapshot());
    }

    private List<FactionStatePacket.RoleEntry> getRoleOptions(FactionClientData.FactionSnapshot snapshot) {
        if (!snapshot.roles().isEmpty()) {
            return snapshot.roles();
        }
        List<FactionStatePacket.RoleEntry> fallback = new ArrayList<>();
        for (var entry : snapshot.permissions()) {
            fallback.add(new FactionStatePacket.RoleEntry(entry.role(), entry.role()));
        }
        return fallback;
    }

    private void updateRoleIndices(FactionClientData.FactionSnapshot snapshot) {
        int roleCount = getRoleOptions(snapshot).size();
        if (roleCount > 0) {
            roleIndex = Math.floorMod(roleIndex, roleCount);
            memberRoleIndex = Math.floorMod(memberRoleIndex, roleCount);
        } else {
            roleIndex = 0;
            memberRoleIndex = 0;
        }
    }

    private String currentRoleName() {
        List<FactionStatePacket.RoleEntry> roles = getRoleOptions();
        if (roles.isEmpty()) {
            return "";
        }
        return roles.get(Math.floorMod(roleIndex, roles.size())).name();
    }

    private String currentRoleDisplay() {
        List<FactionStatePacket.RoleEntry> roles = getRoleOptions();
        if (roles.isEmpty()) {
            return "-";
        }
        FactionStatePacket.RoleEntry role = roles.get(Math.floorMod(roleIndex, roles.size()));
        return role.displayName() == null || role.displayName().isBlank() ? role.name() : role.displayName();
    }

    private String currentMemberRoleName() {
        List<FactionStatePacket.RoleEntry> roles = getRoleOptions();
        if (roles.isEmpty()) {
            return "";
        }
        return roles.get(Math.floorMod(memberRoleIndex, roles.size())).name();
    }

    private String currentMemberRoleDisplay() {
        List<FactionStatePacket.RoleEntry> roles = getRoleOptions();
        if (roles.isEmpty()) {
            return "-";
        }
        FactionStatePacket.RoleEntry role = roles.get(Math.floorMod(memberRoleIndex, roles.size()));
        return role.displayName() == null || role.displayName().isBlank() ? role.name() : role.displayName();
    }

    private String getRoleDisplayName(FactionClientData.FactionSnapshot snapshot, String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return "-";
        }
        for (FactionStatePacket.RoleEntry entry : getRoleOptions(snapshot)) {
            if (entry.name().equalsIgnoreCase(roleName)) {
                return entry.displayName() == null || entry.displayName().isBlank() ? entry.name() : entry.displayName();
            }
        }
        return roleName;
    }

    private List<com.mcprotector.data.FactionRelation> relationOptions() {
        return List.of(com.mcprotector.data.FactionRelation.ALLY, com.mcprotector.data.FactionRelation.WAR);
    }

    private com.mcprotector.data.FactionRelation currentRelation() {
        List<com.mcprotector.data.FactionRelation> options = relationOptions();
        return options.get(relationTypeIndex % options.size());
    }

    private FactionPermission currentRelationPermission() {
        return FactionPermission.values()[relationPermissionIndex % FactionPermission.values().length];
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
        String roleName = currentRoleName();
        if (roleName.isEmpty()) {
            return;
        }
        ClientNetworkSender.sendToServer(
            FactionActionPacket.setPermission(roleName, currentPermission().name(), grant)
        );
    }

    private void sendCreateRole() {
        String roleName = roleNameField.getValue().trim();
        if (roleName.isEmpty()) {
            return;
        }
        ClientNetworkSender.sendToServer(FactionActionPacket.createRole(roleName, roleName));
        roleNameField.setValue("");
    }

    private void sendDeleteRole() {
        String roleName = roleNameField.getValue().trim();
        if (roleName.isEmpty()) {
            roleName = currentRoleName();
        }
        if (roleName.isEmpty()) {
            return;
        }
        ClientNetworkSender.sendToServer(FactionActionPacket.deleteRole(roleName));
        roleNameField.setValue("");
    }

    private void sendRelationPermission(boolean grant) {
        ClientNetworkSender.sendToServer(
            FactionActionPacket.setRelationPermission(currentRelation().name(), currentRelationPermission().name(), grant)
        );
    }

    private void sendDynmapSync() {
        ClientNetworkSender.sendToServer(FactionActionPacket.syncDynmap());
    }

    private void sendMemberAction() {
        String name = memberNameField.getValue().trim();
        if (name.isEmpty()) {
            return;
        }
        ClientNetworkSender.sendToServer(FactionActionPacket.kickMember(name));
        memberNameField.setValue("");
    }

    private void sendMemberRole() {
        String name = memberNameField.getValue().trim();
        if (name.isEmpty()) {
            return;
        }
        String roleName = currentMemberRoleName();
        if (roleName.isEmpty()) {
            return;
        }
        ClientNetworkSender.sendToServer(FactionActionPacket.setRole(name, roleName));
        memberNameField.setValue("");
    }

    private void sendAddRule() {
        String rule = ruleField.getValue().trim();
        if (rule.isEmpty()) {
            return;
        }
        ClientNetworkSender.sendToServer(FactionActionPacket.addRule(rule));
        ruleField.setValue("");
    }

    private void sendRemoveSelectedRule() {
        FactionClientData.FactionSnapshot snapshot = FactionClientData.getSnapshot();
        if (selectedRuleIndex < 0 || selectedRuleIndex >= snapshot.rules().size()) {
            return;
        }
        String rule = snapshot.rules().get(selectedRuleIndex);
        ClientNetworkSender.sendToServer(FactionActionPacket.removeRule(rule));
        selectedRuleIndex = -1;
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
        int panelLeft = Math.max(6, getPanelLeft() - 8);
        int panelRight = Math.min(this.width - 6, this.width - getPanelLeft() + 8);
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

    private void recalculateLayout() {
        int minDimension = Math.min(this.width, this.height);
        layoutPadding = Math.max(8, Math.min(PANEL_PADDING, minDimension / 24));
        controlRowSpacing = this.height < 340 ? 20 : CONTROL_ROW_SPACING;
        contentStartOffset = this.height < 340 ? 46 : CONTENT_START_OFFSET;
        int tabCount = FactionTab.values().length;
        int availableWidth = this.width - layoutPadding * 2 - (tabCount - 1) * TAB_BUTTON_GAP;
        tabButtonWidth = Math.max(MIN_TAB_BUTTON_WIDTH, Math.min(TAB_BUTTON_WIDTH, availableWidth / tabCount));
        panelContentWidth = Math.max(MIN_PANEL_CONTENT_WIDTH,
            Math.min(PANEL_CONTENT_WIDTH, this.width - (layoutPadding * 2) - 16));
        horizontalScale = panelContentWidth / (float) PANEL_CONTENT_WIDTH;
    }

    private int getPanelLeft() {
        int maxLeft = Math.max(layoutPadding, (this.width - panelContentWidth) / 2);
        return Math.max(8, maxLeft);
    }

    private int panelX(int baseOffset) {
        return getPanelLeft() + Math.round(baseOffset * horizontalScale);
    }

    private int scaledWidth(int baseWidth) {
        return Math.max(14, Math.round(baseWidth * horizontalScale));
    }

    private int getPanelRight() {
        return this.width - getPanelLeft();
    }

    private void renderScrollIndicator(GuiGraphics guiGraphics, int totalEntries, int visibleEntries, int offset,
                                       int topY, int bottomY) {
        if (totalEntries <= visibleEntries || bottomY <= topY) {
            return;
        }
        int trackX = getPanelRight() - 6;
        guiGraphics.fill(trackX, topY, trackX + 2, bottomY, 0x66424242);
        int trackHeight = bottomY - topY;
        int thumbHeight = Math.max(8, (int) ((visibleEntries / (float) totalEntries) * trackHeight));
        int maxOffset = Math.max(1, totalEntries - visibleEntries);
        int thumbRange = Math.max(0, trackHeight - thumbHeight);
        int thumbY = topY + Math.round((offset / (float) maxOffset) * thumbRange);
        guiGraphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, 0xCCBDBDBD);
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

    private void renderMembers(GuiGraphics guiGraphics, FactionClientData.FactionSnapshot snapshot, int startY) {
        layoutMemberSectionButtons();
        int contentY = startY + MEMBER_SECTION_BUTTON_HEIGHT + 8;
        if (selectedMemberSection == MemberSection.INVITES) {
            renderInvites(guiGraphics, snapshot, contentY);
            return;
        }
        guiGraphics.drawString(this.font, "Members:", getPanelLeft(), contentY, 0xFFFFFF);
        int y = contentY + 12;
        for (var member : snapshot.members()) {
            String roleLabel = getRoleDisplayName(snapshot, member.role());
            guiGraphics.drawString(this.font, member.name() + " - " + roleLabel, getPanelLeft(), y, 0xCCCCCC);
            y += 10;
        }
    }

    private void renderInvites(GuiGraphics guiGraphics, FactionClientData.FactionSnapshot snapshot, int startY) {
        guiGraphics.drawString(this.font, "Invites:", getPanelLeft(), startY, 0xFFFFFF);
        int y = startY + 12;
        if (!snapshot.inFaction()) {
            if (!snapshot.pendingInviteFaction().isEmpty()) {
                guiGraphics.drawString(this.font, "Pending invite to: " + snapshot.pendingInviteFaction(), getPanelLeft(), y, 0xCCCCCC);
                guiGraphics.drawString(this.font, "Use the buttons above to respond.", getPanelLeft(), y + 12, 0x777777);
            } else {
                guiGraphics.drawString(this.font, "No pending invites.", getPanelLeft(), y, 0x777777);
            }
            return;
        }
        if (snapshot.invites().isEmpty()) {
            guiGraphics.drawString(this.font, "No outgoing invites.", getPanelLeft(), y, 0x777777);
            return;
        }
        for (var invite : snapshot.invites()) {
            String expiry = INVITE_TIME_FORMAT.format(Instant.ofEpochMilli(invite.expiresAt()));
            guiGraphics.drawString(this.font, invite.name() + " (expires " + expiry + ")", getPanelLeft(), y, 0xCCCCCC);
            y += 10;
        }
    }

    private void renderPermissions(GuiGraphics guiGraphics, List<com.mcprotector.network.FactionStatePacket.PermissionEntry> permissions, int startY) {
        guiGraphics.drawString(this.font, "Permissions:", getPanelLeft(), startY, 0xFFFFFF);
        String selectedRole = currentRoleName();
        com.mcprotector.network.FactionStatePacket.PermissionEntry selected = permissions.stream()
            .filter(entry -> entry.role().equalsIgnoreCase(selectedRole))
            .findFirst()
            .orElse(null);
        int y = startY + 12;
        if (selected == null) {
            guiGraphics.drawString(this.font, "No permissions found for " + currentRoleDisplay() + ".", getPanelLeft(), y, 0x777777);
            return;
        }
        guiGraphics.drawString(this.font, currentRoleDisplay() + ":", getPanelLeft(), y, 0xCCCCCC);
        y += 10;
        int lineHeight = 10;
        int availableHeight = Math.max(0, this.height - y - 30);
        int visibleLines = Math.max(1, availableHeight / lineHeight);
        int maxOffset = Math.max(0, selected.permissions().size() - visibleLines);
        permissionsScrollOffset = Math.min(permissionsScrollOffset, maxOffset);
        List<String> visiblePerms = selected.permissions()
            .subList(permissionsScrollOffset, Math.min(selected.permissions().size(), permissionsScrollOffset + visibleLines));
        for (String perm : visiblePerms) {
            guiGraphics.drawString(this.font, "- " + perm, getPanelLeft(), y, 0xAAAAAA);
            y += lineHeight;
        }
        renderScrollIndicator(guiGraphics, selected.permissions().size(), visibleLines, permissionsScrollOffset,
            startY + 22, getPermissionsBottom());
    }

    private List<String> getSelectedPermissions() {
        return FactionClientData.getSnapshot().permissions().stream()
            .filter(entry -> entry.role().equalsIgnoreCase(currentRoleName()))
            .findFirst()
            .map(com.mcprotector.network.FactionStatePacket.PermissionEntry::permissions)
            .orElse(List.of());
    }

    private void renderRelations(GuiGraphics guiGraphics,
                                 List<com.mcprotector.network.FactionStatePacket.RelationEntry> relations,
                                 List<com.mcprotector.network.FactionStatePacket.RelationPermissionEntry> relationPermissions,
                                 int startY) {
        guiGraphics.drawString(this.font, "Relations:", getPanelLeft(), startY, 0xFFFFFF);
        int y = startY + 12;
        if (relations.isEmpty()) {
            guiGraphics.drawString(this.font, "No active relations.", getPanelLeft(), y, 0x777777);
            y += 12;
        } else {
            for (var relation : relations) {
                guiGraphics.drawString(this.font, relation.factionName() + " - " + relation.relation(), getPanelLeft(), y, 0xCCCCCC);
                y += 10;
            }
        }
        y += 6;
        String relationLabel = currentRelation().name();
        guiGraphics.drawString(this.font, "Relation Permissions (" + relationLabel + "):", getPanelLeft(), y, 0xFFFFFF);
        y += 12;
        com.mcprotector.network.FactionStatePacket.RelationPermissionEntry selected = relationPermissions.stream()
            .filter(entry -> entry.relation().equalsIgnoreCase(relationLabel))
            .findFirst()
            .orElse(null);
        if (selected == null || selected.permissions().isEmpty()) {
            guiGraphics.drawString(this.font, "No permissions configured.", getPanelLeft(), y, 0x777777);
            return;
        }
        for (String perm : selected.permissions()) {
            guiGraphics.drawString(this.font, "- " + perm, getPanelLeft(), y, 0xAAAAAA);
            y += 10;
        }
    }

    private void renderFactionList(GuiGraphics guiGraphics,
                                   List<com.mcprotector.network.FactionStatePacket.FactionListEntry> factions,
                                   int startY) {
        guiGraphics.drawString(this.font, "Factions:", getPanelLeft(), startY, 0xFFFFFF);
        int listStart = getFactionListStart(startY);
        if (factions.isEmpty()) {
            guiGraphics.drawString(this.font, "No factions found.", getPanelLeft(), listStart, 0x777777);
            return;
        }
        int listBottom = getFactionListBottom();
        int lineHeight = 10;
        int availableHeight = Math.max(0, listBottom - listStart);
        int visibleLines = Math.max(1, availableHeight / lineHeight);
        int maxOffset = Math.max(0, factions.size() - visibleLines);
        factionListScrollOffset = Math.min(factionListScrollOffset, maxOffset);
        List<com.mcprotector.network.FactionStatePacket.FactionListEntry> visible = factions.subList(
            factionListScrollOffset, Math.min(factions.size(), factionListScrollOffset + visibleLines));
        int y = listStart;
        for (var faction : visible) {
            String label = faction.factionName() + " (" + faction.memberCount() + " members)";
            if ("OWN".equalsIgnoreCase(faction.relation())) {
                label += " - Your faction";
            } else if (!"NEUTRAL".equalsIgnoreCase(faction.relation())) {
                label += " - " + faction.relation();
            }
            int color = 0xFF000000 | faction.color();
            guiGraphics.drawString(this.font, label, getPanelLeft(), y, color);
            y += lineHeight;
        }
        renderScrollIndicator(guiGraphics, factions.size(), visibleLines, factionListScrollOffset, listStart, listBottom);
    }

    private void renderRules(GuiGraphics guiGraphics, List<String> rules, int startY) {
        guiGraphics.drawString(this.font, "Rules:", getPanelLeft(), startY, 0xFFFFFF);
        int listStart = getRulesListStart(startY);
        if (rules.isEmpty()) {
            guiGraphics.drawString(this.font, "No rules set yet.", getPanelLeft(), listStart, 0x777777);
            removeRuleButton.visible = false;
            return;
        }
        if (selectedRuleIndex >= rules.size()) {
            selectedRuleIndex = -1;
        }
        boolean showRemove = selectedRuleIndex >= 0;
        int listBottom = getRulesListBottom(showRemove);
        int lineHeight = 10;
        int availableHeight = Math.max(0, listBottom - listStart);
        int visibleLines = Math.max(1, availableHeight / lineHeight);
        int maxOffset = Math.max(0, rules.size() - visibleLines);
        rulesScrollOffset = Math.min(rulesScrollOffset, maxOffset);
        List<String> visibleRules = rules.subList(rulesScrollOffset, Math.min(rules.size(), rulesScrollOffset + visibleLines));
        int y = listStart;
        for (int i = 0; i < visibleRules.size(); i++) {
            int index = rulesScrollOffset + i;
            String rule = visibleRules.get(i);
            if (index == selectedRuleIndex) {
                guiGraphics.fill(getPanelLeft() - 2, y - 1, getPanelRight() + 2, y + lineHeight - 1, PANEL_HIGHLIGHT);
            }
            guiGraphics.drawString(this.font, (index + 1) + ". " + rule, getPanelLeft(), y, 0xCCCCCC);
            y += lineHeight;
        }
        renderScrollIndicator(guiGraphics, rules.size(), visibleLines, rulesScrollOffset, listStart, listBottom);
        if (showRemove) {
            removeRuleButton.setX(getPanelLeft());
            removeRuleButton.setY(listBottom + 6);
            removeRuleButton.visible = true;
        } else {
            removeRuleButton.visible = false;
            return;
        }
    }

    private void renderFactionMap(GuiGraphics guiGraphics, FactionClientData.FactionSnapshot snapshot, int startY, int mouseX, int mouseY) {
        FactionMapClientData.MapSnapshot mapSnapshot = FactionMapClientData.getSnapshot();
        int radius = mapSnapshot.radius();
        guiGraphics.drawString(this.font, "Faction Map:", getPanelLeft(), startY, 0xFFFFFF);
        if (radius <= 0) {
            guiGraphics.drawString(this.font, "Map data unavailable. Click Refresh.", getPanelLeft(), startY + 14, 0x777777);
            return;
        }
        FactionMapRenderer.MapRegion region = FactionMapRenderer.buildMapRegion(startY, radius, this.width, this.height, layoutPadding);
        updateMapControlLayout();
        FactionMapRenderer.renderMapGrid(guiGraphics, mapSnapshot, region);
        renderMapCardinalMarkers(guiGraphics, region);
        if (!selectedChunks.isEmpty()) {
            FactionMapRenderer.renderSelectionOverlay(guiGraphics, mapSnapshot, region, selectedChunks);
        } else {
            guiGraphics.drawString(this.font, "Drag to select up to 9 chunks", getPanelLeft(), startY + 14, 0x777777);
        }
        ChunkPos hovered = FactionMapRenderer.getChunkFromMouse(region, mouseX, mouseY, mapSnapshot);
        if (hovered != null) {
            FactionMapRenderer.renderMapTooltip(guiGraphics, mapSnapshot, hovered, mouseX, mouseY, this.font);
        }
        renderMapLegend(guiGraphics, region);
        mapClaimsScrollOffset = renderMapClaimsList(guiGraphics, snapshot.claims(), region, mapClaimsScrollOffset);
        if (!selectedChunks.isEmpty()) {
            guiGraphics.drawString(this.font, "Selected " + selectedChunks.size() + " chunk(s)",
                getPanelLeft(), startY + 14, 0xF9A825);
        }
    }

    private int renderMapClaimsList(GuiGraphics guiGraphics,
                                    List<com.mcprotector.network.FactionStatePacket.ClaimEntry> claims,
                                    FactionMapRenderer.MapRegion region,
                                    int scrollOffset) {
        int startY = FactionMapRenderer.getMapClaimsListStart(region);
        guiGraphics.drawString(this.font, "Claims:", getPanelLeft(), startY, 0xFFFFFF);
        int y = startY + 12;
        if (claims.isEmpty()) {
            guiGraphics.drawString(this.font, "No claims.", getPanelLeft(), y, 0x777777);
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
            guiGraphics.drawString(this.font, "Chunk " + claim.chunkX() + ", " + claim.chunkZ(), getPanelLeft(), y, 0xCCCCCC);
            y += lineHeight;
        }
        renderScrollIndicator(guiGraphics, claims.size(), visibleLines, clampedOffset, y - (visibleClaims.size() * lineHeight), getMapClaimsBottomRow() - 2);
        return clampedOffset;
    }

    private void renderMapLegend(GuiGraphics guiGraphics, FactionMapRenderer.MapRegion region) {
        int gridSize = region.cellSize() * (region.radius() * 2 + 1);
        int y = region.originY() + gridSize + 2;
        int x = getPanelLeft();
        String[] labels = {"Own", "Ally", "War", "Neutral", "Safe", "Personal"};
        int[] colors = {MAP_COLOR_OWN, MAP_COLOR_ALLY, MAP_COLOR_WAR, MAP_COLOR_NEUTRAL, MAP_COLOR_SAFE, MAP_COLOR_PERSONAL};
        for (int i = 0; i < labels.length; i++) {
            String label = labels[i];
            guiGraphics.drawString(this.font, label, x, y, colors[i]);
            x += this.font.width(label);
            if (i < labels.length - 1) {
                String separator = " / ";
                guiGraphics.drawString(this.font, separator, x, y, 0x777777);
                x += this.font.width(separator);
            }
        }
    }

    private void renderMapCardinalMarkers(GuiGraphics guiGraphics, FactionMapRenderer.MapRegion region) {
        int gridSize = region.cellSize() * (region.radius() * 2 + 1);
        int mapX = region.originX();
        int mapY = region.originY();
        int mapEndX = mapX + gridSize;
        int mapEndY = mapY + gridSize;
        int centerX = mapX + gridSize / 2;
        int centerY = mapY + gridSize / 2;
        int color = 0xFFE0E0E0;
        String north = "N";
        guiGraphics.drawString(this.font, north, centerX - this.font.width(north) / 2, mapY + 2, color);
        String south = "S";
        guiGraphics.drawString(this.font, south, centerX - this.font.width(south) / 2,
            mapEndY - this.font.lineHeight - 2, color);
        String west = "W";
        guiGraphics.drawString(this.font, west, mapX + 2, centerY - this.font.lineHeight / 2, color);
        String east = "E";
        guiGraphics.drawString(this.font, east, mapEndX - this.font.width(east) - 2,
            centerY - this.font.lineHeight / 2, color);
    }

    private int getContentStart(FactionClientData.FactionSnapshot snapshot) {
        boolean hasControls = selectedTab == FactionTab.PERMISSIONS
            || (selectedTab == FactionTab.RELATIONS && snapshot.inFaction())
            || (selectedTab == FactionTab.MEMBERS && snapshot.inFaction())
            || (selectedTab == FactionTab.RULES && snapshot.inFaction());
        int controlOffset = contentStartOffset;
        if (selectedTab == FactionTab.PERMISSIONS) {
            controlOffset += controlRowSpacing;
        }
        if (selectedTab == FactionTab.MEMBERS) {
            controlOffset += MEMBER_SECTION_BUTTON_HEIGHT + 8;
        }
        return panelTop + (hasControls ? controlOffset : CONTROL_TOP_OFFSET);
    }

    private int getMapClaimsBottomRow() {
        return this.height - layoutPadding - 20;
    }

    private int getPermissionsBottom() {
        return this.height - layoutPadding - 20;
    }

    private int getRulesListStart(int startY) {
        return startY + 12;
    }

    private int getFactionListStart(int startY) {
        return startY + 12;
    }

    private int getRulesListBottom(boolean showRemove) {
        int padding = showRemove ? 26 : 6;
        return this.height - layoutPadding - 20 - padding;
    }

    private int getFactionListBottom() {
        return this.height - layoutPadding - 20;
    }

    private void updateMapControlLayout() {
        int controlsY = getMapClaimsBottomRow();
        boolean showSafeZoneField = safeZoneFactionField.isVisible();
        int safeZoneWidth = scaledWidth(SAFEZONE_FIELD_WIDTH);
        int claimTypeWidth = scaledWidth(120);
        int submitWidth = scaledWidth(16);
        int claimGap = scaledWidth(CLAIM_CONTROL_GAP);
        int claimBlockWidth = (showSafeZoneField ? safeZoneWidth + claimGap : 0) + claimTypeWidth + claimGap + submitWidth;
        int leftLimit = getPanelLeft() + 120;
        int rightLimit = this.width - layoutPadding - 80 - 8;
        int claimStart = Math.max(leftLimit, (leftLimit + rightLimit - claimBlockWidth) / 2);
        if (showSafeZoneField) {
            safeZoneFactionField.setX(claimStart);
            claimTypeButton.setX(claimStart + safeZoneWidth + claimGap);
        } else {
            claimTypeButton.setX(claimStart);
            safeZoneFactionField.setX(claimStart - safeZoneWidth - claimGap);
        }
        safeZoneFactionField.setY(controlsY);
        claimTypeButton.setY(controlsY);
        submitClaimsButton.setX(claimTypeButton.getX() + claimTypeWidth + claimGap);
        submitClaimsButton.setY(controlsY);
    }

    private enum MemberSection {
        MEMBERS,
        INVITES
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
