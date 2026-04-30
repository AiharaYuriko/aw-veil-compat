package com.example.awveilcompat.shader;

import java.util.ArrayList;
import java.util.List;

/**
 * Self-contained, thread-safe, stateless GLSL vertex shader transformer.
 * <p>
 * Injects Armourer's Workshop (AW) uniform declarations, attribute aliases,
 * and the aw_main_pre() function into vertex shader source code, mirroring
 * AW's {@code ShaderPreprocessor} with the "vanilla" profile.
 * <p>
 * This class has zero compile-time or runtime dependency on AW classes,
 * uses no Minecraft singletons, and holds no mutable state. It is safe to
 * call from any thread (including Veil's background shader compilation
 * threads).
 * <p>
 * The transformation is purely additive: original attribute declarations
 * remain unchanged; new uniform declarations and local variables are
 * inserted alongside them. The method is idempotent: already-processed
 * shaders (containing "aw_MatrixFlags") are returned unchanged.
 * <p>
 * <b>Attribute Mappings (Vanilla profile):</b>
 * <pre>
 *   aw_UV0      vec2  UV0       mat4  aw_TextureMatrix
 *   aw_UV1      ivec2 UV1       mat4  aw_OverlayTextureMatrix
 *   aw_UV2      ivec2 UV2       mat4  aw_LightmapTextureMatrix
 *   aw_Color    vec4  Color     vec4  aw_ColorModulator
 *   aw_Normal   vec3  Normal    mat3  aw_NormalMatrix
 *   aw_Position vec3  Position  mat4  aw_ModelViewMatrix
 * </pre>
 * <p>
 * Each attribute is processed via a 3-step register logic:
 * <ol>
 *   <li>Temporarily rename the attribute declaration to a sentinel name
 *       ({@code __aw_VANILLA_aw__})</li>
 *   <li>Rename all body references from the vanilla name to the AW name</li>
 *   <li>Restore the original attribute declaration and add the uniform +
 *       local variable declaration</li>
 * </ol>
 * <p>
 * Supports both GLSL ES ({@code in} qualifier) and GLSL compatibility
 * ({@code attribute} qualifier) syntax.
 */
public final class AwShaderUniformInjector {

    /**
     * Attribute mapping data: [awName, type, vanillaName, matrixType, matrixUniform, expression]
     * Expressions use $1=vanillaName, $2=matrixUniform as placeholders.
     */
    private static final String[][] ATTRIBUTE_MAPPINGS = {
            {"aw_UV0",       "vec2",  "UV0",      "mat4", "aw_TextureMatrix",          "vec2($2 * vec4($1, 1, 1))"},
            {"aw_UV1",       "ivec2", "UV1",      "mat4", "aw_OverlayTextureMatrix",   "ivec2($2 * vec4($1, 1, 1))"},
            {"aw_UV2",       "ivec2", "UV2",      "mat4", "aw_LightmapTextureMatrix",  "ivec2($2 * vec4($1, 1, 1))"},
            {"aw_Color",     "vec4",  "Color",    "vec4", "aw_ColorModulator",         "($2 * $1)"},
            {"aw_Normal",    "vec3",  "Normal",   "mat3", "aw_NormalMatrix",           "($2 * $1)"},
            {"aw_Position",  "vec3",  "Position", "mat4", "aw_ModelViewMatrix",        "vec3($2 * vec4($1, 1))"},
    };

    /** 7 AW uniform declarations injected into every processed vertex shader. */
    private static final String[] UNIFORM_DECLARATIONS = {
            "uniform mat4 aw_ModelViewMatrix;",
            "uniform mat4 aw_TextureMatrix;",
            "uniform mat4 aw_OverlayTextureMatrix;",
            "uniform mat4 aw_LightmapTextureMatrix;",
            "uniform mat3 aw_NormalMatrix;",
            "uniform vec4 aw_ColorModulator;",
            "uniform int aw_MatrixFlags;",
    };

    /** GLSL vertex input qualifiers to try (ES uses "in", compatibility uses "attribute"). */
    private static final String[] QUALIFIERS = {"in", "attribute"};

    private AwShaderUniformInjector() {
        // Utility class: no instantiation
    }

