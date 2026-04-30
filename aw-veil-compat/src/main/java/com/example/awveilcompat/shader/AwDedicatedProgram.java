package com.example.awveilcompat.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;
import java.nio.FloatBuffer;

/**
 * Creates and manages AW's dedicated GLSL shader program for VBO rendering.
 * <p>
 * AW's VBO rendering fails when the currently-bound GL program (e.g. Veil's)
 * does not declare AW's 7 custom uniforms -- {@code glGetUniformLocation}
 * returns -1 and uniforms are silently dropped. This class provides a minimal
 * GLSL 150 core shader program that explicitly declares all 7 AW uniforms,
 * allowing AW's {@code ShaderUniforms.end()} to find valid uniform locations.
 * <p>
 * The program is a pass-through entity solid shader: it transforms vertices
 * through AW's model matrix then the standard view/projection matrices,
 * samples {@code Sampler0} for texture, and outputs the modulated color.
 * <p>
 * <b>Thread safety:</b> All methods must be called on the render thread with
 * an active GL context. This class holds no mutable static state beyond the
 * GL program ID created by {@link #create()}, which is managed externally.
 * <p>
 * <b>Zero AW dependency:</b> No imports from
 * {@code moe.plushie.armourers_workshop} package. This class is fully
 * self-contained.
 */
public final class AwDedicatedProgram {

    private AwDedicatedProgram() {
        // Utility class: no instantiation
    }

    /**
     * GLSL 150 core vertex shader source.
     * <p>
     * Declares all 6 standard entity vertex attributes (layout locations 0-5
     * matching {@code DefaultVertexFormat.NEW_ENTITY}) plus vanilla uniforms
     * {@code ModelViewMat}, {@code ProjMat}, {@code ColorModulator}, and all
     * 7 AW uniforms. Transforms each vertex through AW's model-view matrix
     * before the standard view-projection pipeline.
     */
    public static final String VERTEX_SOURCE = """
            #version 150 core

            layout(location = 0) in vec3  Position;
            layout(location = 1) in vec4  Color;
            layout(location = 2) in vec2  UV0;
            layout(location = 3) in ivec2 UV1;
            layout(location = 4) in ivec2 UV2;
            layout(location = 5) in vec3  Normal;

            uniform mat4 ModelViewMat;
            uniform mat4 ProjMat;
            uniform vec4 ColorModulator;

            uniform mat4 aw_ModelViewMatrix;
            uniform mat4 aw_TextureMatrix;
            uniform mat4 aw_OverlayTextureMatrix;
            uniform mat4 aw_LightmapTextureMatrix;
            uniform mat3 aw_NormalMatrix;
            uniform vec4 aw_ColorModulator;
            uniform int  aw_MatrixFlags;

            out vec4 vertexColor;
            out vec2 texCoord0;
            out vec2 texCoord1;
            out vec2 texCoord2;
            out float vertexDistance;

            void main() {
                vec4 awPos = aw_ModelViewMatrix * vec4(Position, 1.0);
                vec4 viewPos = ModelViewMat * awPos;
                gl_Position = ProjMat * viewPos;
                vertexDistance = length(viewPos.xyz);
                vertexColor = Color * ColorModulator;
                texCoord0 = UV0;
                texCoord1 = UV1;
                texCoord2 = UV2;
            }
            """;

    /**
     * GLSL 150 core fragment shader source.
     * <p>
     * Samples {@code Sampler0} texture with the interpolated UV0 coordinate,
     * multiplies by the interpolated vertex color, discards fragments with
     * alpha below 0.1, and outputs the final color.
     */
    public static final String FRAGMENT_SOURCE = """
            #version 150 core

            in vec4 vertexColor;
            in vec2 texCoord0;
            in vec2 texCoord1;
            in vec2 texCoord2;
            in float vertexDistance;

            uniform sampler2D Sampler0;

            out vec4 fragColor;

            void main() {
                vec4 color = texture(Sampler0, texCoord0) * vertexColor;
                if (color.a < 0.1) discard;
                fragColor = color;
            }
            """;

