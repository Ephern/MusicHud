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

    public static HudShaderProgram getOrCreate(
            ResourceLocation vertexShaderLocation,
            ResourceLocation fragmentShaderLocation,
            Map<String, Integer> uniformBlockBindingPoints) {
        String key = cacheKey(vertexShaderLocation, fragmentShaderLocation);
        return programs.computeIfAbsent(key, k -> {
            try {
                HudShaderProgram program = createProgram(vertexShaderLocation, fragmentShaderLocation);
                // Query the number of active uniform blocks to sanity-check
                int numBlocks = glGetProgrami(program.getProgramId(), GL_ACTIVE_UNIFORM_BLOCKS);
                MusicHud.LOGGER.debug("Shader program {} has {} active uniform blocks",
                        program.getProgramId(), numBlocks);

                for (Map.Entry<String, Integer> entry : uniformBlockBindingPoints.entrySet()) {
                    int index = glGetUniformBlockIndex(program.getProgramId(), entry.getKey());
                    if (index != GL_INVALID_INDEX) {
                        glUniformBlockBinding(program.getProgramId(), index, entry.getValue());
                        program.setUniformBlockBindingPoint(entry.getKey(), entry.getValue());
                        MusicHud.LOGGER.debug("  Bound '{}' (index={}) to bp {}",
                                entry.getKey(), index, entry.getValue());
                    } else {
                        MusicHud.LOGGER.warn("  Uniform block '{}' NOT FOUND in program {} (vs={}), data will NOT be uploaded",
                                entry.getKey(), program.getProgramId(), vertexShaderLocation);
                    }
                }
                // Cache uniform locations for built-in matrices (plain uniforms)
                program.cacheUniformLocation("u_MVP");
                program.cacheUniformLocation("ModelViewMat");
                program.cacheUniformLocation("ProjMat");
                // Log the actual locations found
                MusicHud.LOGGER.info("[SHADER DEBUG] program={} u_MVP={} ModelViewMat={} ProjMat={}",
                        program.getProgramId(),
                        program.getUniformLocation("u_MVP"),
                        program.getUniformLocation("ModelViewMat"),
                        program.getUniformLocation("ProjMat"));
                // Cache sampler uniform locations for manual texture binding
                program.cacheSamplerLocation("Sampler0");
                program.cacheSamplerLocation("Sampler1");
                MusicHud.LOGGER.debug("Created shader program {} for {}", program.getProgramId(), vertexShaderLocation);
                return program;
            } catch (Exception e) {
                MusicHud.LOGGER.error("Failed to create shader program for {}", vertexShaderLocation, e);
                return new HudShaderProgram(0); // invalid program, fallback rendering will be used
            }
        });
    }

    private static HudShaderProgram createProgram(ResourceLocation vertexLocation, ResourceLocation fragmentLocation) {
        int programId = glCreateProgram();
        if (programId <= 0) {
            throw new IllegalStateException("Failed to create program");
        }

        // Drain any stale GL errors before we begin
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
            MusicHud.LOGGER.debug("Drained stale GL error 0x{} after creating program {}",
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
        StringBuffer result = new StringBuffer();
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
