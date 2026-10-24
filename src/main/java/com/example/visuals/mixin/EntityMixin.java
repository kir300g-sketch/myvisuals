package com.example.visuals.mixin;

import com.example.visuals.Freecam;
import com.example.visuals.Freelook;
import com.example.visuals.SmoothLook;
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
                if ((Object) this != MinecraftClient.getInstance().player) return;
        
        if (Freecam.active) {
            Freecam.rotate(cursorDeltaX, cursorDeltaY);
            ci.cancel();
        } else if (Freelook.active) {
            Freelook.rotate(cursorDeltaX, cursorDeltaY);
            ci.cancel();
        } else if (SmoothLook.enabled()) {
            SmoothLook.add(cursorDeltaX, cursorDeltaY);
            ci.cancel();
    }
}
    }

