package com.example.awveilcompat;

import com.example.awveilcompat.detection.ModDetector;
import net.neoforged.fml.common.Mod;

@Mod("awveilcompat")
public class AwVeilCompat {

    public AwVeilCompat() {
        ModDetector.init();
    }
}
