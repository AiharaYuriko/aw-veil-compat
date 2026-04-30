package com.example.awveilcompat.mixin.veil;

import com.example.awveilcompat.shader.AwVeilShaderResourceProvider;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Wraps the ResourceProvider before it's stored in Veil's ShaderProcessorList.
 *
 * Mirrors AW's ShaderIrisMixin: AW wraps Iris's ExtendedShader ResourceProvider
 * with "new AbstractResourceProvider(arg1, "iris")" to inject AW uniforms.
 *
 * This does the Veil equivalent: wraps the ResourceProvider at the point
 * where Veil's VanillaShaderProcessor creates its ShaderProcessorList.
 * All subsequent vanilla shader loads through this processor get AW uniforms.
 *
 * Target: foundry.veil.impl.client.render.shader.processor.VanillaShaderProcessor
 */
@Pseudo
@Mixin(targets = "foundry.veil.impl.client.render.shader.processor.VanillaShaderProcessor")
public class VeilSetupMixin {

    /**
     * Wraps the ShaderProcessorList constructor call, replacing the
     * ResourceProvider argument with our wrapped version that injects
     * AW uniform declarations into vertex shader source.
     */
    @WrapOperation(
            method = "setup",
            at = @At(
                    value = "NEW",
                    target = "foundry/veil/impl/client/render/shader/processor/ShaderProcessorList;<init>(Lnet/minecraft/server/packs/resources/ResourceProvider;)V",
                    ordinal = 0, remap = false
            )
    )
    private static Object aw2$wrapProcessorList(ResourceProvider provider, Operation<Object> original) {
        // Wrap the provider before ShaderProcessorList stores it in ThreadLocal
        ResourceProvider wrapped = new AwVeilShaderResourceProvider(provider);
        return original.call(wrapped);
    }
}
