package indi.etern.musichud.fabric.render;

import indi.etern.musichud.client.ui.render.pipeline.Std140Sizes;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites the project's OpenGL-flavoured GLSL into Vulkan-compatible GLSL without changing the
 * checked-in shader sources. It only injects the decorations Vulkan requires (version, explicit
 * locations/bindings) and wraps the HUD's plain matrix uniforms into a generated UBO.
 */
final class VulkanShaderRewriter {
    private static final Pattern UNIFORM_DECL = Pattern.compile(
            "^\\s*uniform\\s+([A-Za-z0-9_]+)\\s+([A-Za-z0-9_]+)\\s*;\\s*$");
    private static final Pattern SAMPLER_DECL = Pattern.compile(
            "^\\s*uniform\\s+sampler2D\\s+([A-Za-z0-9_]+)\\s*;\\s*$");
    private static final Pattern IN_OUT_DECL = Pattern.compile(
            "^\\s*(in|out)\\s+([A-Za-z0-9_]+)\\s+([A-Za-z0-9_]+)\\s*;\\s*$");
    private static final Pattern UBO_START = Pattern.compile(
            "^\\s*layout\\(std140\\)\\s+uniform\\s+([A-Za-z0-9_]+)\\s*\\{\\s*$");
    private static final String PLAIN_UBO_NAME = "MHBuiltins";

    private VulkanShaderRewriter() {
    }

    record Ubo(String name, int binding, int size) {
    }

    record Sampler(String name, int binding, int imageIdx) {
    }

    record PlainUbo(String name, int binding, int size, List<Field> fields) {
    }

    record Field(String type, String name) {
    }

    record Result(String vertexSource, String fragmentSource,
                  List<Ubo> ubos, List<Sampler> samplers,
                  @Nullable PlainUbo plainUbo) {
    }

    static Result rewrite(String vertexSource, String fragmentSource,
                          Map<String, Integer> attributes) {
        List<UboDecl> uboDecls = mergeUboDecls(parseUbos(vertexSource), parseUbos(fragmentSource));
        List<Field> plainFields = mergePlainFields(
                parsePlainFields(vertexSource), parsePlainFields(fragmentSource));
        List<String> vsOut = parseVaryings(vertexSource, "out");
        List<String> fsIn = parseVaryings(fragmentSource, "in");
        List<String> fsOut = parseVaryings(fragmentSource, "out");
        Map<String, Integer> varyings = assignLocations(vsOut, fsIn);
        Map<String, Integer> fragmentOutputs = assignLocations(fsOut, List.of());

        Map<String, Integer> uboBindings = new LinkedHashMap<>();
        List<Ubo> ubos = new ArrayList<>();
        int binding = 0;
        for (UboDecl decl : uboDecls) {
            uboBindings.put(decl.name(), binding);
            ubos.add(new Ubo(decl.name(), binding, decl.size()));
            binding++;
        }

        PlainUbo plainUbo = null;
        if (!plainFields.isEmpty()) {
            Std140Sizes sizes = Std140Sizes.calc();
            for (Field field : plainFields) {
                addFieldSize(sizes, field.type());
            }
            plainUbo = new PlainUbo(PLAIN_UBO_NAME, binding, sizes.get(), List.copyOf(plainFields));
            binding++;
        }

        Set<String> samplerNames = new LinkedHashSet<>();
        samplerNames.addAll(parseSamplers(vertexSource));
        samplerNames.addAll(parseSamplers(fragmentSource));
        Map<String, Integer> samplerBindings = new LinkedHashMap<>();
        List<Sampler> samplers = new ArrayList<>();
        for (String name : samplerNames) {
            samplerBindings.put(name, binding);
            samplers.add(new Sampler(name, binding, textureIndex(name)));
            binding++;
        }

        String vertex = rewriteStage(vertexSource, true, attributes, varyings,
                fragmentOutputs, uboBindings, samplerBindings, plainUbo);
        String fragment = rewriteStage(fragmentSource, false, attributes, varyings,
                fragmentOutputs, uboBindings, samplerBindings, plainUbo);
        return new Result(vertex, fragment, ubos, samplers, plainUbo);
    }

