package com.example.awveilcompat.detection;

import net.neoforged.fml.ModList;

public final class ModDetector {
    private ModDetector() {}

    // Verified from cloned source
    public static final String AW_MOD_ID = "armourers_workshop";
    public static final String VEIL_MOD_ID = "veil";
    /** Old VBO-disabling mod — conflicts with our shader injection approach. */
    public static final String OLD_COMPAT_MOD_ID = "aw_veil_compat";

    private static Boolean awCached;
    private static Boolean veilCached;
    private static Boolean oldCompatCached;

    /** Called during mod constructor to cache once. */
    public static void init() {
        awCached = ModList.get().isLoaded(AW_MOD_ID);
        veilCached = ModList.get().isLoaded(VEIL_MOD_ID);
        oldCompatCached = ModList.get().isLoaded(OLD_COMPAT_MOD_ID);
    }

    public static boolean isAWLoaded() {
        return awCached != null ? awCached : ModList.get().isLoaded(AW_MOD_ID);
    }

    public static boolean isVeilLoaded() {
        return veilCached != null ? veilCached : ModList.get().isLoaded(VEIL_MOD_ID);
    }

    /** True if the old VBO-disabling compat mod is present. Our mod should disable itself. */
    public static boolean isOldCompatLoaded() {
        return oldCompatCached != null ? oldCompatCached : ModList.get().isLoaded(OLD_COMPAT_MOD_ID);
    }
}
