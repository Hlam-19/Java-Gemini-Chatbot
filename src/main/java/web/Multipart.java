package web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Doc du lieu dang multipart/form-data (form co kem file).
 *
 * com.sun.net.httpserver khong ho tro san dinh dang nay nen phai tu phan tich.
 * Chi lam dung phan can thiet: tach cac phan theo boundary, lay ten truong,
 * ten file va noi dung.
 */
public final class Multipart {

    /** Kich thuoc toi da cua ca request, chan gui qua lon lam het bo nho. */
    private static final int MAX_BODY = 25 * 1024 * 1024;   // 25MB

    private Multipart() {
    }

    /** Mot phan trong form: hoac la truong van ban, hoac la file. */
    public record Part(String name, String fileName, String contentType, byte[] data) {

        public boolean isFile() {
            return fileName != null && !fileName.isBlank();
        }

        public String asText() {
            return new String(data, StandardCharsets.UTF_8);
        }
    }

    /** Ket qua phan tich: cac truong van ban va cac file. */
    public record Form(Map<String, String> fields, List<Part> files) {

        public String field(String name, String defaultValue) {
            return fields.getOrDefault(name, defaultValue);
        }

        public Part firstFile() {
            return files.isEmpty() ? null : files.get(0);
        }
    }

    /** Request nay co phai multipart khong. */
    public static boolean isMultipart(String contentType) {
        return contentType != null
                && contentType.toLowerCase().startsWith("multipart/form-data");
    }

    /**
     * Phan tich body cua request.
     *
     * @param contentType gia tri header Content-Type (chua boundary)
     */
    public static Form parse(InputStream input, String contentType) throws IOException {
        String boundary = boundaryOf(contentType);
        if (boundary == null) {
            throw new IOException("Thieu boundary trong Content-Type");
        }

        byte[] body = readAll(input);
        byte[] delimiter = ("--" + boundary).getBytes(StandardCharsets.UTF_8);

        Map<String, String> fields = new HashMap<>();
        List<Part> files = new ArrayList<>();

        int pos = indexOf(body, delimiter, 0);
        while (pos >= 0) {
            int start = pos + delimiter.length;

            // "--" ngay sau boundary nghia la da het
            if (start + 2 <= body.length
                    && body[start] == '-' && body[start + 1] == '-') {
                break;
            }

            // Bo qua CRLF sau boundary
            while (start < body.length && (body[start] == '\r' || body[start] == '\n')) {
                start++;
            }

            // Tim cho ket thuc phan header (dong trong)
            int headerEnd = indexOf(body, "\r\n\r\n".getBytes(StandardCharsets.UTF_8), start);
            if (headerEnd < 0) {
                break;
            }

            String headers = new String(body, start, headerEnd - start, StandardCharsets.UTF_8);
            int dataStart = headerEnd + 4;

            int next = indexOf(body, delimiter, dataStart);
            if (next < 0) {
                break;
            }

            // Bo CRLF truoc boundary ke tiep
            int dataEnd = next;
            if (dataEnd >= 2 && body[dataEnd - 2] == '\r' && body[dataEnd - 1] == '\n') {
                dataEnd -= 2;
            }

            byte[] data = new byte[Math.max(0, dataEnd - dataStart)];
            System.arraycopy(body, dataStart, data, 0, data.length);

            String name = headerValue(headers, "name");
            String fileName = headerValue(headers, "filename");
            String partType = headerLine(headers, "Content-Type");

            if (fileName != null && !fileName.isBlank()) {
                files.add(new Part(name, fileName, partType, data));
            } else if (name != null) {
                fields.put(name, new String(data, StandardCharsets.UTF_8));
            }

            pos = next;
        }

        return new Form(fields, files);
    }

    // =====================================================================

    private static String boundaryOf(String contentType) {
        for (String part : contentType.split(";")) {
            String p = part.trim();
            if (p.toLowerCase().startsWith("boundary=")) {
                String value = p.substring("boundary=".length()).trim();
                // Boundary co the duoc dat trong dau nhay
                if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                    value = value.substring(1, value.length() - 1);
                }
                return value;
            }
        }
        return null;
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        int total = 0;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_BODY) {
                throw new IOException("Du lieu gui len qua lon");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    /** Lay gia tri kieu name="..." trong phan header. */
    private static String headerValue(String headers, String key) {
        String needle = key + "=\"";
        int i = headers.indexOf(needle);
        if (i < 0) {
            return null;
        }
        int start = i + needle.length();
        int end = headers.indexOf('"', start);
        return end < 0 ? null : headers.substring(start, end);
    }

    /** Lay gia tri cua mot dong header, vd "Content-Type: image/png". */
    private static String headerLine(String headers, String key) {
        for (String line : headers.split("\r\n")) {
            if (line.toLowerCase().startsWith(key.toLowerCase() + ":")) {
                return line.substring(key.length() + 1).trim();
            }
        }
        return null;
    }

    /** Tim vi tri xuat hien dau tien cua pattern trong data, bat dau tu from. */
    private static int indexOf(byte[] data, byte[] pattern, int from) {
        outer:
        for (int i = Math.max(0, from); i <= data.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
