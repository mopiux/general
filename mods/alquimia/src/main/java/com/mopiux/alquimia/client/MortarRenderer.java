package com.mopiux.alquimia.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mopiux.alquimia.alchemy.Grinding;
import com.mopiux.alquimia.block.MortarBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Muestra el ingrediente dentro del mortero (aplastado cuando ya está molido). */
public class MortarRenderer implements BlockEntityRenderer<MortarBlockEntity> {
    public MortarRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(MortarBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ItemStack stack = be.stack();
        if (stack.isEmpty()) return;
        boolean ground = Grinding.get(stack) >= 1f;
        int copies = Math.min(3, 1 + stack.getCount() / 6);
        for (int i = 0; i < copies; i++) {
            pose.pushPose();
            pose.translate(0.5, 0.2 + i * 0.02, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(i * 47f + be.getBlockPos().hashCode() % 360));
            pose.mulPose(Axis.XP.rotationDegrees(90f));
            float s = ground ? 0.42f : 0.38f;
            pose.scale(s, s, ground ? 0.25f : s);
            Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light, overlay, pose,
                    buffers, be.getLevel(), (int) be.getBlockPos().asLong() + i);
            pose.popPose();
        }
    }
}
