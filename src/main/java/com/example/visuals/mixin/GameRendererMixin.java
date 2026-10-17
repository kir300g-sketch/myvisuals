package com.example.visuals.mixin;

import com.example.visuals.Config;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void myvisuals$hurt(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        if (Config.I.antiShake) ci.cancel();
    }

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void myvisuals$bob(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        if (Config.I.antiBob) ci.cancel();
    }
}
