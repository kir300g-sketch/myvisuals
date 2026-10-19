package com.example.visuals.mixin;

import com.example.visuals.Freecam;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class KeyboardInputMixin {

    // в фрикаме тело игрока стоит на месте
    @Inject(method = "tick(ZF)V", at = @At("TAIL"))
    private void myvisuals$freecam(boolean slowDown, float f, CallbackInfo ci) {
        if (Freecam.active) {
            Input in = (Input) (Object) this;
            in.movementForward = 0f;
            in.movementSideways = 0f;
            in.pressingForward = false;
            in.pressingBack = false;
            in.pressingLeft = false;
            in.pressingRight = false;
            in.jumping = false;
            in.sneaking = false;
        }
    }
}
