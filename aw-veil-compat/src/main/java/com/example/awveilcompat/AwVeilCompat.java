package com.example.awveilcompat;

import com.example.awveilcompat.detection.ModDetector;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod("awveilcompat")
public class AwVeilCompat {

    private static final Logger LOGGER = LoggerFactory.getLogger("awveilcompat");

    public AwVeilCompat() {
        ModDetector.init();

        if (ModDetector.isOldCompatLoaded()) {
            LOGGER.warn("[AW-Veil Compat] Detected old aw_veil_compat mod. "
                    + "Overriding its VBO-disabling behavior — this mod provides "
                    + "shader-level compatibility that works with VBO enabled.");

            // Force VBO back on. The old mod sets ModDebugger.vbo = 2 at
            // mixin injection time, which disables AW's VBO rendering.
            // We re-enable it since our shader-level fix handles the
            // Veil incompatibility without the VBO performance penalty.
            try {
                Class<?> debuggerClass = Class.forName(
                        "moe.plushie.armourers_workshop.init.ModDebugger");
                java.lang.reflect.Field vboField = debuggerClass.getField("vbo");
                vboField.setInt(null, 0);
                LOGGER.info("[AW-Veil Compat] ModDebugger.vbo restored to 0 (VBO enabled). "
                        + "Old mod's VBO-disabling behavior neutralized.");
            } catch (Exception e) {
                LOGGER.error("[AW-Veil Compat] Failed to override ModDebugger.vbo", e);
            }
        }
    }
}
