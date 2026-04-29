package com.example.awveilcompat.mixin.core;

import com.example.awveilcompat.detection.ModDetector;
import com.example.awveilcompat.probe.GlStateReader;
import com.example.awveilcompat.probe.ProbeData;
import com.example.awveilcompat.probe.ProbeLogger;
import net.minecraft.client.renderer.RenderStateShard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * AW-side GL state probe at RenderStateShard.clearRenderState() HEAD.
 *
 * Priority 100 ensures this runs BEFORE AW's own mixin injection
 * (AW uses default Mixin priority 1000 — lower value = higher priority).
 * This captures the GL state that AW "sees" before AW's own code runs.
 *
 * CORRECTION from Plan 01-01 source analysis:
 * AW targets RenderStateShard (not RenderType) at TAIL.
 * We target HEAD of the same method to capture state before AW's injection.
 *
 * Phase 1 only: investigation probe. No fix code.
 */
@Mixin(value = RenderStateShard.class, priority = 100)
public abstract class RenderTypeProbeMixin {

    @Unique
    private static ProbeLogger awProbeLogger;

    @Unique
    private static ProbeLogger getLogger() {
        if (awProbeLogger == null) {
            awProbeLogger = new ProbeLogger("aw");
        }
        return awProbeLogger;
    }

    @Inject(method = "clearRenderState", at = @At("HEAD"))
    private void onClearRenderState(CallbackInfo ci) {
        // Only probe when both AW and Veil are loaded (DETECT-01, DETECT-02)
        if (!ModDetector.isAWLoaded() || !ModDetector.isVeilLoaded()) return;

        // Verify we are on the render thread (PITFALL-04)
        if (!GlStateReader.isOnRenderThread()) return;

        long nanoTime = System.nanoTime();
        int currentProgram = GlStateReader.readCurrentProgramId();

        if (currentProgram == 0) {
            getLogger().write(new ProbeData(
                nanoTime,
                "clearRenderState",
                "program=0\tstatus=NO_PROGRAM_BOUND"
            ));
            return;
        }

        // Query AW uniform locations on the current program
        int mvLoc = GlStateReader.readUniformLocation(currentProgram, "aw_ModelViewMatrix");
        int flagsLoc = GlStateReader.readUniformLocation(currentProgram, "aw_MatrixFlags");
        int texLoc = GlStateReader.readUniformLocation(currentProgram, "aw_TextureMatrix");

        String data = String.format(
            "program=%d\taw_ModelViewMatrix=%d\taw_MatrixFlags=%d\taw_TextureMatrix=%d",
            currentProgram, mvLoc, flagsLoc, texLoc
        );

        getLogger().write(new ProbeData(nanoTime, "clearRenderState", data));
    }
}
