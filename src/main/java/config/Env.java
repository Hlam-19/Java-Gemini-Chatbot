package config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Doc cau hinh tu file .env o thu muc goc du an.
 *
 * Thu tu uu tien: bien moi truong he thong > file .env > gia tri mac dinh.
 * Nho vay khi deploy len server chi can set environment variable, khong can file.
 */
public final class Env {

    private static final Map<String, String> VALUES = new HashMap<>();

    static {
        load(".env");
    }

    private Env() {
    }

    private static void load(String fileName) {
        Path path = Path.of(fileName);
        if (!Files.exists(path)) {
            System.err.println("[Env] Khong tim thay file " + fileName
                    + " - se dung bien moi truong he thong hoac gia tri mac dinh.");
            return;
        }
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (String raw : lines) {
                String line = raw.trim();
                // Bo qua dong trong va dong comment
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                // Bo dau nhay bao quanh gia tri neu co: KEY="abc" hoac KEY='abc'
                if (value.length() >= 2
                        && ((value.startsWith("\"") && value.endsWith("\""))
                         || (value.startsWith("'") && value.endsWith("'")))) {
                    value = value.substring(1, value.length() - 1);
                }
                VALUES.put(key, value);
            }
            System.out.println("[Env] Da nap " + VALUES.size() + " bien tu " + fileName);
        } catch (IOException e) {
            System.err.println("[Env] Loi doc file " + fileName + ": " + e.getMessage());
        }
    }

    /** Lay gia tri, tra ve defaultValue neu khong co hoac rong. */
    public static String get(String key, String defaultValue) {
        String fromSystem = System.getenv(key);
        if (fromSystem != null && !fromSystem.isBlank()) {
            return fromSystem;
        }
        String value = VALUES.get(key);
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    /** Lay gia tri bat buoc phai co - thieu thi nem loi ngay khi khoi dong. */
    public static String require(String key) {
        String value = get(key, null);
        if (value == null) {
            throw new IllegalStateException(
                    "Thieu bien '" + key + "'. Hay khai bao trong file .env "
                    + "(tham khao .env.example).");
        }
        return value;
    }

    public static int getInt(String key, int defaultValue) {
        String value = get(key, null);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            System.err.println("[Env] Bien " + key + " khong phai so: '" + value
                    + "' - dung mac dinh " + defaultValue);
            return defaultValue;
        }
    }
}
