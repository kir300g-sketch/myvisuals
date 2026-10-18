package com.example.visuals.mixin;

import com.example.visuals.Freelook;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class EntityMixin {

    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void myvisuals$look(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if (Freelook.active && (Object) this == MinecraftClient.getInstance().player) {
            Freelook.rotate(cursorDeltaX, cursorDeltaY);
            ci.cancel();
        }
    }
}
