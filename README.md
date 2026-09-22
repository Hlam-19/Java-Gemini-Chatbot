# Gemini Chatbot (Java)

Ung dung web chatbot viet bang Java thuan, dung HTTP server co san trong JDK
(`com.sun.net.httpserver`) - khong can Tomcat hay Spring.

## Tinh nang

- Dang ky / dang nhap, mat khau bam SHA-256
- Chat voi Gemini, bot nho ngu canh 20 tin nhan gan nhat
- **Quan ly nhieu doan chat**: tao moi, doi ten tai cho, xoa (co xac nhan), chuyen qua lai
- Tieu de doan chat tu sinh tu cau hoi dau tien
- Luu lich su vao MySQL, moi user chi thay du lieu cua minh
- Giao dien toi (dark), responsive, ho tro dieu huong ban phim
- Redis: luu phien dang nhap (song qua restart), cache lich su va cau tra loi,
  gioi han so tin nhan moi phut

## Yeu cau

**Cach nhanh nhat:** chi can **Docker Desktop**.

**Chay truc tiep de code:** JDK 21+, Maven 3.8+, MySQL 8.0+, va Docker (cho Redis).

## Chay bang Docker (khuyen nghi)

```bash
cp .env.example .env          # roi dan GEMINI_API_KEY vao .env
docker compose up -d --build
```

Mo <http://localhost:8080>. Lenh nay dung san MySQL, Redis va ung dung.

```bash
docker compose logs -f app    # xem log
docker compose down           # tat (du lieu van con)
docker compose down -v        # tat va xoa sach du lieu
```

## Cai dat thu cong

1. Tao database trong MySQL:

   ```sql
   CREATE DATABASE chatbot_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```

   Hai bang `users` va `messages` se duoc tu dong tao khi chay lan dau.

2. Tao file cau hinh tu mau:

   ```bash
   cp .env.example .env
   ```

3. Mo `.env`, dan API key lay tai <https://aistudio.google.com/apikey>:

   ```
   GEMINI_API_KEY=AIza...
   ```

4. Sua thong tin ket noi MySQL trong `.env` cho dung may cua ban
   (`DB_URL`, `DB_USER`, `DB_PASSWORD`).

5. Build va chay:

   ```bash
   mvn clean package
   java -jar target/chatbot-1.0-SNAPSHOT.jar
   ```

6. Mo trinh duyet: <http://localhost:8080>

> Neu may co nhieu ban JDK, chay bang JDK 21+ cu the:
> `"$JAVA_HOME/bin/java" -jar target/chatbot-1.0-SNAPSHOT.jar`

## Cac bien trong .env

| Bien | Mac dinh | Y nghia |
|------|----------|---------|
| `GEMINI_API_KEY` | (bat buoc) | API key cua Google AI Studio |
| `GEMINI_MODEL` | `gemini-3.6-flash` | Ten model muon dung |
| `GEMINI_API_URL` | `https://generativelanguage.googleapis.com/v1beta/models` | Dia chi goc cua API |
| `SERVER_PORT` | `8080` | Cong web server |
| `DB_URL` | `jdbc:mysql://localhost:3306/chatbot_db?...` | Chuoi ket noi MySQL |
| `DB_USER` | `root` | User database |
| `DB_PASSWORD` | (rong) | Mat khau database |

Thu tu uu tien: **bien moi truong he thong** > **file .env** > **gia tri mac dinh**.
Khi deploy len server that, chi can set environment variable, khong can mang file `.env` theo.

File `.env` da nam trong `.gitignore` - khong bao gio bi commit len git.

## Xem du lieu trong database

Du lieu nam trong MySQL (database `chatbot_db`), xem bang **DBeaver** hoac bat ky
cong cu nao khac - khong can tat server chatbot.

**Ket noi DBeaver:**

| Truong | Gia tri |
|--------|---------|
| Driver | MySQL |
| Host | `localhost` |
| Port | `3306` |
| Database | `chatbot_db` |
| Username | `root` |
| Password | (de trong, hoac mat khau MySQL cua ban) |