    /**
     * Inject AW uniforms and {@code aw_main_pre()} into the given GLSL vertex
     * shader source.
     * <p>
     * If the source is null or already contains "aw_MatrixFlags", it is
     * returned unchanged (idempotency guard).
     *
     * @param source Raw GLSL vertex shader source
     * @return Transformed source with AW uniforms injected, or the original
     *         if already processed or null
     */
    public static String inject(String source) {
        // Idempotency guard: skip already-processed shaders
        if (source == null || source.contains("aw_MatrixFlags")) {
            return source;
        }

        List<String> awNames = new ArrayList<>();
        List<String> initializersPassthrough = new ArrayList<>();
        List<String> initializersTransform = new ArrayList<>();

        // Process each attribute mapping through the 3-step register logic
        for (String[] mapping : ATTRIBUTE_MAPPINGS) {
            String awName = mapping[0];
            String type = mapping[1];
            String vanillaName = mapping[2];
            String matrixType = mapping[3];
            String matrixUniform = mapping[4];
            String expr = mapping[5];

            String result = tryRegister(source, awName, type, vanillaName, matrixType, matrixUniform, expr);
            if (result != null) {
                source = result;
                awNames.add(awName);
                initializersPassthrough.add(awName + " = " + vanillaName);
                initializersTransform.add(awName + " = " + expr.replace("$1", vanillaName).replace("$2", matrixUniform));
            }
        }

        // If no vanilla attributes were found (e.g., Veil-native pinwheel shaders),
        // inject only the uniform declarations for compatibility
        if (awNames.isEmpty()) {
            return injectUniformsOnly(source);
        }

        // Inject aw_main_pre() function and its call at the start of main()
        return injectAwMainPre(source, awNames, initializersPassthrough, initializersTransform);
    }

