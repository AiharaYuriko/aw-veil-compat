package com.example.awveilcompat.mixin.core;

import com.example.awveilcompat.detection.ModDetector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Neutralizes the old aw_veil_compat mod's VBO-disabling mixin.
 *
 * Both mods target SkinVertexBufferBuilder.addPart() where ModDebugger.vbo
 * is read. The old mod's mixin (priority=1000 default) sets it to 2 when
 * Veil is loaded. Our mixin (priority=2000) runs AFTER and overrides vbo
 * back to 0 — keeping VBO enabled since our shader fix handles Veil.
 *
 * Safe: @Pseudo skips when AW is absent. Mixin priority 2000 > 1000 default
 * means our @ModifyVariable fires last (highest priority wins the variable).
 */
@Pseudo
@Mixin(targets = "moe.plushie.armourers_workshop.core.client.other.SkinVertexBufferBuilder", priority = 2000)
public class OldModDisablerMixin {

    @ModifyVariable(
            method = "addPart",
            at = @At(value = "FIELD", target = "Lmoe/plushie/armourers_workshop/init/ModDebugger;vbo:I"),
            remap = false
    )
    private int awvc$forceVboEnabled(int vbo) {
        if (!ModDetector.isOldCompatLoaded()) return vbo; // Let vanilla behavior pass through
        return 0; // Force VBO enabled — our shader fix handles Veil
    }
}