Neu DBeaver bao loi ve `serverTimezone`, vao tab **Driver properties** dat
`serverTimezone` = `Asia/Ho_Chi_Minh`.

**Hoac dung dong lenh:**

```bash
mysql -u root chatbot_db -e "SELECT * FROM users; SELECT * FROM messages;"
```

**Cau truc bang:**

```
users                 sessions                  messages
  id            PK      id            PK          id          PK
  username      UQ      user_id       FK->users   user_id     FK->users
  password_hash         title                     session_id  FK->sessions
  created_at            created_at                role        'user'|'model'
                        updated_at                content     TEXT
                                                  created_at
```

Quan he: mot user co nhieu session, moi session co nhieu message.
Xoa user se xoa session va message cua ho; xoa session se xoa message
ben trong (`ON DELETE CASCADE`).

## Cau truc thu muc

```
src/main/java/
  config/Env.java          # Doc bien tu .env
  dao/                     # Tang truy cap du lieu
    DBConnection.java      # Ket noi + tao bang
    UserDAO.java           # Dang ky, dang nhap
    SessionDAO.java        # CRUD doan chat
    MessageDAO.java        # Luu / doc tin nhan
    PasswordUtil.java      # Bam mat khau
  model/                   # User, ChatSession, Message
  service/GeminiService.java   # Goi Gemini API
  web/
    WebServer.java         # Diem khoi dong, dinh tuyen
    AuthHandler.java       # /api/register, /api/login, /api/logout, /api/me
    ChatHandler.java       # /api/chat, /api/history
    SessionHandler.java    # CRUD doan chat
    StaticHandler.java     # Phuc vu file giao dien
    SessionManager.java    # Quan ly phien dang nhap
    Http.java              # Tien ich JSON / cookie
src/main/resources/static/  # index.html, style.css, app.js
```

## API

| Method | Duong dan | Mo ta |
|--------|-----------|-------|
| POST | `/api/register` | Dang ky, tra ve cookie phien |
| POST | `/api/login` | Dang nhap |
| POST | `/api/logout` | Dang xuat |
| GET | `/api/me` | Kiem tra phien hien tai |
| POST | `/api/chat` | Gui tin nhan (kem `sessionId`, bo trong se tu tao doan chat moi) |
| GET | `/api/history?sessionId=N` | Lay lich su cua mot doan chat |
| GET | `/api/sessions` | Danh sach doan chat |
| POST | `/api/sessions` | Tao doan chat moi |
| PATCH | `/api/sessions/{id}` | Doi ten doan chat |
| DELETE | `/api/sessions/{id}` | Xoa doan chat (kem toan bo tin nhan) |

## Thiet ke giao dien

Giao dien duoc dung theo huong dan cua skill `ui-ux-pro-max`:

| Hang muc | Gia tri |
|----------|---------|
| Style | AI-Native UI (chatbot, conversational) |
| Mau chinh | `#7C3AED` (tim) + `#22D3EE` (cyan) |
| Font | Inter |
| Nen | Thang do cao dan: `#020617` -> `#0E1223` -> `#161B2E` |

Cac diem da tuan thu:
- Vung cham toi thieu 44x44px
- Vien focus ro rang cho dieu huong ban phim
- Icon dung SVG (khong dung emoji)
- Xac nhan truoc khi xoa, kem thong bao sau khi xong
- Ton trong `prefers-reduced-motion`
- Responsive: sidebar truot ra tren man hinh < 860px

## Tai lieu chi tiet

Xem [docs/HUONG-DAN-DU-AN.md](docs/HUONG-DAN-DU-AN.md) - giai thich tung file,
so do luong xu ly, quy uoc code va cach go loi. Danh cho thanh vien moi.

## Han che hien tai (neu muon phat trien tiep)

- Mat khau bam SHA-256 khong salt - du an that nen dung BCrypt
- Server chay HTTP, chua co HTTPS
- Chua phan trang lich su, doan chat rat dai se tai cham
- Chua co test tu dong
