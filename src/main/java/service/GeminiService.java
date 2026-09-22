package service;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Properties;
import org.json.JSONArray;
import org.json.JSONObject;

public class GeminiService {

    private String apiKey;

    public GeminiService() {
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream("config.properties")) {
            props.load(fis);
            this.apiKey = props.getProperty("GEMINI_API_KEY");
        } catch (IOException e) {
            System.err.println("Loi doc file config.properties: " + e.getMessage());
        }
    }

    public String askGemini(String prompt) throws Exception {

    if (apiKey == null || apiKey.trim().isEmpty()) {
        return "Loi: Chua cau hinh GEMINI_API_KEY trong file config.properties!";
    }

    String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent";

    JSONObject jsonBody = new JSONObject();
    JSONArray contentsArray = new JSONArray();
    JSONObject contentObj = new JSONObject();
    JSONArray partsArray = new JSONArray();
    JSONObject textObj = new JSONObject();

    textObj.put("text", prompt);
    partsArray.put(textObj);
    contentObj.put("parts", partsArray);
    contentsArray.put(contentObj);
    jsonBody.put("contents", contentsArray);

    HttpClient client = HttpClient.newHttpClient();

    // Thử tối đa 3 lần
    for (int attempt = 1; attempt <= 3; attempt++) {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey.trim())
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody.toString()))
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {

            JSONObject responseJson = new JSONObject(response.body());

            JSONArray candidates =
                    responseJson.getJSONArray("candidates");

            if (candidates.length() > 0) {

                JSONObject firstCandidate =
                        candidates.getJSONObject(0);

                JSONObject content =
                        firstCandidate.getJSONObject("content");

                JSONArray parts =
                        content.getJSONArray("parts");

                return parts.getJSONObject(0).getString("text");
            }

            return "Khong nhan duoc phan hoi tu Gemini.";
        }

        // Nếu là 503 thì chờ rồi thử lại
        if (response.statusCode() == 503) {

            System.out.println(
                    "Gemini dang ban. Thu lai lan "
                    + attempt + "/3..."
            );

            Thread.sleep(attempt * 3000);

        } else {

            return "Loi API (Code "
                    + response.statusCode()
                    + "): "
                    + response.body();
        }
    }

    return "Gemini dang qua tai (503). Vui long thu lai sau.";
}
}