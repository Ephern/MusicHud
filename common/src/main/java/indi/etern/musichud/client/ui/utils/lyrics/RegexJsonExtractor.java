package indi.etern.musichud.client.ui.utils.lyrics;

import indi.etern.musichud.utils.JsonUtil;

import java.util.ArrayList;
import java.util.List;

public class RegexJsonExtractor {
    public static <T> List<T> extractJsonObjectsSafely(String input, Class<T> valueType) {
        List<T> results = new ArrayList<>();
        List<String> jsonStrings = new ArrayList<>();

        int start = -1;
        int braceCount = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (escaped) {
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                inString = !inString;
            } else if (!inString) {
                if (c == '{') {
                    if (braceCount == 0) {
                        start = i; // 记录开始位置
                    }
                    braceCount++;
                } else if (c == '}') {
                    braceCount--;
                    if (braceCount == 0 && start != -1) {
                        // 找到完整的JSON对象
                        String jsonStr = input.substring(start, i + 1);
                        jsonStrings.add(jsonStr);
                        start = -1; // 重置
                    }
                }
            }
        }

        for (String jsonStr : jsonStrings) {
            try {
                T obj = JsonUtil.gson.fromJson(jsonStr, valueType);
                results.add(obj);
            } catch (RuntimeException e) {
                System.err.println("Failed to parse JSON: " + e.getMessage());
            }
        }

        return results;
    }
}