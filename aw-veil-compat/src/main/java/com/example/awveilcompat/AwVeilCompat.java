package com.example.awveilcompat;

import com.example.awveilcompat.detection.ModDetector;
import net.neoforged.fml.common.Mod;

@Mod("awveilcompat")
public class AwVeilCompat {

    public AwVeilCompat() {
        // Cache mod presence at construction time (ModList.get() is available here)
        ModDetector.init();

        // Phase 1: investigation only — no fix code
        // Phase 2+ will register mixin configs and event handlers here
    }
}
