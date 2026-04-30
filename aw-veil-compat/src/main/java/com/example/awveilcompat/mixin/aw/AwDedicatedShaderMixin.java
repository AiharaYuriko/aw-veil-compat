package com.example.awveilcompat.mixin.aw;

import com.example.awveilcompat.detection.ModDetector;
import com.example.awveilcompat.shader.AwDedicatedProgram;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL20;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin that saves the current GL program, binds AW's dedicated shader program
 * before AW's uniform application, and restores the original program after.
 * <p>
 * <b>Problem:</b> AW's {@code ShaderUniforms.end()} reads
 * {@code GL_CURRENT_PROGRAM} to determine which program to apply 7 AW uniforms
 * to. When Veil (or any shader-modifying mod) is active, the current program
 * is not AW's -- it does not declare {@code aw_ModelViewMatrix},
 * {@code aw_MatrixFlags}, etc. {@code glGetUniformLocation} returns -1 for all
 * AW uniforms, which are silently dropped. AW VBO models do not render.
 * <p>
 * <b>Fix:</b> At {@code end()} HEAD, save the current GL program ID, bind
 * AW's dedicated shader program (which declares all 7 AW uniforms), upload
 * current matrices from {@link RenderSystem}, then let AW's normal flow apply
 * uniforms and trigger the VBO draw. At {@code end()} RETURN, restore the
 * original program.
 * <p>
 * Re-entrancy is protected by a {@code reentrantDepth} counter. Program
 * creation failures are permanent (set {@code awProgram = -1}) and all future
 * calls are skipped -- the mod degrades gracefully to the original (unfixed)
 * behavior rather than crashing.
 * <p>
 * <b>Coexistence:</b> The existing {@link ShaderUniformsProbeMixin} also
 * {@code @Inject}s at {@code end()} HEAD. Both fire. The probe logs whatever
 * {@code GL_CURRENT_PROGRAM} is at its handler time -- either our program or
 * the original depending on mixin ordering. Either result is informative.
 *
 * @see AwDedicatedProgram
 */
@Pseudo
@Mixin(targets = "moe.plushie.armourers_workshop.core.client.shader.ShaderUniforms")
public class AwDedicatedShaderMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("awveilcompat");

    static {
        LOGGER.info("[AW-Veil Compat] AwDedicatedShaderMixin class loaded");
    }

    /** AW's dedicated shader program ID. 0 = uninitialized, -1 = failed, >0 = valid. */
    private static int awProgram = 0;

    /** GL program ID to restore in the TAIL handler. Set at HEAD, consumed at RETURN. */
    private static int savedProgram = 0;

    /**
     * Re-entrancy counter. 0 = outside {@code end()}. >0 = inside {@code end()}.
     * Nested recursive calls increment without re-saving; the outermost RETURN
     * handles restore.
     */
    private static int reentrantDepth = 0;

    /**
     * HEAD injection: save current GL program, bind AW's dedicated program,
     * upload matrices.
     * <p>
     * Guard chain:
     * <ol>
     *   <li>Skip if AW mod is not loaded</li>
     *   <li>Skip if already inside a re-entrant {@code end()} call</li>
     *   <li>Lazy-init AW program on render thread (skip if GL not ready)</li>
     *   <li>Skip if program creation failed (awProgram = -1)</li>
     *   <li>Skip if AW's program is already bound (re-entrancy guard)</li>
     * </ol>
     */
    @Inject(method = "end", at = @At("HEAD"), require = 0)
    private static void onEnd(CallbackInfo ci) {
        // Guard: only act when AW is loaded
        if (!ModDetector.isAWLoaded()) return;

        // Re-entrancy guard: if end() is called recursively, increment and skip
        if (reentrantDepth > 0) {
            reentrantDepth++;
            return;
        }

        // Lazy init: compile the dedicated shader program on the render thread
        if (awProgram == 0) {
            LOGGER.info("[AW-Veil Compat] Lazy-init: awProgram={}, onRenderThread={}", awProgram, RenderSystem.isOnRenderThread());
            if (!RenderSystem.isOnRenderThread()) return; // GL context not available yet
            try {
                awProgram = AwDedicatedProgram.create();
                LOGGER.info("[AW-Veil Compat] Dedicated shader program created: id={}", awProgram);
            } catch (Exception e) {
                LOGGER.error("[AW-Veil Compat] Failed to create dedicated shader program", e);
                awProgram = -1; // permanent failure, skip for the session
                return;
            }
        }

        // Skip if creation failed (awProgram == -1) or invalid
        if (awProgram <= 0) return;

        // Save the currently bound GL program
        int current = GL20.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        if (current == awProgram) return; // already our program bound, nothing to do
        savedProgram = current;

        // Bind AW's dedicated program and upload current matrices
        reentrantDepth = 1;
        GL20.glUseProgram(awProgram);
        AwDedicatedProgram.uploadMatrices(awProgram);
    }

    /**
     * TAIL injection: restore the original GL program after AW's uniform
     * application and VBO draw complete.
     * <p>
     * Handles nested depth: decrements inner calls without restoring.
     * Only the outermost RETURN restores {@code savedProgram}.
     */
    @Inject(method = "end", at = @At("RETURN"), require = 0)
    private static void onEndReturn(CallbackInfo ci) {
        // No active save from HEAD -- nothing to restore
        if (reentrantDepth == 0) return;

        // Nested recursive call: decrement and let outer call handle restore
        if (reentrantDepth > 1) {
            reentrantDepth--;
            return;
        }

        // Outermost call: reset depth and restore the original program
        reentrantDepth = 0;

        if (savedProgram != 0 && savedProgram != awProgram) {
            GL20.glUseProgram(savedProgram);
        }
        savedProgram = 0;
    }
}
