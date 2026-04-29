package com.example.awveilcompat.mixin.aw;

import com.example.awveilcompat.detection.ModDetector;
import com.example.awveilcompat.probe.ProbeData;
import com.example.awveilcompat.probe.ProbeLogger;
import org.lwjgl.opengl.GL20;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Core diagnostic probe at THE exact conflict point.
 *
 * AW's ShaderUniforms.end() reads GL_CURRENT_PROGRAM to determine
 * which program to apply uniforms for. AW then writes aw_ModelViewMatrix,
 * aw_MatrixFlags, aw_TextureMatrix to that program via glUniform*().
 *
 * If Veil has changed the active shader program, AW captures Veil's
 * program ID. Veil's shader doesn't have AW's uniform names, so
 * glGetUniformLocation() returns -1 and uniforms are silently dropped.
 *
 * This probe fires at ShaderUniforms.end() HEAD to capture the program
 * AW is about to use for all pending uniform applications.
 *
 * Verified target: moe.plushie.armourers_workshop.core.client.shader.ShaderUniforms
 * Method: end() — reads GL20.glGetInteger(35725) = GL_CURRENT_PROGRAM
 */
@Mixin(targets = "moe.plushie.armourers_workshop.core.client.shader.ShaderUniforms")
public abstract class ShaderUniformsProbeMixin {

    @Unique
    private static ProbeLogger awProbeLogger;

    @Unique
    private static ProbeLogger getLogger() {
        if (awProbeLogger == null) {
            awProbeLogger = new ProbeLogger("aw");
        }
        return awProbeLogger;
    }

    @Inject(method = "end", at = @At("HEAD"), require = 0)
    private static void onEnd(CallbackInfo ci) {
        if (!ModDetector.isAWLoaded()) return;

        long nanoTime = System.nanoTime();
        int currentProgram = GL20.glGetInteger(GL20.GL_CURRENT_PROGRAM);

        String data = String.format(
            "program=%d\tevent=uniformFlush\tstage=begin",
            currentProgram
        );

        getLogger().write(new ProbeData(nanoTime, "awUniformFlush", data));
    }

    @Inject(method = "apply", at = @At("HEAD"), require = 0)
    private static void onApply(int program, CallbackInfo ci) {
        if (!ModDetector.isAWLoaded()) return;

        long nanoTime = System.nanoTime();
        int currentProgram = GL20.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        boolean mismatch = (program != currentProgram);

        String data = String.format(
            "awProgram=%d\tcurrentProgram=%d\tmismatch=%s",
            program,
            currentProgram,
            mismatch
        );

        getLogger().write(new ProbeData(nanoTime, "awApplyUniforms", data));
    }
}
