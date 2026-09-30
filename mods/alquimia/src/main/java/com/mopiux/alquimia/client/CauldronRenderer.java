package com.mopiux.alquimia.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mopiux.alquimia.block.AlchemicalCauldronBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Dibuja el líquido del caldero con el color de la mezcla (agua, esencia cercana o elixir). */
public class CauldronRenderer implements BlockEntityRenderer<AlchemicalCauldronBlockEntity> {
    private static final ResourceLocation WATER = new ResourceLocation("minecraft", "block/water_still");

    public CauldronRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(AlchemicalCauldronBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        int water = be.visualWater();
        if (water <= 0) return;
        float h = (6f + water * 3f) / 16f;
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(WATER);
        int color = be.visualColor();
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF, a = 220;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS));
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float min = 2f / 16f, max = 14f / 16f;
        float u0 = sprite.getU(2), u1 = sprite.getU(14), v0 = sprite.getV(2), v1 = sprite.getV(14);
        vertex(vc, m, n, min, h, min, u0, v0, r, g, b, a, light);
        vertex(vc, m, n, min, h, max, u0, v1, r, g, b, a, light);
        vertex(vc, m, n, max, h, max, u1, v1, r, g, b, a, light);
        vertex(vc, m, n, max, h, min, u1, v0, r, g, b, a, light);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v,
                               int r, int g, int b, int a, int light) {
        vc.vertex(m, x, y, z).color(r, g, b, a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(n, 0f, 1f, 0f).endVertex();
    }
}
