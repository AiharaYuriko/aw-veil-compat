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
            LOGGER.warn("[AW-Veil Compat] Detected old aw_veil_compat mod (VBO-disabling approach). "
                    + "This mod is already handling AW-Veil compatibility. "
                    + "awveilcompat mixins will be disabled to avoid conflicts.");
        }
    }
}
