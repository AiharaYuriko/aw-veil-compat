package com.example.awveilcompat.shader;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Wraps a ResourceProvider to inject AW uniform declarations into .vsh files.
 *
 * Mirrors exactly what AW's ShaderIrisMixin does for Iris: intercept the
 * ResourceProvider at shader setup time so that Veil's shader compilation
 * uses our wrapped provider that injects AW uniforms.
 *
 * This is the Veil equivalent of AW wrapping "new AbstractResourceProvider(arg1, "iris")"
 * for Iris shaders.
 */
public class AwVeilShaderResourceProvider implements ResourceProvider {

    private final ResourceProvider delegate;

    public AwVeilShaderResourceProvider(ResourceProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public Optional<Resource> getResource(ResourceLocation location) {
        return delegate.getResource(location).map(resource -> {
            if (location.getPath().endsWith(".vsh")) {
                return transformResource(resource, location);
            }
            return resource;
        });
    }

    @Override
    public Resource getResourceOrThrow(ResourceLocation location) {
        try {
            Resource resource = delegate.getResourceOrThrow(location);
            if (location.getPath().endsWith(".vsh")) {
                return transformResource(resource, location);
            }
            return resource;
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private Resource transformResource(Resource resource, ResourceLocation location) {
        try (Reader reader = resource.openAsReader()) {
            String source = IOUtils.toString(reader);

            // Apply AW uniform injection
            String transformed = injectAwUniforms(source);

            // Return original if unchanged (idempotent guard)
            if (transformed == source || transformed.equals(source)) {
                return resource;
            }

            byte[] bytes = transformed.getBytes(StandardCharsets.UTF_8);
            return new Resource(
                    resource.source(),
                    () -> new ByteArrayInputStream(bytes),
                    resource::metadata
            );
        } catch (IOException e) {
            return resource; // pass through on error
        }
    }

    // AW uniform declarations injected into vertex shader source
    private static final String AW_UNIFORMS =
            "uniform mat4 aw_ModelViewMatrix;\n" +
            "uniform mat4 aw_TextureMatrix;\n" +
            "uniform mat4 aw_OverlayTextureMatrix;\n" +
            "uniform mat4 aw_LightmapTextureMatrix;\n" +
            "uniform mat3 aw_NormalMatrix;\n" +
            "uniform vec4 aw_ColorModulator;\n" +
            "uniform int  aw_MatrixFlags;\n";

    static String injectAwUniforms(String source) {
        // Idempotent: skip if already injected
        if (source.contains("aw_ModelViewMatrix")) {
            return source;
        }
        // Inject after #version directive or at beginning
        int versionEnd = source.indexOf('\n');
        if (versionEnd > 0 && source.substring(0, versionEnd).contains("#version")) {
            return source.substring(0, versionEnd + 1) + AW_UNIFORMS + source.substring(versionEnd + 1);
        }
        return AW_UNIFORMS + source;
    }
}
