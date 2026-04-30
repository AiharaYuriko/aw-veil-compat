package com.example.awveilcompat.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;
import java.nio.FloatBuffer;

public final class AwDedicatedProgram {

    private AwDedicatedProgram() {}

    public static final String VERTEX_SOURCE =
            "#version 150 core\n" +
            "\n" +
            "in vec3  Position;\n" +
            "in vec4  Color;\n" +
            "in vec2  UV0;\n" +
            "in ivec2 UV1;\n" +
            "in ivec2 UV2;\n" +
            "in vec3  Normal;\n" +
            "\n" +
            "uniform mat4 ModelViewMat;\n" +
            "uniform mat4 ProjMat;\n" +
            "uniform vec4 ColorModulator;\n" +
            "\n" +
            "uniform mat4 aw_ModelViewMatrix;\n" +
            "uniform mat4 aw_TextureMatrix;\n" +
            "uniform mat4 aw_OverlayTextureMatrix;\n" +
            "uniform mat4 aw_LightmapTextureMatrix;\n" +
            "uniform mat3 aw_NormalMatrix;\n" +
            "uniform vec4 aw_ColorModulator;\n" +
            "uniform int  aw_MatrixFlags;\n" +
            "\n" +
            "out vec4 vertexColor;\n" +
            "out vec2 texCoord0;\n" +
            "out vec2 texCoord1;\n" +
            "out vec2 texCoord2;\n" +
            "out float vertexDistance;\n" +
            "\n" +
            "void main() {\n" +
            "    vec4 awPos = aw_ModelViewMatrix * vec4(Position, 1.0);\n" +
            "    vec4 viewPos = ModelViewMat * awPos;\n" +
            "    gl_Position = ProjMat * viewPos;\n" +
            "    vertexDistance = length(viewPos.xyz);\n" +
            "    vertexColor = Color * ColorModulator;\n" +
            "    texCoord0 = UV0;\n" +
            "    texCoord1 = UV1;\n" +
            "    texCoord2 = UV2;\n" +
            "}\n";

    public static final String FRAGMENT_SOURCE =
            "#version 150 core\n" +
            "\n" +
            "in vec4 vertexColor;\n" +
            "in vec2 texCoord0;\n" +
            "in vec2 texCoord1;\n" +
            "in vec2 texCoord2;\n" +
            "in float vertexDistance;\n" +
            "\n" +
            "uniform sampler2D Sampler0;\n" +
            "uniform sampler2D Sampler2;\n" +
            "\n" +
            "out vec4 fragColor;\n" +
            "\n" +
            "void main() {\n" +
            "    vec4 color = texture(Sampler0, texCoord0) * vertexColor;\n" +
            "    fragColor = color;\n" +
            "}\n";

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

    public static int create() {
        int vertexShader = compileShader(GL20.GL_VERTEX_SHADER, VERTEX_SOURCE);
        int fragmentShader = compileShader(GL20.GL_FRAGMENT_SHADER, FRAGMENT_SOURCE);

        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vertexShader);
        GL20.glAttachShader(program, fragmentShader);

        // Bind attribute locations (avoids needing layout(location) in GLSL)
        GL20.glBindAttribLocation(program, 0, "Position");
        GL20.glBindAttribLocation(program, 1, "Color");
        GL20.glBindAttribLocation(program, 2, "UV0");
        GL20.glBindAttribLocation(program, 3, "UV1");
        GL20.glBindAttribLocation(program, 4, "UV2");
        GL20.glBindAttribLocation(program, 5, "Normal");

        GL20.glLinkProgram(program);

        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL20.GL_FALSE) {
            String log = GL20.glGetProgramInfoLog(program);
            GL20.glDeleteProgram(program);
            GL20.glDeleteShader(vertexShader);
            GL20.glDeleteShader(fragmentShader);
            throw new RuntimeException("AW dedicated program link failed: " + log);
        }

        GL20.glDetachShader(program, vertexShader);
        GL20.glDetachShader(program, fragmentShader);
        GL20.glDeleteShader(vertexShader);
        GL20.glDeleteShader(fragmentShader);

        return program;
    }

    public static void uploadMatrices(int program) {
        Matrix4f projMat = RenderSystem.getProjectionMatrix();
        Matrix4f modelViewMat = RenderSystem.getModelViewMatrix();
        float[] shaderColor = RenderSystem.getShaderColor();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buf = stack.mallocFloat(16);

            projMat.get(0, buf);
            buf.flip();
            int loc = GL20.glGetUniformLocation(program, "ProjMat");
            if (loc >= 0) GL20.glUniformMatrix4fv(loc, false, buf);

            modelViewMat.get(0, buf);
            buf.flip();
            loc = GL20.glGetUniformLocation(program, "ModelViewMat");
            if (loc >= 0) GL20.glUniformMatrix4fv(loc, false, buf);

            loc = GL20.glGetUniformLocation(program, "ColorModulator");
            if (loc >= 0) GL20.glUniform4f(loc, shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
        }
    }
}
