package com.example.awveilcompat.mixin.core;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Pseudo;

/**
 * Neutralizes the old aw_veil_compat mod's VBO-disabling mixin.
 *
 * The old mod's mixin replaces read of ModDebugger.vbo with a method
 * that returns 2 when Veil/Sable is loaded, forcing immediate-mode
 * rendering. We overwrite that method to always return ModDebugger.vbo
 * (which defaults to 0 = VBO enabled).
 *
 * Our shader-level fix handles Veil compatibility without disabling VBO.
 */
@Pseudo
@Mixin(targets = "com.codex.awveilcompat.mixin.AwSkinVertexBufferBuilderMixin", priority = 100)
public class OldModDisablerMixin {

    @Overwrite(remap = false)
    private int awvc$forceNoVboWithVeil() {
        // Return original vbo value, ignoring old mod's force-disable logic.
        // When the old mod is not loaded, this mixin is skipped via @Pseudo.
        // Default vbo = 0 means AW uses VBO rendering (our shader fix handles Veil).
        return 0;
    }
}
