package com.example.visuals.mixin;

import com.example.visuals.Freelook;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Camera.class)
public class CameraMixin {

    @ModifyVariable(method = "setRotation", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private float myvisuals$yaw(float yaw) {
        return Freelook.active ? Freelook.yaw : yaw;
    }

    @ModifyVariable(method = "setRotation", at = @At("HEAD"), ordinal = 1, argsOnly = true)
    private float myvisuals$pitch(float pitch) {
        return Freelook.active ? Freelook.pitch : pitch;
    }
}
