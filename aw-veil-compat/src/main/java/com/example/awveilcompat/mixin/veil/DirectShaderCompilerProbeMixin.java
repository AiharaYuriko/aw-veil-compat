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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Veil-side probe at DirectShaderCompiler.compile().
 * Captures shader compilation events.
 *
 * Actual Veil source signature (verified):
 *   public CompiledShader compile(int type, VeilShaderSource source)
 *
 * @Pseudo: Veil class may not exist.
 * require=0: optional injection.
 */
@Pseudo
@Mixin(targets = "foundry.veil.impl.client.render.shader.compiler.DirectShaderCompiler")
public abstract class DirectShaderCompilerProbeMixin {

    @Unique
    private static ProbeLogger dcsProbeLogger;

    @Unique
    private static ProbeLogger getLogger() {
        if (dcsProbeLogger == null) {
            dcsProbeLogger = new ProbeLogger("veil");
        }
        return dcsProbeLogger;
    }

    @Inject(method = "compile", at = @At("HEAD"), require = 0)
    private void onCompile(CallbackInfoReturnable<foundry.veil.api.client.render.shader.CompiledShader> cir) {
        if (!ModDetector.isVeilLoaded()) return;
        if (!GlStateReader.isOnRenderThread()) return;

        long nanoTime = System.nanoTime();
        int programId = GlStateReader.readCurrentProgramId();

        String data = String.format(
            "program=%d\tevent=compile",
            programId
        );

        getLogger().write(new ProbeData(nanoTime, "shaderCompile", data));
    }
}
