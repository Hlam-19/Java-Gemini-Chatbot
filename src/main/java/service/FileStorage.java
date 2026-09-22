package service;

import config.Env;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Luu file nguoi dung tai len vao thu muc tren dia.
 *
 * Co so quy tac an toan quan trong:
 *   1. Ten file luu tren dia do he thong sinh ra (UUID), khong dung ten goc -
 *      nguoi dung khong the dat ten kieu "../../etc/passwd" hay ghi de file khac.
 *   2. Chi chap nhan cac duoi file trong danh sach cho phep.
 *   3. Gioi han dung luong.
 * Ten goc van duoc luu trong database de hien thi lai cho dung.
 */
public final class FileStorage {

    /** Thu muc goc chua file tai len. */
    private static final Path ROOT = Path.of(Env.get("UPLOAD_DIR", "uploads"));

    /** Dung luong toi da moi file, mac dinh 10MB. */
    private static final long MAX_SIZE = Env.getInt("UPLOAD_MAX_MB", 10) * 1024L * 1024L;

    /** Duoi file duoc phep, kem kieu MIME tuong ung de gui cho Gemini. */
    private static final Map<String, String> ALLOWED = Map.ofEntries(
            // Anh - Gemini doc duoc truc tiep
            Map.entry("png",  "image/png"),
            Map.entry("jpg",  "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("webp", "image/webp"),
            Map.entry("gif",  "image/gif"),
            // Tai lieu
            Map.entry("pdf",  "application/pdf"),
            // Van ban va ma nguon
            Map.entry("txt",  "text/plain"),
            Map.entry("md",   "text/plain"),
            Map.entry("csv",  "text/plain"),
            Map.entry("json", "text/plain"),
            Map.entry("xml",  "text/plain"),
            Map.entry("java", "text/plain"),
            Map.entry("js",   "text/plain"),
            Map.entry("py",   "text/plain"),
            Map.entry("sql",  "text/plain"),
            Map.entry("html", "text/plain"),
            Map.entry("css",  "text/plain")
    );

    /** Cac kieu ma Gemini nhan duoi dang du lieu nhi phan (inline data). */
    private static final Set<String> BINARY_FOR_GEMINI =
            Set.of("image/png", "image/jpeg", "image/webp", "image/gif", "application/pdf");

    private FileStorage() {
    }

    /** Ket qua sau khi luu mot file. */
    public record Stored(String storedName, String originalName, String mimeType, long size) {

        /** Duong dan tuong doi de luu vao database va tra ve cho trinh duyet. */
        public String path() {
            return "uploads/" + storedName;
        }

        /** Gemini co doc truc tiep file nay khong (anh, PDF). */
        public boolean isBinaryForGemini() {
            return BINARY_FOR_GEMINI.contains(mimeType);
        }

        public boolean isImage() {
            return mimeType.startsWith("image/");
        }
    }

    /** Duoi file nay co duoc phep khong. */
    public static boolean isAllowed(String fileName) {
        return ALLOWED.containsKey(extensionOf(fileName));
    }

    public static String mimeTypeOf(String fileName) {
        return ALLOWED.getOrDefault(extensionOf(fileName), "application/octet-stream");
    }

    public static long maxSize() {
        return MAX_SIZE;
    }

    /** Danh sach duoi file cho phep, de hien thi trong thong bao loi. */
    public static String allowedList() {
        return String.join(", ", ALLOWED.keySet().stream().sorted().toList());
    }

    /**
     * Luu noi dung file xuong dia.
     *
     * @param originalName ten goc do trinh duyet gui len (chi dung de hien thi)
     * @param data         noi dung file
     * @throws IOException khi khong ghi duoc, hoac file khong hop le
     */
    public static Stored save(String originalName, byte[] data) throws IOException {
        if (data == null || data.length == 0) {
            throw new IOException("File rong");
        }
        if (data.length > MAX_SIZE) {
            throw new IOException("File qua lon (toi da " + (MAX_SIZE / 1024 / 1024) + "MB)");
        }

        String ext = extensionOf(originalName);
        if (!ALLOWED.containsKey(ext)) {
            throw new IOException("Dinh dang khong duoc ho tro. Chi nhan: " + allowedList());
        }

        Files.createDirectories(ROOT);

        // Ten tren dia do he thong sinh - khong lay tu nguoi dung
        String storedName = UUID.randomUUID() + "." + ext;
        Path target = ROOT.resolve(storedName);

        // Chan chac lan nua: duong dan cuoi cung phai nam trong thu muc uploads
        if (!target.normalize().startsWith(ROOT.normalize().toAbsolutePath())
                && !target.normalize().toAbsolutePath().startsWith(ROOT.toAbsolutePath())) {
            throw new IOException("Duong dan khong hop le");
        }

        Files.write(target, data);

        return new Stored(storedName, safeName(originalName), ALLOWED.get(ext), data.length);
    }

    /** Doc lai file da luu, dung khi gui kem cho Gemini. */
    public static byte[] read(String storedName) throws IOException {
        Path file = ROOT.resolve(sanitizeStoredName(storedName));
        if (!Files.exists(file)) {
            throw new IOException("Khong tim thay file");
        }
        return Files.readAllBytes(file);
    }

    public static Path resolve(String storedName) {
        return ROOT.resolve(sanitizeStoredName(storedName));
    }

    /** Xoa file khi khong con dung den. */
    public static void delete(String storedName) {
        try {
            Files.deleteIfExists(ROOT.resolve(sanitizeStoredName(storedName)));
        } catch (IOException e) {
            System.err.println("[FileStorage] Khong xoa duoc file: " + e.getMessage());
        }
    }

    // =====================================================================

    private static String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT).trim();
    }

    /** Bo duong dan khoi ten goc, chi giu lai phan ten de hien thi. */
    private static String safeName(String name) {
        if (name == null || name.isBlank()) {
            return "file";
        }
        String base = name.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        return base.length() > 255 ? base.substring(0, 255) : base;
    }

    /**
     * Ten file tren dia luon co dang UUID.duoi. Chan moi ky tu khac
     * de khong the dung "../" thoat ra ngoai thu muc uploads.
     */
    private static String sanitizeStoredName(String storedName) {
        if (storedName == null || !storedName.matches("[a-fA-F0-9-]{36}\\.[a-z0-9]{1,6}")) {
            throw new IllegalArgumentException("Ten file khong hop le");
        }
        return storedName;
    }
}
