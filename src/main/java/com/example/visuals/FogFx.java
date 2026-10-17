package com.example.visuals;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.CameraSubmersionType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;

import java.awt.Color;

public class FogFx {
    public static void apply(Camera camera, BackgroundRenderer.FogType type, float viewDistance) {
        Config c = Config.I;
        if (!c.fogRemove && !c.hazeEnabled) return;
        if (type != BackgroundRenderer.FogType.FOG_TERRAIN) return;
        if (camera.getSubmersionType() != CameraSubmersionType.NONE) return;
        if (camera.getFocusedEntity() instanceof LivingEntity le
                && (le.hasStatusEffect(StatusEffects.BLINDNESS) || le.hasStatusEffect(StatusEffects.DARKNESS))) return;

        if (c.hazeEnabled) {
            float end = Math.max(10f, c.hazeDist);
            float start = end * (1f - Math.max(0.05f, Math.min(1f, c.hazeDensity)));
            RenderSystem.setShaderFogStart(start);
            RenderSystem.setShaderFogEnd(end);
            float h = c.hazeRainbow ? (System.currentTimeMillis() % 8000) / 8000f : c.hazeHue;
            int rgb = Color.HSBtoRGB(h, 0.45f, 0.95f) & 0xFFFFFF;
            RenderSystem.setShaderFogColor(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f);
        } else {
            RenderSystem.setShaderFogStart(viewDistance * 8f);
            RenderSystem.setShaderFogEnd(viewDistance * 10f);
        }
    }
}
