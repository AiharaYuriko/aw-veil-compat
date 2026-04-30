package com.example.awveilcompat.mixin.aw;

import com.example.awveilcompat.detection.ModDetector;
import com.example.awveilcompat.probe.ProbeData;
import com.example.awveilcompat.probe.ProbeLogger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draw-level probe — captures GL state at the moment AW submits geometry.
 *
 * Injects at Shader.render() HEAD (before applyUniforms + draw).
 * Records: shader program, VAO, VBO, active texture, blend, depth state.
 *
 * Target: moe.plushie.armourers_workshop.core.client.shader.Shader
 */
@Mixin(targets = "moe.plushie.armourers_workshop.core.client.shader.Shader")
public abstract class ShaderRenderProbeMixin {

    @Unique
    private static ProbeLogger probeLogger;

    @Unique
    private static ProbeLogger getLogger() {
        if (probeLogger == null) {
            probeLogger = new ProbeLogger("aw");
        }
        return probeLogger;
    }

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void onRender(CallbackInfo ci) {
        if (!ModDetector.isAWLoaded()) return;

        long nanoTime = System.nanoTime();
        int program = GL20.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int vao = GL30.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int vbo = GL20.glGetInteger(GL20.GL_ARRAY_BUFFER_BINDING);
        int ibo = GL20.glGetInteger(GL20.GL_ELEMENT_ARRAY_BUFFER_BINDING);
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        int cullFace = GL11.glIsEnabled(GL11.GL_CULL_FACE) ? 1 : 0;

        String data = String.format(
            "program=%d\tvao=%d\tvbo=%d\tibo=%d\tactiveTex=%d\tblend=%s\tdepth=%s\tcull=%d",
            program, vao, vbo, ibo, activeTexture,
            blend, depthTest, cullFace
        );

        getLogger().write(new ProbeData(nanoTime, "awDraw", data));
    }
}
