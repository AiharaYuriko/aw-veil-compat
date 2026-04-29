package com.example.awveilcompat.mixin.aw;

import com.example.awveilcompat.detection.ModDetector;
import com.example.awveilcompat.probe.ProbeData;
import com.example.awveilcompat.probe.ProbeLogger;
import org.lwjgl.opengl.GL20;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Core diagnostic probe at the EXACT conflict point.
 *
 * AW reads GL_CURRENT_PROGRAM in push() and stores it as programId.
 * All AW uniform writes then target this programId.
 * If Veil has changed the active shader, AW captures the wrong program.
 *
 * This probe fires AFTER push() captures programId, recording:
 * - AW's captured programId
 * - Actual GL_CURRENT_PROGRAM (should match, mismatch = conflict)
 * - VAO/VBO bindings for context
 *
 * Target: moe.plushie.armourers_workshop.compat.client.renderer.shader.state.AbstractShaderObjectState
 */
@Mixin(targets = "moe.plushie.armourers_workshop.compat.client.renderer.shader.state.AbstractShaderObjectState")
public abstract class AbstractShaderObjectStateProbeMixin {

    @Shadow(remap = false)
    public int programId;

    @Unique
    private static ProbeLogger awProbeLogger;

    @Unique
    private static ProbeLogger getLogger() {
        if (awProbeLogger == null) {
            awProbeLogger = new ProbeLogger("aw");
        }
        return awProbeLogger;
    }

    @Inject(method = "push", at = @At("TAIL"), require = 0)
    private void onPush(CallbackInfo ci) {
        if (!ModDetector.isAWLoaded()) return;

        long nanoTime = System.nanoTime();
        int currentProgram = GL20.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        boolean mismatch = (programId != currentProgram);

        String data = String.format(
            "awProgram=%d\tcurrentProgram=%d\tmismatch=%s\tvao=%d\tvbo=%d\tibo=%d",
            programId,
            currentProgram,
            mismatch,
            GL20.glGetInteger(org.lwjgl.opengl.GL30.GL_VERTEX_ARRAY_BINDING),
            GL20.glGetInteger(GL20.GL_ARRAY_BUFFER_BINDING),
            GL20.glGetInteger(GL20.GL_ELEMENT_ARRAY_BUFFER_BINDING)
        );

        getLogger().write(new ProbeData(nanoTime, "awPush", data));
    }
}