    private static String rewriteStage(String source, boolean vertexStage,
                                       Map<String, Integer> attributes,
                                       Map<String, Integer> varyings,
                                       Map<String, Integer> fragmentOutputs,
                                       Map<String, Integer> uboBindings,
                                       Map<String, Integer> samplerBindings,
                                       @Nullable PlainUbo plainUbo) {
        StringBuilder out = new StringBuilder(source.length() + 256);
        String[] lines = source.split("\n", -1);
        int fragmentOutputCursor = 0;
        for (String line : lines) {
            String trimmed = stripComment(line).trim();
            if (trimmed.startsWith("#version")) {
                out.append("#version 450\n");
                appendPlainUbo(out, plainUbo);
                continue;
            }
            if (trimmed.isEmpty()) {
                out.append(line).append('\n');
                continue;
            }
            if (trimmed.startsWith("layout(std140) uniform ")) {
                Matcher m = UBO_START.matcher(trimmed);
                if (m.matches()) {
                    String name = m.group(1);
                    Integer b = uboBindings.get(name);
                    if (b != null) {
                        out.append("layout(std140, binding = ").append(b)
                                .append(") uniform ").append(name).append(" {\n");
                        continue;
                    }
                }
                out.append(line).append('\n');
                continue;
            }
            Matcher sampler = SAMPLER_DECL.matcher(trimmed);
            if (sampler.matches()) {
                String name = sampler.group(1);
                Integer b = samplerBindings.get(name);
                if (b != null) {
                    out.append("layout(binding = ").append(b)
                            .append(") uniform sampler2D ").append(name).append(";\n");
                    continue;
                }
            }
            Matcher uniform = UNIFORM_DECL.matcher(trimmed);
            if (uniform.matches()) {
                // Consumed by the generated plain-uniform block.
                continue;
            }
            Matcher inOut = IN_OUT_DECL.matcher(trimmed);
            if (inOut.matches()) {
                String direction = inOut.group(1);
                String name = inOut.group(3);
                if ("in".equals(direction) && vertexStage) {
                    Integer location = attributes.get(name);
                    out.append(prefixLocation(location, line));
                    continue;
                }
                if ("in".equals(direction)) {
                    out.append(prefixLocation(varyings.get(name), line));
                    continue;
                }
                if (vertexStage) {
                    out.append(prefixLocation(varyings.get(name), line));
                    continue;
                }
                Integer location = fragmentOutputs.get(name);
                if (location == null) {
                    location = fragmentOutputCursor++;
                } else {
                    fragmentOutputCursor = Math.max(fragmentOutputCursor, location + 1);
                }
                out.append(prefixLocation(location, line));
                continue;
            }
            if (trimmed.contains("gl_VertexID")) {
                out.append(line.replace("gl_VertexID", "gl_VertexIndex")).append('\n');
            } else {
                out.append(line).append('\n');
            }
        }
        return out.toString();
    }

    private static String prefixLocation(@Nullable Integer location, String line) {
        if (location == null || line.trim().startsWith("layout")) {
            return line;
        }
        int indent = line.indexOf(line.trim());
        String prefix = line.substring(0, Math.max(0, indent));
        return prefix + "layout(location = " + location + ") " + line.trim();
    }

    private static void appendPlainUbo(StringBuilder out, @Nullable PlainUbo plainUbo) {
        if (plainUbo == null) {
            return;
        }
        out.append("layout(std140, binding = ").append(plainUbo.binding())
                .append(") uniform ").append(plainUbo.name()).append(" {\n");
        for (Field field : plainUbo.fields()) {
            out.append("    ").append(field.type()).append(' ').append(field.name()).append(";\n");
        }
        out.append("};\n");
    }

