package web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import service.FileStorage;

/**
 * Tra ve file nguoi dung da tai len: GET /uploads/{ten-file}
 *
 * Yeu cau dang nhap. Ten file la UUID do he thong sinh nen khong doan duoc,
 * va FileStorage tu chan moi ten khong dung dinh dang UUID.
 */
public class UploadHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange ex) throws IOException {
        if (Http.currentUserId(ex) == null) {
            Http.sendError(ex, 401, "Vui long dang nhap truoc");
            return;
        }
        if (!Http.requireMethod(ex, "GET")) {
            return;
        }

        String path = ex.getRequestURI().getPath();       // /uploads/xxx.png
        String name = path.substring(path.lastIndexOf('/') + 1);

        try {
            Path file = FileStorage.resolve(name);
            if (!Files.exists(file)) {
                notFound(ex);
                return;
            }

            byte[] data = Files.readAllBytes(file);
            ex.getResponseHeaders().add("Content-Type", FileStorage.mimeTypeOf(name));
            ex.getResponseHeaders().add("Cache-Control", "private, max-age=86400");
            ex.sendResponseHeaders(200, data.length);
            ex.getResponseBody().write(data);
            ex.close();

        } catch (IllegalArgumentException e) {
            // Ten file khong dung dinh dang UUID - co the la thu do duong dan
            notFound(ex);
        }
    }

    private void notFound(HttpExchange ex) throws IOException {
        byte[] data = "404 - Khong tim thay file".getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
        ex.sendResponseHeaders(404, data.length);
        ex.getResponseBody().write(data);
        ex.close();
    }
}
