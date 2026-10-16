package com.example.visuals.mixin;

import com.example.visuals.SwordPose;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public class HeldItemRendererMixin {

    // отключаем ванильную анимацию удара, если включена наша
    @ModifyVariable(method = "renderFirstPersonItem", at = @At("HEAD"), ordinal = 2, argsOnly = true)
    private float myvisuals$swing(float value) {
        return SwordPose.replacesSwing() ? 0f : value;
    }

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
    private void myvisuals$pose(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
                                float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
                                VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        SwordPose.apply(player, tickDelta, hand, item, matrices);
    }
}
