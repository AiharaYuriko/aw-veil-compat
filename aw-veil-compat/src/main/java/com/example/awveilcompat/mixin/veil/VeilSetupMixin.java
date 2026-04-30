package com.example.awveilcompat.mixin.veil;

import com.example.awveilcompat.shader.AwVeilShaderResourceProvider;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Wraps the ResourceProvider at the start of VanillaShaderProcessor.setup().
 *
 * Veil stores this provider in ShaderProcessorList (ThreadLocal), then uses it
 * for all vanilla shader compilation. By wrapping it here, AW uniform
 * declarations are injected into every .vsh file that Veil processes.
 *
 * Mirrors AW's ShaderIrisMixin approach.
 */
@Pseudo
@Mixin(targets = "foundry.veil.impl.client.render.shader.processor.VanillaShaderProcessor")
public class VeilSetupMixin {

    @ModifyVariable(method = "setup", at = @At("HEAD"), argsOnly = true)
    private static ResourceProvider aw2$wrapProvider(ResourceProvider provider) {
        return new AwVeilShaderResourceProvider(provider);
    }
}
