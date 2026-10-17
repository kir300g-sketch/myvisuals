package com.example.visuals.mixin;

import com.example.visuals.FogFx;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BackgroundRenderer.class)
public class BackgroundRendererMixin {

    @Inject(method = "applyFog", at = @At("TAIL"))
    private static void myvisuals$fog(Camera camera, BackgroundRenderer.FogType fogType, float viewDistance,
                                      boolean thickFog, float tickDelta, CallbackInfo ci) {
        FogFx.apply(camera, fogType, viewDistance);
    }
}
