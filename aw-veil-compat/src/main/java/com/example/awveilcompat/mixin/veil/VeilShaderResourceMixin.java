package com.example.awveilcompat.mixin.veil;

import com.example.awveilcompat.shader.AwShaderUniformInjector;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.apache.commons.io.IOUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * Mixin that intercepts {@code ResourceProvider.getResourceOrThrow()} inside
 * Veil's {@code ShaderManager.readShader()} to inject AW uniforms into
 * vertex shader source before Veil's GLSL parser processes it.
 * <p>
 * Uses {@code @WrapOperation} (MixinExtras) instead of {@code @Redirect}
 * to allow composition with other mods that may wrap the same call.
 * <p>
 * Only transforms {@code .vsh} files. Fragment shaders ({@code .fsh}) pass
 * through unchanged since AW uniforms are only relevant in the vertex stage.
 * <p>
 * The transformation is applied via {@link AwShaderUniformInjector#inject(String)},
 * which is idempotent and thread-safe. If the injector returns the same
 * string (no transformation needed), the original Resource is returned to
 * avoid unnecessary object allocation.
 * <p>
 * <b>Safety layers:</b>
 * <ol>
 *   <li>{@code @Pseudo} — avoids classloading Veil classes if absent</li>
 *   <li>Mixin config has {@code "defaultRequire": 0} — optional injection</li>
 *   <li>MixinPlugin gates {@code .mixin.veil.} paths to Veil-loaded only</li>
 *   <li>Fast path: non-{@code .vsh} files pass through immediately</li>
 * </ol>
 */
@Pseudo
@Mixin(targets = "foundry.veil.api.client.render.shader.ShaderManager")
public class VeilShaderResourceMixin {

    /**
     * Wraps the {@code ResourceProvider.getResourceOrThrow(ResourceLocation)}
     * call inside {@code ShaderManager.readShader()}.
     * <p>
     * For vertex shader ({@code .vsh}) files, reads the source, applies
     * {@link AwShaderUniformInjector#inject(String)}, and returns a new
     * Resource wrapping the transformed source. For all other files, returns
     * the original resource unchanged.
     *
     * @param provider The ResourceProvider from ShaderManager.readShader()
     * @param location The ResourceLocation of the shader file being loaded
     * @param original The original operation to call
     * @return The (possibly transformed) Resource
     * @throws IOException if reading the source fails
     */
    @WrapOperation(
            method = "readShader",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/packs/resources/ResourceProvider;getResourceOrThrow(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/server/packs/resources/Resource;"
            ),
            remap = false
    )
    private Resource aw2$wrapShaderSource(ResourceProvider provider, ResourceLocation location,
                                           Operation<Resource> original) throws IOException {
        // Fast path: only process vertex shaders
        if (!location.getPath().endsWith(".vsh")) {
            return original.call(provider, location);
        }

        // Get the original resource
        Resource resource = original.call(provider, location);

        // Read the original shader source
        String originalSource;
        try (Reader reader = resource.openAsReader()) {
            originalSource = IOUtils.toString(reader);
        }

        // Transform through our injector (idempotent — already-processed
        // shaders return unchanged)
        String transformedSource = AwShaderUniformInjector.inject(originalSource);

        // If nothing changed, return original resource to avoid unnecessary
        // object allocation
        if (transformedSource == originalSource || transformedSource.equals(originalSource)) {
            return resource;
        }

        // Return a new Resource wrapping the transformed source
        byte[] transformedBytes = transformedSource.getBytes(StandardCharsets.UTF_8);
        ResourceMetadata meta = resource.metadata();
        return new Resource(
                resource.source(),
                () -> new ByteArrayInputStream(transformedBytes),
                () -> meta
        );
    }
}
