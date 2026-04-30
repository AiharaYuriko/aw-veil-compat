package com.example.awveilcompat.detection;

import net.neoforged.fml.ModList;

public final class ModDetector {
    private ModDetector() {}

    public static final String AW_MOD_ID = "armourers_workshop";
    public static final String VEIL_MOD_ID = "veil";

    private static Boolean awCached;
    private static Boolean veilCached;

    public static void init() {
        awCached = ModList.get().isLoaded(AW_MOD_ID);
        veilCached = ModList.get().isLoaded(VEIL_MOD_ID);
    }

    public static boolean isAWLoaded() {
        return awCached != null ? awCached : ModList.get().isLoaded(AW_MOD_ID);
    }

    public static boolean isVeilLoaded() {
        return veilCached != null ? veilCached : ModList.get().isLoaded(VEIL_MOD_ID);
    }
}
