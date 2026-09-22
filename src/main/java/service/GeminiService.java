package service;

import config.Env;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import model.Message;
import org.json.JSONArray;
import org.json.JSONObject;

public class GeminiService {

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final HttpClient client;

    public GeminiService() {
        this.apiKey = Env.get("GEMINI_API_KEY", "");
        this.model = Env.get("GEMINI_MODEL", "gemini-3.6-flash");
        this.baseUrl = Env.get("GEMINI_API_URL",
                "https://generativelanguage.googleapis.com/v1beta/models");
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /** Ten model dang dung - dung lam mot phan khoa cache. */
    public String getModel() {
        return model;
    }

    /** Mot file gui kem cau hoi. */
    public record Attachment(String mimeType, byte[] data) {
    }

    /** Hoi mot cau don le, khong kem lich su. */
    public String askGemini(String prompt) throws Exception {
        return askGemini(prompt, List.of(), null);
    }

    /** Hoi kem lich su, khong co file dinh kem. */
    public String askGemini(String prompt, List<Message> history) throws Exception {
        return askGemini(prompt, history, null);
    }

    /**
     * Hoi Gemini kem lich su hoi thoai de bot nho ngu canh.
     *
     * @param prompt  cau hoi moi cua nguoi dung
     * @param history cac tin nhan truoc do (role "user" hoac "model")
     */
    public String askGemini(String prompt, List<Message> history, Attachment file)
            throws Exception {

        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("dan_api_key")) {
            return "Loi: Chua cau hinh GEMINI_API_KEY trong file .env!";
        }

        String url = baseUrl + "/" + model + ":generateContent";

        // Dung mang contents: [{role, parts:[{text}]}, ...]
        JSONArray contents = new JSONArray();
        for (Message m : history) {
            contents.put(buildContent(m.getRole(), m.getContent()));
        }
        // Cau hoi moi: gui kem file neu co
        if (file != null) {
            JSONArray parts = new JSONArray();
            parts.put(new JSONObject().put("text", prompt));
            parts.put(new JSONObject().put("inline_data", new JSONObject()
                    .put("mime_type", file.mimeType())
                    .put("data", Base64.getEncoder().encodeToString(file.data()))));
            contents.put(new JSONObject().put("role", "user").put("parts", parts));
        } else {
            contents.put(buildContent("user", prompt));
        }

        JSONObject body = new JSONObject().put("contents", contents);

        // Thu toi da 3 lan neu server ban (503)
        for (int attempt = 1; attempt <= 3; attempt++) {

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", apiKey.trim())
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return extractText(response.body());
            }

            if (response.statusCode() == 503) {
                System.out.println("[Gemini] Server ban, thu lai lan " + attempt + "/3...");
                Thread.sleep(attempt * 3000L);
                continue;
            }

            return "Loi API (Code " + response.statusCode() + "): " + response.body();
        }

        return "Gemini dang qua tai (503). Vui long thu lai sau.";
    }

    private JSONObject buildContent(String role, String text) {
        // Gemini chi chap nhan role "user" hoac "model"
        String safeRole = "model".equalsIgnoreCase(role) ? "model" : "user";
        return new JSONObject()
                .put("role", safeRole)
                .put("parts", new JSONArray().put(new JSONObject().put("text", text)));
    }

    private String extractText(String responseBody) {
        JSONObject json = new JSONObject(responseBody);
        JSONArray candidates = json.optJSONArray("candidates");
        if (candidates == null || candidates.isEmpty()) {
            return "Khong nhan duoc phan hoi tu Gemini.";
        }
        JSONObject content = candidates.getJSONObject(0).optJSONObject("content");
        if (content == null) {
            return "Khong nhan duoc phan hoi tu Gemini.";
        }
        JSONArray parts = content.optJSONArray("parts");
        if (parts == null || parts.isEmpty()) {
            return "Khong nhan duoc phan hoi tu Gemini.";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length(); i++) {
            sb.append(parts.getJSONObject(i).optString("text", ""));
        }
        return sb.toString();
    }
}
