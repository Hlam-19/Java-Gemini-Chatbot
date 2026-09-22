package web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Phuc vu cac file giao dien nam trong src/main/resources/static. */
public class StaticHandler implements HttpHandler {

    private static final String ROOT = "/static";

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/") || path.isEmpty()) {
            path = "/index.html";
        }

        // Chan duong dan kieu ../ de khong doc duoc file ngoai thu muc static
        if (path.contains("..")) {
            notFound(ex);
            return;
        }

        String resource = ROOT + path;
        try (InputStream is = getClass().getResourceAsStream(resource)) {
            if (is == null) {
                notFound(ex);
                return;
            }
            byte[] data = is.readAllBytes();
            ex.getResponseHeaders().add("Content-Type", contentType(path));
            ex.sendResponseHeaders(200, data.length);
            ex.getResponseBody().write(data);
        } finally {
            ex.close();
        }
    }

    private void notFound(HttpExchange ex) throws IOException {
        byte[] data = "404 - Khong tim thay trang".getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
        ex.sendResponseHeaders(404, data.length);
        ex.getResponseBody().write(data);
        ex.close();
    }

    private String contentType(String path) {
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".css"))  return "text/css; charset=utf-8";
        if (path.endsWith(".js"))   return "application/javascript; charset=utf-8";
        if (path.endsWith(".svg"))  return "image/svg+xml";
        if (path.endsWith(".png"))  return "image/png";
        if (path.endsWith(".ico"))  return "image/x-icon";
        return "application/octet-stream";
    }
}
