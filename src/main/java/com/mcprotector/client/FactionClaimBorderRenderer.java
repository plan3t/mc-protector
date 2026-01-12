package com.mcprotector.client;

import com.mcprotector.network.FactionClaimMapPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.Camera;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.Map;

public final class FactionClaimBorderRenderer {
    private static final int SAFE_ZONE_COLOR = 0xFFF9A825;
    private static final int PERSONAL_CLAIM_COLOR = 0xFF9C27B0;
    private static final float BORDER_ALPHA = 0.35f;
    private static final double BORDER_HEIGHT = 4.0;

    private FactionClaimBorderRenderer() {
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            return;
        }
        FactionMapClientData.MapSnapshot snapshot = FactionMapClientData.getSnapshot();
        if (snapshot.claims().isEmpty()) {
            return;
        }
        int renderRadius = client.options.getEffectiveRenderDistance();
        ChunkPos playerChunk = new ChunkPos(client.player.blockPosition());
        PoseStack poseStack = event.getPoseStack();
        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        PoseStack.Pose pose = poseStack.last();
        MultiBufferSource.BufferSource bufferSource = client.renderBuffers().bufferSource();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
        TextureAtlasSprite sprite = client.getBlockRenderer().getBlockModelShaper()
            .getParticleIcon(Blocks.WHITE_STAINED_GLASS.defaultBlockState());
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();
        for (Map.Entry<Long, FactionClaimMapPacket.ClaimEntry> entry : snapshot.claims().entrySet()) {
            ChunkPos chunkPos = new ChunkPos(entry.getKey());
            int dx = chunkPos.x - playerChunk.x;
            int dz = chunkPos.z - playerChunk.z;
            if (Math.abs(dx) > renderRadius || Math.abs(dz) > renderRadius) {
                continue;
            }
            int color = getBorderColor(entry.getValue());
            float red = ((color >> 16) & 0xFF) / 255.0f;
            float green = ((color >> 8) & 0xFF) / 255.0f;
            float blue = (color & 0xFF) / 255.0f;
            double minX = chunkPos.getMinBlockX();
            double maxX = chunkPos.getMaxBlockX() + 1.0;
            double minZ = chunkPos.getMinBlockZ();
            double maxZ = chunkPos.getMaxBlockZ() + 1.0;
            double minY = client.player.getY() - 1.0;
            double maxY = minY + BORDER_HEIGHT;
            drawVerticalQuad(consumer, pose, minX, minZ, minX, maxZ, minY, maxY, red, green, blue, BORDER_ALPHA,
                u0, u1, v0, v1);
            drawVerticalQuad(consumer, pose, maxX, minZ, maxX, maxZ, minY, maxY, red, green, blue, BORDER_ALPHA,
                u0, u1, v0, v1);
            drawVerticalQuad(consumer, pose, minX, minZ, maxX, minZ, minY, maxY, red, green, blue, BORDER_ALPHA,
                u0, u1, v0, v1);
            drawVerticalQuad(consumer, pose, minX, maxZ, maxX, maxZ, minY, maxY, red, green, blue, BORDER_ALPHA,
                u0, u1, v0, v1);
        }
        bufferSource.endBatch();
        poseStack.popPose();
    }

    private static int getBorderColor(FactionClaimMapPacket.ClaimEntry entry) {
        if (entry.safeZone()) {
            return SAFE_ZONE_COLOR;
        }
        if (entry.personal()) {
            return PERSONAL_CLAIM_COLOR;
        }
        return switch (entry.relation()) {
            case "OWN" -> 0xFF4CAF50;
            case "ALLY" -> 0xFF4FC3F7;
            case "WAR" -> 0xFFEF5350;
            default -> 0xFF8D8D8D;
        };
    }

    private static void drawVerticalQuad(VertexConsumer consumer, PoseStack.Pose pose, double x1, double z1,
                                         double x2, double z2, double minY, double maxY, float red, float green,
                                         float blue, float alpha, float u0, float u1, float v0, float v1) {
        int light = LightTexture.FULL_BRIGHT;
        consumer.vertex(pose.pose(), (float) x1, (float) minY, (float) z1)
            .color(red, green, blue, alpha)
            .uv(u0, v1)
            .overlayCoords(OverlayTexture.NO_OVERLAY)
            .uv2(light)
            .normal(pose.normal(), 0.0f, 1.0f, 0.0f)
            .endVertex();
        consumer.vertex(pose.pose(), (float) x1, (float) maxY, (float) z1)
            .color(red, green, blue, alpha)
            .uv(u0, v0)
            .overlayCoords(OverlayTexture.NO_OVERLAY)
            .uv2(light)
            .normal(pose.normal(), 0.0f, 1.0f, 0.0f)
            .endVertex();
        consumer.vertex(pose.pose(), (float) x2, (float) maxY, (float) z2)
            .color(red, green, blue, alpha)
            .uv(u1, v0)
            .overlayCoords(OverlayTexture.NO_OVERLAY)
            .uv2(light)
            .normal(pose.normal(), 0.0f, 1.0f, 0.0f)
            .endVertex();
        consumer.vertex(pose.pose(), (float) x2, (float) minY, (float) z2)
            .color(red, green, blue, alpha)
            .uv(u1, v1)
            .overlayCoords(OverlayTexture.NO_OVERLAY)
            .uv2(light)
            .normal(pose.normal(), 0.0f, 1.0f, 0.0f)
            .endVertex();
    }
}
