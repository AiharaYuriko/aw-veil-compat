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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wraps a ResourceProvider to transform vertex shaders for AW compatibility.
 *
 * Mirrors AW's ShaderPreprocessor "vanilla" profile: renames vanilla vertex
 * attributes (Position, Color, UV0, etc.) to AW-prefixed names, declares AW
 * uniform matrices, and injects an aw_main_pre() function that computes the
 * AW-transformed values from the original attributes and AW matrices.
 *
 * This is the Veil equivalent of AW's ShaderIrisMixin + ShaderVanillaMixin.
 */
public class AwVeilShaderResourceProvider implements ResourceProvider {

    private final ResourceProvider delegate;

    public AwVeilShaderResourceProvider(ResourceProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public Optional<Resource> getResource(ResourceLocation location) {
        return delegate.getResource(location).map(r -> transformIfVsh(r, location));
    }

    @Override
    public Resource getResourceOrThrow(ResourceLocation location) {
        try {
            return transformIfVsh(delegate.getResourceOrThrow(location), location);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private Resource transformIfVsh(Resource resource, ResourceLocation location) {
        if (!location.getPath().endsWith(".vsh")) return resource;
        try (Reader reader = resource.openAsReader()) {
            String source = IOUtils.toString(reader);
            String transformed = ShaderTransformer.process(source);
            if (transformed == source || transformed.equals(source)) return resource;
            byte[] bytes = transformed.getBytes(StandardCharsets.UTF_8);
            return new Resource(resource.source(),
                    () -> new ByteArrayInputStream(bytes), resource::metadata);
        } catch (IOException e) {
            return resource;
        }
    }

    /**
     * Self-contained implementation of AW's ShaderPreprocessor vanilla profile.
     *
     * For each vertex attribute (Position, Color, UV0, UV1, UV2, Normal):
     * 1. Rename the original declaration to a temporary name
     * 2. Replace all references in shader body with the AW-prefixed name
     * 3. Declare the corresponding AW uniform matrix
     * 4. Generate aw_main_pre() that computes the AW value from the original
     */
    public static final class ShaderTransformer {

        // Start with minimal transforms. Position is critical (model matrices).
        // Color/texture/normal transforms are AW enhancements — add if needed
        // after confirming basic rendering works.
        private static final AttributeSpec[] SPECS = {
                new AttributeSpec("Position",    "vec3",  "aw_ModelViewMatrix",     "mat4", "vec3($2 * vec4($1, 1))"),
        };

        public static String process(String source) {
            if (source.contains("aw_ModelViewMatrix")) return source; // idempotent

            // Only transform 3D entity/block shaders. GUI and text shaders
            // use screen-space Position without ModelViewMat — skip them.
            if (!source.contains("ModelViewMat")) return source;

            List<String> init1 = new ArrayList<>(); // flag=0: simple copy
            List<String> init2 = new ArrayList<>(); // flag=1: matrix transform
            String result = source;

            for (AttributeSpec spec : SPECS) {
                String tempName = "__aw_" + spec.vanillaName + "_aw__";
                String awName = "aw_" + spec.vanillaName;

                // Step 1: Rename declaration: "in vec3 Position;" → "in vec3 __aw_Position_aw__;"
                String declPattern = "(in\\s+" + Pattern.quote(spec.type) + "\\s+)" +
                        Pattern.quote(spec.vanillaName) + "(\\s*;)";
                String declReplacement = "$1" + tempName + "$2";
                String step1 = result.replaceFirst(declPattern, declReplacement);
                if (step1.equals(result)) continue; // attribute not in this shader
                result = step1;

                // Step 2: Replace uses of vanilla name → awName (everywhere except declaration)
                result = result.replaceAll("\\b" + Pattern.quote(spec.vanillaName) + "\\b", awName);

                // Step 3: Restore original attribute declaration + add AW var + uniform
                // "in vec3 __aw_Position_aw__;" → "uniform mat4 aw_ModelViewMatrix;\nvec3 aw_Position;\nin vec3 Position;"
                String restoreTempDecl = "(in\\s+" + Pattern.quote(spec.type) + "\\s+)" +
                        tempName + "(\\s*;)";
                String restored = "uniform " + spec.matrixType + " " + spec.matrixName + ";\n" +
                        spec.type + " " + awName + ";\n" +
                        "$1" + spec.vanillaName + "$2";
                result = result.replaceFirst(restoreTempDecl, restored);

                // Add initializer lines — use original vanilla name (restored above)
                String expr = spec.expression
                        .replace("$1", spec.vanillaName)
                        .replace("$2", spec.matrixName);
                init1.add(awName + " = " + spec.vanillaName + ";");
                init2.add(awName + " = " + expr + ";");
            }

            if (init1.isEmpty()) return source; // no attributes found — shader not relevant

            // Build aw_main_pre() function
            StringBuilder pre = new StringBuilder();
            pre.append("#ifdef GL_ES\n");
            pre.append("uniform int aw_MatrixFlags;\n");
            pre.append("#else\n");
            pre.append("uniform int aw_MatrixFlags = 0;\n");
            pre.append("#endif\n\n");
            pre.append("void aw_main_pre() {\n");
            pre.append("  if ((aw_MatrixFlags & 0x01) != 0) {\n");
            for (String line : init2) pre.append("    ").append(line).append("\n");
            pre.append("  } else {\n");
            for (String line : init1) pre.append("    ").append(line).append("\n");
            pre.append("  }\n");
            // Normal non-uniform scale normalization
            if (init2.stream().anyMatch(s -> s.contains("aw_Normal"))) {
                pre.append("  if ((aw_MatrixFlags & 0x02) != 0) {\n");
                pre.append("    aw_Normal = normalize(aw_Normal);\n");
                pre.append("  }\n");
            }
            pre.append("}\n");

            // Inject aw_main_pre() call at start of main()
            result = result.replaceFirst("(void\\s+main\\s*\\(\\s*\\)\\s*\\{)(\\s*)",
                    Matcher.quoteReplacement(pre.toString()) + "\n$1$2aw_main_pre();$2$2");

            return result;
        }

        record AttributeSpec(String vanillaName, String type, String matrixName,
                             String matrixType, String expression) {}
    }
}
