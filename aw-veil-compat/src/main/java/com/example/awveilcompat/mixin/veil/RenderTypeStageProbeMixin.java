package com.example.awveilcompat.mixin.veil;

import com.example.awveilcompat.detection.ModDetector;
import com.example.awveilcompat.probe.GlStateReader;
import com.example.awveilcompat.probe.ProbeData;
import com.example.awveilcompat.probe.ProbeLogger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Veil-side probe at ForgeRenderTypeStageHandler.register().
 * Captures shader lifecycle events when Veil registers a new render type stage.
 *
 * CORRECTION from Plan 01-01 source analysis:
 * There is NO RenderTypeStageRegistry class. Veil uses ForgeRenderTypeStageHandler
 * with NeoForge RenderLevelStageEvent.Stage for stage management.
 *
 * Actual Veil source signature (verified):
 *   public static synchronized void register(
 *       @Nullable RenderLevelStageEvent.Stage stage, RenderType renderType)
 *
 * @Pseudo: Veil class may not exist (second safety layer beyond MixinPlugin).
 * require=0: optional injection — silently skip if method signature changes.
 */
@Pseudo
@Mixin(targets = "foundry.veil.forge.impl.ForgeRenderTypeStageHandler")
public abstract class RenderTypeStageProbeMixin {

    @Unique
    private static ProbeLogger rtsProbeLogger;

    @Unique
    private static ProbeLogger getLogger() {
        if (rtsProbeLogger == null) {
            rtsProbeLogger = new ProbeLogger("veil");
        }
        return rtsProbeLogger;
    }

    @Inject(method = "register", at = @At("HEAD"), require = 0)
    private void onRegister(CallbackInfo ci) {
        if (!ModDetector.isVeilLoaded()) return;
        if (!GlStateReader.isOnRenderThread()) return;

        long nanoTime = System.nanoTime();
        int programId = GlStateReader.readCurrentProgramId();

        String data = String.format(
            "program=%d\tevent=registerStage",
            programId
        );

        getLogger().write(new ProbeData(nanoTime, "renderTypeStage", data));
    }
}
