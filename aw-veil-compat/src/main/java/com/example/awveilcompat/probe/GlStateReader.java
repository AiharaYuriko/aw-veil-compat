package com.example.awveilcompat.probe;

import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.system.MemoryStack;
import java.nio.IntBuffer;
import static org.lwjgl.opengl.GL20.*;

/**
 * Thread-safe GL state query utilities for probe data collection.
 * All GL read methods MUST be called from the render thread.
 */
public final class GlStateReader {

    private GlStateReader() {}

    /**
     * Checks if the current thread is the render thread.
     */
    public static boolean isOnRenderThread() {
        return RenderSystem.isOnRenderThread();
    }

    /**
     * Reads GL_CURRENT_PROGRAM.
     * Returns 0 if no program is bound.
     * Uses MemoryStack for zero-allocation buffer (stack-allocated, auto-freed).
     */
    public static int readCurrentProgramId() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer buf = stack.mallocInt(1);
            glGetIntegerv(GL_CURRENT_PROGRAM, buf);
            return buf.get(0);
        }
    }

    /**
     * Reads a uniform location on the given program.
     * Returns -1 if the uniform does not exist on this program.
     */
    public static int readUniformLocation(int programId, String uniformName) {
        if (programId == 0) return -1;
        return glGetUniformLocation(programId, uniformName);
    }
}