    /**
     * Attempt to register a single attribute mapping using the 3-step AW
     * ShaderPreprocessor logic.
     * <p>
     * Tries both {@code in} and {@code attribute} qualifiers. Returns the
     * transformed source if the attribute was found, or null if not found
     * with any qualifier.
     */
    private static String tryRegister(String source, String awName, String type, String vanillaName,
                                       String matrixType, String matrixUniform, String expr) {
        for (String qualifier : QUALIFIERS) {
            String result = registerAttribute(source, awName, type, vanillaName,
                    matrixType, matrixUniform, qualifier);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    /**
     * AW 3-step register logic for a single attribute.
     * <p>
     * Step 1: Temporarily renames the attribute declaration to
     *         {@code __aw_VANILLA_aw__}.
     * Step 2: Renames all shader-body references of the vanilla name
     *         to the AW-prefixed name.
     * Step 3: Restores the original attribute declaration and inserts
     *         the uniform declaration + local variable.
     *
     * @return Transformed source, or null if the attribute was not found
     *         with this qualifier
     */
    private static String registerAttribute(String source, String awName, String type, String vanillaName,
                                             String matrixType, String matrixUniform, String qualifier) {
        // Step 1: Find the attribute declaration and temporarily rename it
        // Pattern: (qualifier\s+type\s+)(\bvanillaName\b)(.*?;)
        // Capture groups: $1 = qualifier+type+space, $2 = vanilla name, $3 = rest of declaration
        String step1Pattern = "(" + qualifier + "\\s+" + type + "\\s+)(\\b" + vanillaName + "\\b)(.*?;)";
        String step1Replacement = "$1__aw_" + vanillaName + "_aw__$3";
        String afterStep1 = source.replaceAll(step1Pattern, step1Replacement);

        // If nothing changed, the attribute was not found with this qualifier
        if (afterStep1.equals(source)) {
            return null;
        }

        // Step 2: Rename all vanillaName references in the body to awName
        // This affects the renamed attribute (now __aw_VANILLA_aw__ which is not
        // \bVANILLA\b match) and all body references.
        String step2Pattern = "\\b" + vanillaName + "\\b";
        String step2Replacement = awName;
        String afterStep2 = afterStep1.replaceAll(step2Pattern, step2Replacement);

        // Step 3: Restore the original attribute declaration and add uniform + local variable
        // Pattern: (qualifier\s+type\s+)(\b__aw_VANILLA_aw__\b)(.*?;)
        // Replacement: uniform matrixType matrixUniform;\ntype awName;\n$1vanillaName$3
        String step3Pattern = "(" + qualifier + "\\s+" + type + "\\s+)(\\b__aw_" + vanillaName + "_aw__\\b)(.*?;)";
        String step3Replacement = "uniform " + matrixType + " " + matrixUniform + ";\n"
                + type + " " + awName + ";\n$1" + vanillaName + "$3";
        return afterStep2.replaceAll(step3Pattern, step3Replacement);
    }

    /**
     * Inject only the 7 uniform declarations (no attribute renaming, no
     * aw_main_pre()). Used as a fallback when no vanilla attributes are
     * found in the shader source (e.g., Veil-native pinwheel format shaders).
     * <p>
     * Uniforms are injected after the {@code #version} directive if present,
     * otherwise at the top of the file.
     */
    private static String injectUniformsOnly(String source) {
        // Look for the #version directive
        int versionIdx = source.indexOf("#version");
        if (versionIdx >= 0) {
            // Find end of the #version line
            int eol = source.indexOf('\n', versionIdx);
            if (eol < 0) {
                eol = source.length();
            }
            // Build: everything before #version + #version line + uniforms + rest
            StringBuilder sb = new StringBuilder(source.length() + 200);
            sb.append(source, 0, eol + 1); // Include the #version line and its newline
            for (String uniform : UNIFORM_DECLARATIONS) {
                sb.append(uniform).append('\n');
            }
            sb.append(source.substring(eol + 1));
            return sb.toString();
        }

        // No #version line: prepend uniforms at the top of the file
        StringBuilder sb = new StringBuilder(source.length() + 200);
        for (String uniform : UNIFORM_DECLARATIONS) {
            sb.append(uniform).append('\n');
        }
        sb.append(source);
        return sb.toString();
    }

    /**
     * Generate the {@code aw_main_pre()} function and inject it before
     * {@code main()}, then insert {@code aw_main_pre();} as the first
     * statement in {@code main()}.
     * <p>
     * The aw_main_pre function conditionally applies matrix transforms
     * based on {@code aw_MatrixFlags}. When flag 0x01 is set, transforms
     * are applied; otherwise values pass through unchanged. When flag
     * 0x02 is also set, the normal vector is normalized (non-uniform
     * scaling correction).
     */
    private static String injectAwMainPre(String source, List<String> awNames,
                                           List<String> initPassthrough, List<String> initTransform) {
        // Build the aw_main_pre function body
        StringBuilder preBuilder = new StringBuilder();
        preBuilder.append("#ifdef GL_ES\n");
        preBuilder.append("uniform int aw_MatrixFlags;\n");
        preBuilder.append("#else\n");
        preBuilder.append("uniform int aw_MatrixFlags = 0;\n");
        preBuilder.append("#endif\n");
        preBuilder.append("\n");
        preBuilder.append("void aw_main_pre() {\n");
        preBuilder.append("  if ((aw_MatrixFlags & 0x01) != 0) {\n");

        // Transform branch: apply matrix transforms
        for (String s : initTransform) {
            preBuilder.append("    ").append(s).append(";\n");
        }

        // Normal normalization for non-uniform scaling (flag 0x02)
        if (awNames.contains("aw_Normal")) {
            preBuilder.append("    if ((aw_MatrixFlags & 0x02) != 0) {\n");
            preBuilder.append("      aw_Normal = normalize(aw_Normal);\n");
            preBuilder.append("    }\n");
        }

        preBuilder.append("  } else {\n");

        // Passthrough branch: direct assignment without transforms
        for (String s : initPassthrough) {
            preBuilder.append("    ").append(s).append(";\n");
        }

        preBuilder.append("  }\n");
        preBuilder.append("}\n");

        String awMainPre = preBuilder.toString();

        // Inject aw_main_pre before main() and call it at the start of main()
        // Pattern matches: (void main() {)(whitespace)
        // Replacement: aw_main_pre_body + \n + $1 + $2 + aw_main_pre(); + $2 + $2
        return source.replaceAll(
                "(void\\s+main\\s*\\(\\)\\s*\\{)(\\s*)",
                awMainPre + "\n$1$2aw_main_pre();$2$2"
        );
    }
}
