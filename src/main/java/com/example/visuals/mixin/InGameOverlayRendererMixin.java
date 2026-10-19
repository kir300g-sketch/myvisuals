package com.example.visuals.mixin;

import com.example.visuals.Config;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(InGameOverlayRenderer.class)
public class InGameOverlayRendererMixin {

    // огонь на экране от первого лица
    @Redirect(method = "renderOverlays",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isOnFire()Z"))
    private static boolean myvisuals$fire(ClientPlayerEntity player) {
        return !Config.I.noFire && player.isOnFire();
    }
}
