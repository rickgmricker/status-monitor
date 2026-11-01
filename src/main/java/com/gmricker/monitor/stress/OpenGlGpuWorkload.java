package com.gmricker.monitor.stress;

import static com.gmricker.monitor.i18n.I18n.tr;

import java.nio.ByteBuffer;
import java.util.Locale;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/** Offscreen shader workload. Native libraries and graphics context load only on demand. */
final class OpenGlGpuWorkload implements GpuStressTest.Workload {
    private static final int SIZE = 1024;
    private static final String VERTEX = """
            #version 330 core
            void main() {
                vec2 point = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                gl_Position = vec4(point * 2.0 - 1.0, 0.0, 1.0);
            }
            """;
    private static final String FRAGMENT = """
            #version 330 core
            uniform float phase;
            out vec4 color;
            void main() {
                vec4 value = vec4(gl_FragCoord.xy * 0.001, phase, 0.781);
                for (int i = 0; i < 128; i++) {
                    value = fract(value * value.wxyz + vec4(0.123, 0.357, 0.531, 0.719) + phase);
                    value = abs(value) * inversesqrt(dot(value, value) + 0.1);
                }
                color = vec4(fract(value.xyz), 1.0);
            }
            """;

    private GLFWErrorCallback errors;
    private String lastError;
    private boolean glfwReady;
    private boolean glReady;
    private long window;
    private int program;
    private int vertexArray;
    private int framebuffer;
    private int texture;
    private int phaseUniform;
    private long frame;
    private String device;

    @Override
    public void initialize() {
        errors = GLFWErrorCallback.create((code, description) -> lastError = GLFWErrorCallback.getDescription(description));
        errors.set();
        if (!glfwInit()) throw new IllegalStateException(tr("Driver gráfico indisponível: ") + lastError);
        glfwReady = true;
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        window = glfwCreateWindow(64, 64, "Monitor GPU stress", NULL, NULL);
        if (window == NULL) throw new IllegalStateException(tr("A GPU ou o driver não oferece OpenGL 3.3: ") + lastError);
        glfwMakeContextCurrent(window);
        GL.createCapabilities();
        glReady = true;
        device = glGetString(GL_RENDERER);
        String renderer = device == null ? "" : device.toLowerCase(Locale.ROOT);
        if (renderer.isBlank() || renderer.contains("llvmpipe") || renderer.contains("softpipe")
                || renderer.contains("gdi generic") || renderer.contains("software rasterizer")) {
            throw new IllegalStateException(tr("O driver ofereceu renderização por software, sem acesso à GPU."));
        }
        int vertex = compile(GL_VERTEX_SHADER, VERTEX);
        int fragment = 0;
        try {
            fragment = compile(GL_FRAGMENT_SHADER, FRAGMENT);
            program = glCreateProgram();
            glAttachShader(program, vertex);
            glAttachShader(program, fragment);
            glLinkProgram(program);
            if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
                throw new IllegalStateException(tr("Falha ao preparar a carga gráfica: ") + glGetProgramInfoLog(program));
            }
        } finally {
            glDeleteShader(vertex);
            if (fragment != 0) glDeleteShader(fragment);
        }
        vertexArray = glGenVertexArrays();
        glBindVertexArray(vertexArray);
        texture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, SIZE, SIZE, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        framebuffer = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException(tr("Não foi possível criar o alvo de renderização na GPU."));
        }
        glViewport(0, 0, SIZE, SIZE);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_BLEND);
        glUseProgram(program);
        phaseUniform = glGetUniformLocation(program, "phase");
    }

    private static int compile(int type, String source) {
        int shader = glCreateShader(type);
        try {
            glShaderSource(shader, source);
            glCompileShader(shader);
            if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
                throw new IllegalStateException(tr("Falha ao preparar o shader: ") + glGetShaderInfoLog(shader));
            }
            return shader;
        } catch (RuntimeException | Error failure) {
            glDeleteShader(shader);
            throw failure;
        }
    }

    @Override
    public void render() {
        glUniform1f(phaseUniform, (frame++ % 10000) * 0.0001f);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        // Complete each bounded batch instead of queuing an unbounded backlog after Stop.
        glFinish();
        glfwPollEvents();
        if (glGetError() != GL_NO_ERROR) throw new IllegalStateException(tr("O driver interrompeu a carga gráfica."));
    }

    @Override
    public String device() { return device; }

    @Override
    public void close() {
        try {
            if (glReady) {
                if (program != 0) glDeleteProgram(program);
                if (vertexArray != 0) glDeleteVertexArrays(vertexArray);
                if (framebuffer != 0) glDeleteFramebuffers(framebuffer);
                if (texture != 0) glDeleteTextures(texture);
            }
        } finally {
            if (glReady) GL.setCapabilities(null);
            if (window != NULL) {
                glfwMakeContextCurrent(NULL);
                glfwDestroyWindow(window);
            }
            if (glfwReady) glfwTerminate();
            if (errors != null) {
                glfwSetErrorCallback(null);
                errors.free();
            }
        }
    }
}
