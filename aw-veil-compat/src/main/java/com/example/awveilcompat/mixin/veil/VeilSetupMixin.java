package com.example.awveilcompat.mixin.veil;

import com.example.awveilcompat.shader.AwVeilShaderResourceProvider;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Two-pronged approach to inject AW uniforms into Veil's shader pipeline.
 *
 * 1. @ModifyVariable on VanillaShaderProcessor.setup(): wraps the ResourceProvider
 *    stored in ShaderProcessorList, covering ShaderImporter (#include resolution).
 *
 * 2. @ModifyVariable on VanillaShaderProcessor.modify(): transforms the shader
 *    source string BEFORE Veil's processors (ShaderModifyProcessor, etc.) see it.
 *    This covers the main shader source that VanillaShaderCompiler loads from
 *    resourceManager directly (bypassing our wrapped provider).
 */
@Pseudo
@Mixin(targets = "foundry.veil.impl.client.render.shader.processor.VanillaShaderProcessor")
public class VeilSetupMixin {

    /** Wrap the ResourceProvider for ShaderImporter (#include files). */
    @ModifyVariable(method = "setup", at = @At("HEAD"), argsOnly = true)
    private static ResourceProvider aw2$wrapSetupProvider(ResourceProvider provider) {
        return new AwVeilShaderResourceProvider(provider);
    }

    /** Transform shader source before Veil's processors modify it. */
    @ModifyVariable(method = "modify", at = @At("HEAD"), argsOnly = true, index = 6)
    private static String aw2$transformSource(String source) {
        return AwVeilShaderResourceProvider.ShaderTransformer.process(source);
    }
}
