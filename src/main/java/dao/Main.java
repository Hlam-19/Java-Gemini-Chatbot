package dao;

import model.User;
import service.GeminiService;

public class Main {

    public static void main(String[] args) {
        try {
            DBConnection.initTables();

            UserDAO userDAO = new UserDAO();
            
            // Tao username ngau nhien bang thoi gian de khong bao gio bi trung UNIQUE
            String testUser = "user_" + (System.currentTimeMillis() % 10000);
            userDAO.register(testUser, "123456");

            User user = userDAO.login(testUser, "123456");
            if (user == null) {
                System.out.println("Dang nhap that bai!");
                return;
            }
            System.out.println("Dang nhap thanh cong voi user: " + testUser);

            String cauHoi = "Giai thich ngan gon dinh luat Newton 1 trong 2 cau.";
            System.out.println("[USER GUI]: " + cauHoi);

            MessageDAO messageDAO = new MessageDAO();
            messageDAO.saveMessage(user.getId(), "user", cauHoi);

            System.out.println("Dang ket noi toi Gemini API, vui long cho...");
            GeminiService geminiService = new GeminiService();
            String cauTraLoi = geminiService.askGemini(cauHoi); 

            System.out.println("\n[GEMINI PHAN HOI]:\n" + cauTraLoi);

            messageDAO.saveMessage(user.getId(), "model", cauTraLoi);

        } catch (Exception e) {
            System.err.println("LOI KHI CHAY:");
            e.printStackTrace();
        }
    }
}