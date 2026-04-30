package com.example.awveilcompat.mixin;

import com.example.awveilcompat.detection.ModDetector;
import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import java.util.List;
import java.util.Set;

public class AwVeilCompatMixinPlugin implements IMixinConfigPlugin {

    private Boolean awLoaded;
    private Boolean veilLoaded;

    @Override
    public void onLoad(String mixinPackage) {
        this.awLoaded = FMLLoader.getLoadingModList()
                .getModFileById(ModDetector.AW_MOD_ID) != null;
        this.veilLoaded = FMLLoader.getLoadingModList()
                .getModFileById(ModDetector.VEIL_MOD_ID) != null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.contains(".mixin.aw.")) {
            return awLoaded;
        }
        if (mixinClassName.contains(".mixin.veil.")) {
            return veilLoaded;
        }
        return true;
    }

    // ---- Required stubs ----
    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
