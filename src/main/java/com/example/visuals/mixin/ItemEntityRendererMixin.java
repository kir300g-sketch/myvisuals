package com.example.visuals.mixin;

import com.example.visuals.Config;
import com.example.visuals.ItemPhysics;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public class ItemEntityRendererMixin {

    @Inject(method = "render(Lnet/minecraft/entity/ItemEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"))
    private void myvisuals$head(ItemEntity e, float f, float g, MatrixStack ms, VertexConsumerProvider vcp, int light, CallbackInfo ci) {
        ItemPhysics.current = e;
    }

    @Inject(method = "render(Lnet/minecraft/entity/ItemEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("RETURN"))
    private void myvisuals$tail(ItemEntity e, float f, float g, MatrixStack ms, VertexConsumerProvider vcp, int light, CallbackInfo ci) {
        ItemPhysics.current = null;
    }

    // убираем подпрыгивание
    @Redirect(method = "render(Lnet/minecraft/entity/ItemEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/MathHelper;sin(F)F"))
    private float myvisuals$sin(float v) {
        return Config.I.itemPhysics ? -1f : MathHelper.sin(v);
    }

    // вместо вращения: случайный угол и укладывание плашмя
    @Redirect(method = "render(Lnet/minecraft/entity/ItemEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;multiply(Lorg/joml/Quaternionf;)V"))
    private void myvisuals$rot(MatrixStack ms, Quaternionf q) {
        ItemPhysics.rotate(ms, q);
    }
}
