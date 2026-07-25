package indi.etern.musichud.client.ui.hud.pipelines;

import indi.etern.musichud.MusicHud;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL31.*;

public class HudShaderManager {
    private static final Map<String, HudShaderProgram> programs = new HashMap<>();

    private static String cacheKey(ResourceLocation vs, ResourceLocation fs) {
        return vs + "|" + fs;
    }
    private static final Pattern MOJ_IMPORT = Pattern.compile("#moj_import\\s+<([^>]+)>");
    private static int bindingPoint;
    private static final Map<String, Integer> boundPoints = new HashMap<>();

    public static synchronized int getOrCreateBindingPoint(String name) {
        return boundPoints.computeIfAbsent(name, k -> bindingPoint++);
    }

    public static Integer getBindingPoint(String name) {
        return boundPoints.get(name);
    }

    public static HudShaderProgram getOrCreate(
            ResourceLocation vertexShaderLocation,
            ResourceLocation fragmentShaderLocation,
            List<String> UBONames) {
        String key = cacheKey(vertexShaderLocation, fragmentShaderLocation);
        return programs.computeIfAbsent(key, k -> {
            try {
                HudShaderProgram program = createProgram(vertexShaderLocation, fragmentShaderLocation);

                for (String uboName : UBONames) {
                    int index = glGetUniformBlockIndex(program.getProgramId(), uboName);
                    if (index != GL_INVALID_INDEX) {
                        int bindingPoint = getOrCreateBindingPoint(uboName);
                        glUniformBlockBinding(program.getProgramId(), index, bindingPoint);
                    }
                }
                // Pre-cache uniform locations for built-in matrices (plain uniforms)
                int modelViewMatLoc = program.getUniformOrSamplerLocation("ModelViewMat");
                int projMatLoc = program.getUniformOrSamplerLocation("ProjMat");
                if (MusicHud.LOGGER.isDebugEnabled()) {
                    MusicHud.LOGGER.debug("Program {}: ModelViewMat={} ProjMat={}",
                            program.getProgramId(),
                            modelViewMatLoc,
                            projMatLoc);
                }
                return program;
            } catch (Exception e) {
                return new HudShaderProgram(0); // invalid program, fallback rendering will be used
            }
        });
    }

    private static HudShaderProgram createProgram(ResourceLocation vertexLocation, ResourceLocation fragmentLocation) {
        int programId = glCreateProgram();
        if (programId <= 0) {
            throw new IllegalStateException("Failed to create program");
        }

        //noinspection StatementWithEmptyBody
        while (glGetError() != GL_NO_ERROR);

        String vertexSource = readShaderSourceWithImports(vertexLocation);
        String fragmentSource = readShaderSourceWithImports(fragmentLocation);

        int vs = compileShader(GL_VERTEX_SHADER, vertexSource);
        int fs = compileShader(GL_FRAGMENT_SHADER, fragmentSource);

        glAttachShader(programId, vs);
        glAttachShader(programId, fs);

        glBindAttribLocation(programId, 0, "Position");
        glBindAttribLocation(programId, 1, "Color");

        glLinkProgram(programId);

        if (glGetProgrami(programId, GL_LINK_STATUS) == GL_FALSE) {
            String log = glGetProgramInfoLog(programId);
            glDeleteProgram(programId);
            throw new IllegalStateException("Shader link error: " + log);
        }

        glDetachShader(programId, vs);
        glDetachShader(programId, fs);
        glDeleteShader(vs);
        glDeleteShader(fs);

        // Drain any GL errors from shader compilation/linking
        // so they don't pollute subsequent rendering error checks
        int err;
        while ((err = glGetError()) != GL_NO_ERROR) {
            MusicHud.LOGGER.error("Drained stale GL error 0x{} after creating program {}",
                    Integer.toHexString(err), programId);
        }

        return new HudShaderProgram(programId);
    }

    private static int compileShader(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException("Shader compile error: " + log + "\nSource: " + source);
        }
        return shader;
    }

    private static String readShaderSourceWithImports(ResourceLocation location) {
        String source = readRawResource(location);
        StringBuilder result = new StringBuilder();
        Matcher matcher = MOJ_IMPORT.matcher(source);
        while (matcher.find()) {
            String importRef = matcher.group(1);
            String importContent = resolveImport(importRef);
            matcher.appendReplacement(result, Matcher.quoteReplacement(importContent));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String resolveImport(String importRef) {
        // importRef format: "namespace:path" e.g. "minecraft:dynamictransforms.glsl"
        // In 1.21.1, moj_import files may not be accessible via ResourceManager.
        // Use hardcoded definitions for known standard imports.
        if ("minecraft:dynamictransforms.glsl".equals(importRef)) {
            return "uniform mat4 ModelViewMat;";
        }
        if ("minecraft:projection.glsl".equals(importRef)) {
            return "uniform mat4 ProjMat;";
        }
        String[] parts = importRef.split(":", 2);
        String namespace = parts[0];
        String path = parts[1];
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(namespace, "shaders/include/" + path);
        return readRawResource(location);
    }

    private static String readRawResource(ResourceLocation location) {
        ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
        Optional<Resource> resource = resourceManager.getResource(location);
        if (resource.isEmpty()) {
            throw new IllegalStateException("Resource not found: " + location);
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            throw new RuntimeException("Failed to read resource: " + location, e);
        }
    }
}
