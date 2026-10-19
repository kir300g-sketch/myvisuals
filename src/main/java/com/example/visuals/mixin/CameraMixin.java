package com.example.visuals.mixin;

import com.example.visuals.Freecam;
import com.example.visuals.Freelook;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Camera.class)
public class CameraMixin {

    @ModifyVariable(method = "setRotation", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private float myvisuals$yaw(float yaw) {
        if (Freecam.active) return Freecam.yaw;
        return Freelook.active ? Freelook.yaw : yaw;
    }

    @ModifyVariable(method = "setRotation", at = @At("HEAD"), ordinal = 1, argsOnly = true)
    private float myvisuals$pitch(float pitch) {
        if (Freecam.active) return Freecam.pitch;
        return Freelook.active ? Freelook.pitch : pitch;
    }

    @ModifyVariable(method = "setPos(DDD)V", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private double myvisuals$x(double x) {
        if (Freecam.active) {
            Freecam.frame();
            return Freecam.x;
        }
        return x;
    }

    @ModifyVariable(method = "setPos(DDD)V", at = @At("HEAD"), ordinal = 1, argsOnly = true)
    private double myvisuals$y(double y) {
        return Freecam.active ? Freecam.y : y;
    }

    @ModifyVariable(method = "setPos(DDD)V", at = @At("HEAD"), ordinal = 2, argsOnly = true)
    private double myvisuals$z(double z) {
        return Freecam.active ? Freecam.z : z;
    }

    // в третьем лице камера отодвигается назад: во фрикаме это не нужно
    @ModifyVariable(method = "moveBy(DDD)V", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private double myvisuals$mx(double v) {
        return Freecam.active ? 0.0 : v;
    }

    @ModifyVariable(method = "moveBy(DDD)V", at = @At("HEAD"), ordinal = 1, argsOnly = true)
    private double myvisuals$my(double v) {
        return Freecam.active ? 0.0 : v;
    }

    @ModifyVariable(method = "moveBy(DDD)V", at = @At("HEAD"), ordinal = 2, argsOnly = true)
    private double myvisuals$mz(double v) {
        return Freecam.active ? 0.0 : v;
    }
}