    /**
     * Compile a GLSL shader of the given type from the given source.
     * <p>
     * Creates the shader object, sets the source, compiles, and checks the
     * compile status. On failure, retrieves the info log, deletes the shader,
     * and throws a {@link RuntimeException} with the log message.
     *
     * @param type   GL shader type constant (e.g. {@code GL20.GL_VERTEX_SHADER})
     * @param source GLSL source string
     * @return the GL shader object ID
     * @throws RuntimeException if compilation fails
     */
    private static int compileShader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL20.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new RuntimeException("Shader compile failed (type=" + type + "): " + log);
        }
        return shader;
    }

    /**
     * Create the dedicated AW shader program.
     * <p>
     * Compiles the vertex and fragment shaders from the embedded GLSL sources,
     * creates a GL program object, attaches both shaders, links the program,
     * and performs cleanup (detach and delete shader objects).
     * <p>
     * Call this method once on the render thread when a GL context is active.
     * The returned program ID can be reused for the entire session (the
     * program is stateless -- uniforms must be uploaded each frame via
     * {@link #uploadMatrices(int)}).
     *
     * @return the GL program object ID
     * @throws RuntimeException if compilation or linking fails
     */
    public static int create() {
        int vertexShader = compileShader(GL20.GL_VERTEX_SHADER, VERTEX_SOURCE);
        int fragmentShader = compileShader(GL20.GL_FRAGMENT_SHADER, FRAGMENT_SOURCE);

        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vertexShader);
        GL20.glAttachShader(program, fragmentShader);
        GL20.glLinkProgram(program);

        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL20.GL_FALSE) {
            String log = GL20.glGetProgramInfoLog(program);
            GL20.glDeleteProgram(program);
            GL20.glDeleteShader(vertexShader);
            GL20.glDeleteShader(fragmentShader);
            throw new RuntimeException("AW dedicated program link failed: " + log);
        }

        // Detach and delete shader objects (no longer needed after linking)
        GL20.glDetachShader(program, vertexShader);
        GL20.glDetachShader(program, fragmentShader);
        GL20.glDeleteShader(vertexShader);
        GL20.glDeleteShader(fragmentShader);

        return program;
    }

    /**
     * Upload current rendering matrices from Minecraft's RenderSystem to the
     * given dedicated AW program.
     * <p>
     * Captures {@code ProjMat}, {@code ModelViewMat}, and {@code ColorModulator}
     * from {@link RenderSystem} and uploads them to the program's corresponding
     * uniform locations using LWJGL's {@link MemoryStack} for zero-allocation
     * float buffers.
     * <p>
     * Must be called with the given program bound via {@code GL20.glUseProgram()}
     * and on the render thread. This method is lightweight (no GL pipeline
     * stalls -- matrices come from the RenderSystem cache).
     *
     * @param program the GL program object ID to upload matrices to
     */
    public static void uploadMatrices(int program) {
        Matrix4f projMat = RenderSystem.getProjectionMatrix();
        Matrix4f modelViewMat = RenderSystem.getModelViewMatrix();
        float[] shaderColor = RenderSystem.getShaderColor();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buf = stack.mallocFloat(16);

            // Upload ProjMat
            projMat.get(0, buf);
            buf.flip();
            int loc = GL20.glGetUniformLocation(program, "ProjMat");
            if (loc >= 0) {
                GL20.glUniformMatrix4fv(loc, false, buf);
            }

            // Upload ModelViewMat
            modelViewMat.get(0, buf);
            buf.flip();
            loc = GL20.glGetUniformLocation(program, "ModelViewMat");
            if (loc >= 0) {
                GL20.glUniformMatrix4fv(loc, false, buf);
            }

            // Upload ColorModulator
            loc = GL20.glGetUniformLocation(program, "ColorModulator");
            if (loc >= 0) {
                GL20.glUniform4f(loc, shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
            }
        }
    }
}
