package com.example.visuals.mixin;

import com.example.visuals.ChunkRise;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {

    @Redirect(method = "renderLayer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/GlUniform;set(FFF)V"),
            require = 0)
    private void myvisuals$chunkOffset(GlUniform uniform, float x, float y, float z) {
        uniform.set(x, y + ChunkRise.offset(x, y, z), z);
    }
}
