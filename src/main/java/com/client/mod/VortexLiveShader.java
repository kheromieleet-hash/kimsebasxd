package com.client.mod;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.backend.opengl.GlTexture;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

public class VortexLiveShader {
    public static final Identifier TEXTURE_ID = Identifier.fromNamespaceAndPath("inventoryhud", "textures/gui/vortex_live.png");
    public static final int TEX_W = 960;
    public static final int TEX_H = 540;

    private static final long START_TIME = System.nanoTime();
    private static boolean initialized = false;
    private static boolean failed = false;
    private static boolean loggedFirstFrame = false;
    private static boolean loggedError = false;

    private static int programId = -1;
    private static int vaoId = -1;
    private static int vboId = -1;
    private static int fboId = -1;

    private static int uResolutionLoc = -1;
    private static int uMouseLoc = -1;
    private static int uTimeLoc = -1;

    private static final String VERTEX_SHADER = 
        "#version 330 core\n" +
        "layout (location = 0) in vec2 aPos;\n" +
        "void main() {\n" +
        "    gl_Position = vec4(aPos, 0.0, 1.0);\n" +
        "}\n";

    private static final String FRAGMENT_SHADER = 
        "#version 330 core\n" +
        "out vec4 fragColor;\n" +
        "uniform vec2 iResolution;\n" +
        "uniform vec2 iMouse;\n" +
        "uniform float iTime;\n" +
        "vec2 rotate2D(vec2 v, float angle) {\n" +
        "    float s = sin(angle);\n" +
        "    float c = cos(angle);\n" +
        "    return mat2(c, -s, s, c) * v;\n" +
        "}\n" +
        "vec3 acesToneMap(vec3 color) {\n" +
        "    const mat3 inputMatrix = mat3(\n" +
        "        0.59719, 0.07600, 0.02840,\n" +
        "        0.35458, 0.90834, 0.13383,\n" +
        "        0.04823, 0.01566, 0.83777\n" +
        "    );\n" +
        "    const mat3 outputMatrix = mat3(\n" +
        "         1.60475, -0.10208, -0.00327,\n" +
        "        -0.53108,  1.10813, -0.07276,\n" +
        "        -0.07367, -0.00605,  1.07602\n" +
        "    );\n" +
        "    vec3 acesColor = inputMatrix * color;\n" +
        "    vec3 numerator   = acesColor * (acesColor + 0.0245786) - 0.000090537;\n" +
        "    vec3 denominator = acesColor * (0.983729 * acesColor + 0.432951) + 0.238081;\n" +
        "    return outputMatrix * (numerator / denominator);\n" +
        "}\n" +
        "float goldenNoise(vec3 p) {\n" +
        "    const float PHI = 1.618033988;\n" +
        "    const mat3 GOLDEN_BASIS = mat3(\n" +
        "        -0.571464913,  0.814921382,  0.096597072,\n" +
        "        -0.278044873, -0.303026659,  0.911518454,\n" +
        "         0.772087367,  0.494042493,  0.399753815\n" +
        "    );\n" +
        "    return dot(cos(GOLDEN_BASIS * p), sin(PHI * p * GOLDEN_BASIS));\n" +
        "}\n" +
        "void main() {\n" +
        "    float time = iTime;\n" +
        "    vec3  rayPos           = vec3(0.0, 0.0, time);\n" +
        "    vec3  accumulatedLight = vec3(0.0);\n" +
        "    vec3  twistedPos;\n" +
        "    const float LOOK_STRENGTH = 0.0;\n" +
        "    vec3 rayDir = normalize(vec3(2.0 * gl_FragCoord.xy - iResolution.xy, iResolution.y));\n" +
        "    fragColor = vec4(0.0, 0.0, 0.0, 1.0);\n" +
        "    const int   MAX_STEPS   = 8;\n" +
        "    const float TWIST_SPEED = 0.5;\n" +
        "    const float TWIST_DEPTH = 3.0;\n" +
        "    for (int i = 0; i < MAX_STEPS; i++) {\n" +
        "        twistedPos    = rayPos;\n" +
        "        twistedPos.xy = rotate2D(sin(twistedPos.xy), time * TWIST_SPEED + twistedPos.z * TWIST_DEPTH);\n" +
        "        float density = 0.001 + abs(goldenNoise(twistedPos * 10.0) / 10.0 - goldenNoise(twistedPos)) * 0.4;\n" +
        "        density = max(density, 2.0 - length(rayPos.xy));\n" +
        "        density += abs(rayPos.y * 0.75 + sin(rayPos.z + time * 0.1 + rayPos.x * 1.5)) * 0.2;\n" +
        "        rayPos += rayDir * density;\n" +
        "        float iterFloat = float(i);\n" +
        "        vec3  tint      = 1.0 + sin(iterFloat + length(rayPos.xy * 0.1) + vec3(1.5));\n" +
        "        accumulatedLight += tint / density;\n" +
        "    }\n" +
        "    vec3 hdrColor = accumulatedLight * accumulatedLight / 600.0;\n" +
        "    fragColor.rgb = acesToneMap(hdrColor);\n" +
        "    const vec3 colorGrade = vec3(1.0, 0.45, 0.45);\n" +
        "    fragColor.rgb *= colorGrade;\n" +
        "}\n";

