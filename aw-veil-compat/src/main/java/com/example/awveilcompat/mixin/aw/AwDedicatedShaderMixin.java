package com.example.awveilcompat.mixin.aw;

import com.example.awveilcompat.detection.ModDetector;
import com.example.awveilcompat.shader.AwDedicatedProgram;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL20;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces AW's captured GL program ID with our dedicated shader program.
 *
 * AW's ShaderRenderState.save() reads GL_CURRENT_PROGRAM and stores it
 * as {@code programId}. This ID is then used for all uniform operations
 * (getLastProgramId() → ShaderUniforms.apply(programId)).
 *
 * When Veil is active, GL_CURRENT_PROGRAM is a Veil shader without AW's
 * uniforms → glGetUniformLocation returns -1 → uniforms silently dropped.
 *
 * Fix: After save() captures the program, overwrite programId with our
 * dedicated shader that declares all 7 AW uniforms. AW's uniform system
 * now writes to a program that accepts them.
 *
 * Target: moe.plushie.armourers_workshop.core.client.shader.ShaderRenderState
 */
@Pseudo
@Mixin(targets = "moe.plushie.armourers_workshop.core.client.shader.ShaderRenderState")
public class AwDedicatedShaderMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("awveilcompat");

    static {
        LOGGER.info("[AW-Veil Compat] AwDedicatedShaderMixin class loaded");
    }

    @Shadow(remap = false)
    private int programId;

    private static int awProgram = 0;

    @Inject(method = "save", at = @At("TAIL"), require = 0)
    private void onSave(CallbackInfo ci) {
        if (!ModDetector.isAWLoaded()) return;

        // Lazy init on render thread
        if (awProgram == 0) {
            if (!RenderSystem.isOnRenderThread()) return;
            try {
                awProgram = AwDedicatedProgram.create();
                LOGGER.info("[AW-Veil Compat] Dedicated shader created: id={}", awProgram);
            } catch (Exception e) {
                LOGGER.error("[AW-Veil Compat] Shader creation failed", e);
                awProgram = -1;
                return;
            }
        }

        if (awProgram <= 0) return;

        // Overwrite captured programId with our dedicated shader
        // AW will now apply uniforms to our program which has all AW uniforms
        programId = awProgram;

        // Also bind our shader so the actual GL draw uses it
        // save() is called from prepare() before render()/drawElements()
        GL20.glUseProgram(awProgram);
    }

    /**
     * Ensure our shader is bound before the draw call.
     * load() restores VAO/VBO/IBO but NOT the program — we add program binding.
     */
    @Inject(method = "load", at = @At("TAIL"), require = 0)
    private void onLoad(CallbackInfo ci) {
        if (!ModDetector.isAWLoaded()) return;
        if (awProgram <= 0) return;

        // Re-bind our shader — load() restored VAO/VBO/IBO, we ensure correct program
        GL20.glUseProgram(awProgram);
        AwDedicatedProgram.uploadMatrices(awProgram);
    }
}
