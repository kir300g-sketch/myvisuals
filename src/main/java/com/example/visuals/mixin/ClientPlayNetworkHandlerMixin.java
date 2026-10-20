package com.example.visuals.mixin;

import com.example.visuals.Config;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {

    // раз в секунду сервер присылает время и вызывал мигание: пока наше время включено, пакет игнорируем
    @Inject(method = "onWorldTimeUpdate", at = @At("HEAD"), cancellable = true)
    private void myvisuals$time(WorldTimeUpdateS2CPacket packet, CallbackInfo ci) {
        if (Config.I.timeEnabled) ci.cancel();
    }
}