    private static List<UboDecl> parseUbos(String source) {
        List<UboDecl> result = new ArrayList<>();
        String[] lines = source.split("\n", -1);
        String current = null;
        Std140Sizes sizes = null;
        for (String raw : lines) {
            String line = stripComment(raw).trim();
            Matcher start = UBO_START.matcher(line);
            if (current == null) {
                if (start.matches()) {
                    current = start.group(1);
                    sizes = Std140Sizes.calc();
                }
                continue;
            }
            if (line.startsWith("};")) {
                result.add(new UboDecl(current, sizes.get()));
                current = null;
                sizes = null;
                continue;
            }
            String[] parts = line.split("\\s+");
            if (parts.length >= 2) {
                addFieldSize(sizes, parts[0]);
            }
        }
        return result;
    }

    private static List<Field> parsePlainFields(String source) {
        List<Field> result = new ArrayList<>();
        for (String raw : source.split("\n", -1)) {
            String line = stripComment(raw).trim();
            Matcher m = UNIFORM_DECL.matcher(line);
            if (!m.matches()) {
                continue;
            }
            String type = m.group(1);
            if ("sampler2D".equals(type)) {
                continue;
            }
            result.add(new Field(type, m.group(2)));
        }
        return result;
    }

    private static List<String> parseSamplers(String source) {
        List<String> result = new ArrayList<>();
        for (String raw : source.split("\n", -1)) {
            Matcher m = SAMPLER_DECL.matcher(stripComment(raw).trim());
            if (m.matches()) {
                result.add(m.group(1));
            }
        }
        return result;
    }

    private static List<String> parseVaryings(String source, String direction) {
        List<String> result = new ArrayList<>();
        for (String raw : source.split("\n", -1)) {
            Matcher m = IN_OUT_DECL.matcher(stripComment(raw).trim());
            if (m.matches() && direction.equals(m.group(1))) {
                result.add(m.group(3));
            }
        }
        return result;
    }

    private static Map<String, Integer> assignLocations(List<String> first, List<String> second) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String name : first) {
            result.putIfAbsent(name, result.size());
        }
        for (String name : second) {
            result.putIfAbsent(name, result.size());
        }
        return result;
    }

    private static List<UboDecl> mergeUboDecls(List<UboDecl> first, List<UboDecl> second) {
        Map<String, UboDecl> merged = new LinkedHashMap<>();
        for (UboDecl decl : first) {
            merged.putIfAbsent(decl.name(), decl);
        }
        for (UboDecl decl : second) {
            merged.putIfAbsent(decl.name(), decl);
        }
        return new ArrayList<>(merged.values());
    }

    private static List<Field> mergePlainFields(List<Field> first, List<Field> second) {
        Map<String, Field> merged = new LinkedHashMap<>();
        for (Field field : first) {
            merged.putIfAbsent(field.name(), field);
        }
        for (Field field : second) {
            merged.putIfAbsent(field.name(), field);
        }
        return new ArrayList<>(merged.values());
    }

    private static void addFieldSize(Std140Sizes sizes, String type) {
        switch (type) {
            case "mat4" -> sizes.putMat4f();
            case "vec4" -> sizes.putVec4();
            case "vec3" -> sizes.putVec3();
            case "vec2" -> sizes.putVec2();
            case "float" -> sizes.putFloat();
            case "int" -> sizes.putInt();
            default -> throw new IllegalArgumentException("Unsupported shader uniform type: " + type);
        }
    }

    private static int textureIndex(String name) {
        return switch (name) {
            case "Sampler0" -> 0;
            case "Sampler1" -> 1;
            case "Sampler2" -> 2;
            case "Sampler3" -> 3;
            default -> -1;
        };
    }

    private static String stripComment(String line) {
        int comment = line.indexOf("//");
        return comment >= 0 ? line.substring(0, comment) : line;
    }

    private record UboDecl(String name, int size) {
    }
}