    private static synchronized void init() {
        if (initialized || failed) return;

        try {
            int vs = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
            GL20.glShaderSource(vs, VERTEX_SHADER);
            GL20.glCompileShader(vs);
            if (GL20.glGetShaderi(vs, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
                System.err.println("[ClientMod] Vertex shader compile failed: " + GL20.glGetShaderInfoLog(vs));
                failed = true;
                return;
            }

            int fs = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
            GL20.glShaderSource(fs, FRAGMENT_SHADER);
            GL20.glCompileShader(fs);
            if (GL20.glGetShaderi(fs, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
                System.err.println("[ClientMod] Fragment shader compile failed: " + GL20.glGetShaderInfoLog(fs));
                failed = true;
                return;
            }

            programId = GL20.glCreateProgram();
            GL20.glAttachShader(programId, vs);
            GL20.glAttachShader(programId, fs);
            GL20.glLinkProgram(programId);
            GL20.glDeleteShader(vs);
            GL20.glDeleteShader(fs);

            if (GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                System.err.println("[ClientMod] Shader program link failed: " + GL20.glGetProgramInfoLog(programId));
                failed = true;
                return;
            }

            uResolutionLoc = GL20.glGetUniformLocation(programId, "iResolution");
            uMouseLoc = GL20.glGetUniformLocation(programId, "iMouse");
            uTimeLoc = GL20.glGetUniformLocation(programId, "iTime");

            float[] quad = {
                -1f, -1f,
                 1f, -1f,
                -1f,  1f,
                -1f,  1f,
                 1f, -1f,
                 1f,  1f
            };
            FloatBuffer fb = ByteBuffer.allocateDirect(quad.length * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
                    .put(quad);
            fb.flip();

            vaoId = GL30.glGenVertexArrays();
            vboId = GL15.glGenBuffers();
            GL30.glBindVertexArray(vaoId);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, fb, GL15.GL_STATIC_DRAW);
            GL20.glEnableVertexAttribArray(0);
            GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 0, 0);
            GL30.glBindVertexArray(0);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

            // Create RenderPearl GpuTexture
            GpuTexture gpuTexture = RenderSystem.getDevice().createTexture(
                "vortex_live",
                5, // FLAG_TEXTURE | FLAG_COLOR_ATTACHMENT
                GpuFormat.RGBA8_UNORM,
                TEX_W,
                TEX_H,
                1,
                1
            );

            if (!(gpuTexture instanceof GlTexture glTex)) {
                System.err.println("[ClientMod] Non-OpenGL GpuDevice detected, live shader disabled.");
                failed = true;
                return;
            }

            int glTextureId = glTex.glId();

            // CRITICAL FIX: Ensure texture completeness in OpenGL core profile
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, glTextureId);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

            GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            GpuTextureView view = RenderSystem.getDevice().createTextureView(gpuTexture);

            VortexTexture textureWrapper = new VortexTexture(gpuTexture, view, sampler);
            Minecraft.getInstance().getTextureManager().register(TEXTURE_ID, textureWrapper);

            // Create OpenGL Framebuffer attached to the GpuTexture's native GL texture ID
            fboId = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboId);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, glTextureId, 0);
            int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);

            if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                System.err.println("[ClientMod] FBO setup failed, status: " + status);
                failed = true;
                return;
            }

            initialized = true;
            System.out.println("[ClientMod] Taunahi Live GLSL Vortex Shader successfully initialized (960x540)!");
        } catch (Throwable t) {
            System.err.println("[ClientMod] Failed to initialize Taunahi Live Vortex Shader: " + t.getMessage());
            t.printStackTrace();
            failed = true;
        }
    }

    public static boolean isAvailable() {
        if (!initialized && !failed) {
            init();
        }
        return initialized && !failed;
    }

    public static void render(int screenW, int screenH) {
        if (!isAvailable()) return;

        int prevFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int prevProg = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int prevVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int[] prevViewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, prevViewport);
        boolean depthEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blendEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cullEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);

        try {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboId);
            GL11.glViewport(0, 0, TEX_W, TEX_H);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glColorMask(true, true, true, true);

            GL20.glUseProgram(programId);
            GL20.glUniform2f(uResolutionLoc, (float) TEX_W, (float) TEX_H);
            GL20.glUniform2f(uMouseLoc, TEX_W / 2.0f, TEX_H / 2.0f);

            // Clean nanoTime calculation (no GLFW dependency, SDL3 compatible)
            float time = (float) ((System.nanoTime() - START_TIME) / 1_000_000_000.0);
            GL20.glUniform1f(uTimeLoc, time);

            GL30.glBindVertexArray(vaoId);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
            GL11.glFlush();

            if (!loggedFirstFrame) {
                ByteBuffer pixel = ByteBuffer.allocateDirect(4);
                GL11.glReadPixels(TEX_W / 2, TEX_H / 2, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
                int r = pixel.get(0) & 0xFF;
                int g = pixel.get(1) & 0xFF;
                int b = pixel.get(2) & 0xFF;
                int a = pixel.get(3) & 0xFF;
                System.out.println("[ClientMod] VortexLiveShader first frame center pixel RGBA: (" + r + "," + g + "," + b + "," + a + ")");
                loggedFirstFrame = true;
            }
        } catch (Throwable t) {
            if (!loggedError) {
                System.err.println("[ClientMod] Exception during VortexLiveShader.render: " + t.getMessage());
                t.printStackTrace();
                loggedError = true;
            }
        } finally {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
            GL20.glUseProgram(prevProg);
            GL30.glBindVertexArray(prevVao);
            GL11.glViewport(prevViewport[0], prevViewport[1], prevViewport[2], prevViewport[3]);
            if (depthEnabled) GL11.glEnable(GL11.GL_DEPTH_TEST); else GL11.glDisable(GL11.GL_DEPTH_TEST);
            if (blendEnabled) GL11.glEnable(GL11.GL_BLEND); else GL11.glDisable(GL11.GL_BLEND);
            if (cullEnabled) GL11.glEnable(GL11.GL_CULL_FACE); else GL11.glDisable(GL11.GL_CULL_FACE);
            if (scissorEnabled) GL11.glEnable(GL11.GL_SCISSOR_TEST); else GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }
}
