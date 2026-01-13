package com.mcprotector.client;

import com.mcprotector.network.FactionClaimMapPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.Camera;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.Map;

public final class FactionClaimBorderRenderer {
    private static final float BORDER_ALPHA = 0.35f;
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
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());
        for (Map.Entry<Long, FactionClaimMapPacket.ClaimEntry> entry : snapshot.claims().entrySet()) {
            ChunkPos chunkPos = new ChunkPos(entry.getKey());
            int dx = chunkPos.x - playerChunk.x;
            int dz = chunkPos.z - playerChunk.z;
            if (Math.abs(dx) > renderRadius || Math.abs(dz) > renderRadius) {
                continue;
            }
            int color = entry.getValue().color();
            float red = ((color >> 16) & 0xFF) / 255.0f;
            float green = ((color >> 8) & 0xFF) / 255.0f;
            float blue = (color & 0xFF) / 255.0f;
            double minX = chunkPos.getMinBlockX();
            double maxX = chunkPos.getMaxBlockX() + 1.0;
            double minZ = chunkPos.getMinBlockZ();
            double maxZ = chunkPos.getMaxBlockZ() + 1.0;
            double minY = client.level.getMinBuildHeight();
            double maxY = client.level.getMaxBuildHeight();
            drawLine(consumer, pose, minX, minY, minZ, maxX, minY, minZ, red, green, blue, BORDER_ALPHA);
            drawLine(consumer, pose, maxX, minY, minZ, maxX, minY, maxZ, red, green, blue, BORDER_ALPHA);
            drawLine(consumer, pose, maxX, minY, maxZ, minX, minY, maxZ, red, green, blue, BORDER_ALPHA);
            drawLine(consumer, pose, minX, minY, maxZ, minX, minY, minZ, red, green, blue, BORDER_ALPHA);

            drawLine(consumer, pose, minX, maxY, minZ, maxX, maxY, minZ, red, green, blue, BORDER_ALPHA);
            drawLine(consumer, pose, maxX, maxY, minZ, maxX, maxY, maxZ, red, green, blue, BORDER_ALPHA);
            drawLine(consumer, pose, maxX, maxY, maxZ, minX, maxY, maxZ, red, green, blue, BORDER_ALPHA);
            drawLine(consumer, pose, minX, maxY, maxZ, minX, maxY, minZ, red, green, blue, BORDER_ALPHA);

            drawLine(consumer, pose, minX, minY, minZ, minX, maxY, minZ, red, green, blue, BORDER_ALPHA);
            drawLine(consumer, pose, maxX, minY, minZ, maxX, maxY, minZ, red, green, blue, BORDER_ALPHA);
            drawLine(consumer, pose, maxX, minY, maxZ, maxX, maxY, maxZ, red, green, blue, BORDER_ALPHA);
            drawLine(consumer, pose, minX, minY, maxZ, minX, maxY, maxZ, red, green, blue, BORDER_ALPHA);
        }
        bufferSource.endBatch();
        poseStack.popPose();
    }

    private static void drawVerticalQuad(VertexConsumer consumer, PoseStack.Pose pose, double x1, double z1,
                                         double x2, double z2, double minY, double maxY, float red, float green,
                                         float blue, float alpha, float u0, float u1, float v0, float v1) {
        int light = LightTexture.FULL_BRIGHT;
        consumer.addVertex(pose.pose(), (float) x1, (float) y1, (float) z1)
            .setColor(red, green, blue, alpha)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(0.0f, 1.0f, 0.0f);
        consumer.addVertex(pose.pose(), (float) x2, (float) y2, (float) z2)
            .setColor(red, green, blue, alpha)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(0.0f, 1.0f, 0.0f);
    }
}
