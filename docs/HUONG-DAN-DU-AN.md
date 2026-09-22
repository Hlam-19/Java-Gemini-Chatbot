# Gemini Chatbot — Tài liệu dự án

Tài liệu này dành cho thành viên mới tham gia dự án. Đọc xong bạn sẽ biết:
dự án làm gì, chạy thế nào, mỗi file chịu trách nhiệm gì, và dữ liệu đi qua
những đâu khi bạn gõ một câu hỏi.

---

## Mục lục

1. [Dự án này là gì](#1-dự-án-này-là-gì)
2. [Công nghệ sử dụng](#2-công-nghệ-sử-dụng)
3. [Chạy dự án lần đầu](#3-chạy-dự-án-lần-đầu)
4. [Kiến trúc tổng thể](#4-kiến-trúc-tổng-thể)
5. [Giải thích từng file](#5-giải-thích-từng-file)
6. [Cơ sở dữ liệu](#6-cơ-sở-dữ-liệu)
7. [Redis dùng làm gì](#7-redis-dùng-làm-gì)
8. [Danh sách API](#8-danh-sách-api)
9. [Luồng hoạt động](#9-luồng-hoạt-động)
10. [Quy ước khi viết code](#10-quy-ước-khi-viết-code)
11. [Gỡ lỗi thường gặp](#11-gỡ-lỗi-thường-gặp)
12. [Hạn chế và hướng phát triển](#12-hạn-chế-và-hướng-phát-triển)

---

## 1. Dự án này là gì

Ứng dụng web chatbot gọi tới Google Gemini API. Người dùng đăng ký tài khoản,
tạo nhiều đoạn hội thoại riêng biệt, và trò chuyện với AI. Toàn bộ lịch sử được
lưu lại để lần sau mở ra đọc tiếp.

**Điểm đáng chú ý về mặt kỹ thuật:** dự án viết bằng **Java thuần**, dùng
`com.sun.net.httpserver` có sẵn trong JDK — **không dùng Spring Boot, không
dùng Tomcat**. Mục đích là để người đọc hiểu rõ từng bước xử lý HTTP thay vì
phó mặc cho framework.

### Tính năng

| Tính năng | Mô tả |
|-----------|-------|
| Đăng ký / đăng nhập | Mật khẩu băm SHA-256, phiên lưu bằng cookie HttpOnly |
| Chat với Gemini | Gửi kèm 20 tin nhắn gần nhất để bot nhớ ngữ cảnh |
| Nhiều đoạn chat | Tạo, đổi tên tại chỗ, xoá (có xác nhận), chuyển qua lại |
| Tự đặt tên | Tiêu đề đoạn chat lấy từ câu hỏi đầu tiên |
| Cache | Redis giảm tải MySQL và tiết kiệm quota API |
| Chống spam | Giới hạn số tin nhắn mỗi phút cho từng người dùng |
| Đính kèm file | Gửi ảnh, PDF, file văn bản cho Gemini đọc |
| Hiển thị Markdown | Câu trả lời render thành tiêu đề, danh sách, bảng, khối code |

---

## 2. Công nghệ sử dụng

| Thành phần | Lựa chọn | Vì sao |
|------------|----------|--------|
| Ngôn ngữ | Java 21 (LTS) | Có text block (`"""`), bản LTS ổn định |
| HTTP server | `com.sun.net.httpserver` | Có sẵn trong JDK, không cần thư viện ngoài |
| Cơ sở dữ liệu | MySQL 8 | Phổ biến, xem được bằng DBeaver/Workbench |
| Cache | Redis 7 | Lưu phiên + cache + đếm rate limit |
| Thư viện | `mysql-connector-j`, `jedis`, `org.json` | Tối thiểu, dễ đọc |
| Giao diện | HTML/CSS/JS thuần | Không build step, mở file là đọc được |
| Đóng gói | Docker Compose | Team chạy một lệnh là có đủ môi trường |

**Chủ ý:** giữ số thư viện ở mức tối thiểu. Mỗi thư viện thêm vào là một thứ
người đọc phải học.

---

## 3. Chạy dự án lần đầu

### Cách A — Docker Compose (khuyến nghị)

Cần cài sẵn: **Docker Desktop**.

```bash
# 1. Tạo file cấu hình từ mẫu
cp .env.example .env

# 2. Mở .env, dán API key lấy tại https://aistudio.google.com/apikey
#    GEMINI_API_KEY=AIza...

# 3. Chạy toàn bộ (MySQL + Redis + ứng dụng)
docker compose up -d --build

# 4. Xem log để chắc chắn đã lên
docker compose logs -f app
```

Mở trình duyệt: <http://localhost:8080>

Các lệnh hay dùng:

```bash
docker compose ps            # xem trạng thái
docker compose logs -f app   # xem log ứng dụng
docker compose restart app          # khởi động lại sau khi sửa .env
docker compose up -d --build app    # build lại sau khi sửa code Java
docker compose down          # tắt (dữ liệu vẫn còn)
docker compose down -v       # tắt và XOÁ SẠCH dữ liệu
```

### Cách B — Chạy trực tiếp (khi đang code)

Cần cài sẵn: **JDK 21+**, **Maven**, **MySQL**, và **Docker** (chỉ để chạy Redis).

```bash
# 1. Tạo database
mysql -u root -e "CREATE DATABASE chatbot_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 2. Chạy riêng Redis
docker compose up -d redis

# 3. Sửa .env cho khớp MySQL trên máy bạn (DB_USER, DB_PASSWORD)

# 4. Build và chạy
mvn clean package
java -jar target/chatbot-1.0-SNAPSHOT.jar
```

> **Máy có nhiều bản JDK?** Dùng đường dẫn tường minh:
> `"$JAVA_HOME/bin/java" -jar target/chatbot-1.0-SNAPSHOT.jar`
>
> Nếu chạy `java -jar` mà gặp lỗi `UnsupportedClassVersionError`, đó là do
> lệnh `java` trên PATH là bản cũ hơn bản Maven dùng để build.

Khi khởi động thành công, log sẽ như sau:

```
[Env] Da nap 18 bien tu .env
[DB] Da ket noi MySQL va khoi tao bang users, sessions, messages.
[Redis] Da ket noi localhost:6379
=================================================
  Chatbot dang chay tai: http://localhost:8080
  Model: gemini-2.5-flash
  Redis: da ket noi
=================================================
```

---

## 4. Kiến trúc tổng thể

```
        Trình duyệt
     (HTML + CSS + JS)
             |
             | HTTP + cookie phiên
             v
   +---------------------------+
   |   WebServer (định tuyến)  |
   +---------------------------+
      |        |           |
      v        v           v
   Auth     Chat        Session      <- tầng web (xử lý HTTP)
  Handler  Handler      Handler
      |        |           |
      +--------+-----------+
               |
       +-------+--------+
       |                |
       v                v
   GeminiService      DAO           <- tầng nghiệp vụ / dữ liệu
   (gọi API)      (UserDAO,
                   SessionDAO,
                   MessageDAO)
       |                |
       |          +-----+-----+
       v          v           v
   Gemini API   Redis       MySQL
                (cache)   (lưu lâu dài)
```

### Phân tầng

Dự án chia 4 tầng, **tầng trên gọi tầng dưới, không đi ngược lại**:

| Tầng | Gói | Nhiệm vụ |
|------|-----|----------|
| Giao diện | `resources/static/` | Hiển thị, bắt sự kiện người dùng |
| Web | `web/` | Nhận HTTP, kiểm tra quyền, trả JSON |
| Nghiệp vụ | `service/`, `cache/` | Gọi Gemini, quản lý cache |
| Dữ liệu | `dao/`, `model/` | Đọc/ghi MySQL |

**Quy tắc:** `web/` không được viết SQL trực tiếp; mọi truy vấn phải đi qua `dao/`.

---

## 5. Giải thích từng file

### Cấu trúc thư mục

```
Java-Gemini-Chatbot/
├── .env                       # Cấu hình thật (KHÔNG commit)
├── .env.example               # Mẫu cấu hình để team copy
├── .gitignore
├── .dockerignore
├── Dockerfile                 # Đóng gói ứng dụng thành image
├── docker-compose.yml         # Chạy MySQL + Redis + app
├── pom.xml                    # Khai báo thư viện, cấu hình build
├── README.md                  # Hướng dẫn ngắn
├── docs/
│   └── HUONG-DAN-DU-AN.md    # Tài liệu này
└── src/main/
    ├── java/
    │   ├── config/Env.java
    │   ├── cache/
    │   │   ├── RedisClient.java
    │   │   └── ChatCache.java
    │   ├── dao/
    │   │   ├── DBConnection.java
    │   │   ├── UserDAO.java
    │   │   ├── SessionDAO.java
    │   │   ├── MessageDAO.java
    │   │   └── PasswordUtil.java
    │   ├── model/
    │   │   ├── User.java
    │   │   ├── ChatSession.java
    │   │   └── Message.java
    │   ├── service/
    │   │   ├── GeminiService.java
    │   │   └── FileStorage.java
    │   └── web/
    │       ├── WebServer.java
    │       ├── AuthHandler.java
    │       ├── ChatHandler.java
    │       ├── SessionHandler.java
    │       ├── StaticHandler.java
    │       ├── UploadHandler.java
    │       ├── Multipart.java
    │       ├── SessionManager.java
    │       └── Http.java
    └── resources/static/
        ├── index.html
        ├── style.css
        ├── app.js
        ├── markdown.js         # Chuyển Markdown -> HTML
        └── favicon.svg
```

---

### 5.1. Cấu hình

#### `config/Env.java`

Đọc biến cấu hình từ file `.env` ở thư mục gốc.

Thứ tự ưu tiên: **biến môi trường hệ thống → file `.env` → giá trị mặc định**.
Nhờ vậy khi deploy chỉ cần set environment variable, không phải mang file theo —
và đó cũng là cách `docker-compose.yml` truyền cấu hình vào container.

```java
Env.get("GEMINI_MODEL", "gemini-2.5-flash");   // có mặc định
Env.getInt("SERVER_PORT", 8080);               // ép kiểu số
Env.require("GEMINI_API_KEY");                 // thiếu thì ném lỗi ngay
```

Lớp này tự bỏ qua dòng trống, dòng bắt đầu bằng `#`, và bóc dấu nháy quanh giá trị.

> **Lưu ý:** `.env` nằm trong `.gitignore`. Không bao giờ commit file này —
> nó chứa API key. Khi thêm biến mới, nhớ cập nhật cả `.env.example`.

---

### 5.2. Tầng cache (`cache/`)

#### `cache/RedisClient.java`

Quản lý connection pool tới Redis.

**Nguyên tắc quan trọng nhất của file này:** Redis chỉ là lớp tăng tốc, **không
phải nguồn dữ liệu chính**. Redis chết thì ứng dụng vẫn phải chạy bình thường
bằng MySQL.

Vì vậy mọi thao tác đều đi qua `execute(...)` — tự bắt lỗi và trả về giá trị
mặc định thay vì ném ra ngoài:

```java
// Redis lỗi thì trả về null, nơi gọi tự đọc MySQL
String json = RedisClient.execute(jedis -> jedis.get(key), null);
```

#### `cache/ChatCache.java`

Ba nhiệm vụ, mỗi nhiệm vụ một tiền tố khoá riêng:

| Khoá | Nội dung | Hạn dùng |
|------|----------|----------|
| `history:{sessionId}` | Lịch sử tin nhắn của đoạn chat | 1 giờ |
| `reply:{hash}` | Câu trả lời của Gemini | 24 giờ |
| `rate:{userId}:{phút}` | Số lần gọi trong phút đó | 2 phút |

**Về cache câu trả lời:** chỉ áp dụng cho **câu hỏi đầu tiên** của đoạn chat.
Lý do: cùng một câu hỏi nhưng ngữ cảnh trước đó khác nhau thì câu trả lời phải
khác nhau. Dùng lại câu trả lời cũ trong trường hợp đó sẽ sai.

Cũng **không cache thông báo lỗi** — lần sau gọi lại có thể thành công.

**Về rate limit:** dùng kỹ thuật "cửa sổ theo phút". Mỗi phút là một khoá riêng
(`rate:5:29834983`), hết phút thì khoá tự hết hạn — không cần dọn dẹp.

---

### 5.3. Tầng dữ liệu (`dao/` và `model/`)

#### `dao/DBConnection.java`

Mở kết nối MySQL và tạo 3 bảng nếu chưa có (`CREATE TABLE IF NOT EXISTS`).
Chạy một lần lúc khởi động, nên team không phải import file SQL thủ công.

#### `dao/PasswordUtil.java`

Băm mật khẩu bằng SHA-256.

> ⚠️ **Cảnh báo:** SHA-256 không có salt, **không đủ an toàn cho sản phẩm thật**.
> Dự án thực tế phải dùng BCrypt hoặc Argon2. Ở đây giữ SHA-256 vì mục đích học tập
> và để không thêm thư viện.

#### `dao/UserDAO.java`

| Hàm | Việc |
|-----|------|
| `register(username, plainPassword)` | Đăng ký — **tự băm mật khẩu** trước khi lưu |
| `login(username, plainPassword)` | Đăng nhập, trả `User` hoặc `null` |
| `findById(id)` | Khôi phục thông tin từ phiên |
| `existsByUsername(username)` | Kiểm tra trùng tên |

> Cả `register` và `login` đều nhận **mật khẩu thô** và tự băm bên trong.
> Nơi gọi không cần biết đến việc băm.

#### `dao/SessionDAO.java`

CRUD đoạn chat. Đáng chú ý là `belongsTo(sessionId, userId)` — kiểm tra quyền
sở hữu. **Mọi thao tác lên một đoạn chat đều phải gọi hàm này trước**, nếu không
người dùng A sẽ đọc/sửa/xoá được đoạn chat của người dùng B.

`touch(sessionId)` cập nhật `updated_at` để đoạn chat vừa dùng nhảy lên đầu danh sách.

#### `dao/MessageDAO.java`

Đọc/ghi tin nhắn, có gắn cache:

- `getHistory(sessionId)` — thử Redis trước, không có mới đọc MySQL rồi cache lại
- `saveMessage(...)` — ghi MySQL xong **xoá cache** của đoạn chat đó (vì lịch sử đã đổi)

#### `model/User.java`, `model/ChatSession.java`, `model/Message.java`

Các lớp chỉ chứa dữ liệu (getter/setter), tương ứng với 3 bảng trong MySQL.

---

### 5.4. Tầng nghiệp vụ (`service/`)

#### `service/GeminiService.java`

Gọi Gemini API.

```java
String reply = gemini.askGemini(prompt, history);
```

Điểm cần biết:

- Đọc API key, tên model, URL từ `.env` — đổi model chỉ cần sửa `.env`, không build lại
- Gửi kèm `history` để bot nhớ ngữ cảnh; Gemini chỉ chấp nhận `role` là `"user"` hoặc `"model"`
- Gặp lỗi 503 (server bận) thì **tự thử lại 3 lần**, mỗi lần chờ lâu hơn
- Trả về chuỗi thông báo lỗi thay vì ném exception, để tầng web hiển thị cho người dùng

---

#### `service/FileStorage.java`

Lưu file người dùng tải lên vào thư mục `uploads/`.

Ba quy tắc an toàn quan trọng:

1. **Tên file trên đĩa do hệ thống sinh** (UUID), không dùng tên gốc — người
   dùng không thể đặt tên kiểu `../../etc/passwd` hay ghi đè file khác.
   Tên gốc vẫn lưu trong database để hiển thị lại cho đúng.
2. **Chỉ chấp nhận đuôi file trong danh sách cho phép** (ảnh, PDF, file văn bản).
3. **Giới hạn dung lượng** (mặc định 10MB, đổi bằng `UPLOAD_MAX_MB`).

Ảnh và PDF được gửi cho Gemini dưới dạng dữ liệu nhị phân (`inline_data`);
file văn bản thì đọc nội dung rồi ghép thẳng vào câu hỏi.

### 5.5. Tầng web (`web/`)

#### `web/WebServer.java` — điểm khởi động

Tạo bảng, kiểm tra Redis, đăng ký các route, rồi mở cổng.

```java
server.createContext("/api/login",    auth);
server.createContext("/api/chat",     chat);
server.createContext("/api/sessions", sessions);   // bắt cả /api/sessions/{id}
server.createContext("/",             new StaticHandler());
```

Dùng **thread pool 16 luồng** vì mỗi lần gọi Gemini mất vài giây — nếu chạy một
luồng thì một người dùng sẽ chặn tất cả những người còn lại.

#### `web/Http.java` — tiện ích dùng chung

Gom các việc lặp đi lặp lại: đọc JSON từ request, gửi JSON về, đọc cookie,
lấy `userId` của phiên hiện tại, kiểm tra HTTP method.

```java
Integer userId = Http.currentUserId(ex);     // null = chưa đăng nhập
Http.sendJson(ex, 200, new JSONObject()...);
Http.sendError(ex, 404, "Khong tim thay");
```

#### `web/SessionManager.java` — quản lý phiên

Sinh token ngẫu nhiên 32 byte, lưu vào Redis kèm hạn dùng (mặc định 7 ngày).

Hai điểm thiết kế quan trọng:

1. **Gia hạn khi còn hoạt động** — mỗi lần kiểm tra phiên hợp lệ thì đặt lại TTL,
   nên người dùng đang dùng sẽ không bị đăng xuất giữa chừng.

2. **Luôn ghi song song vào bộ nhớ (`FALLBACK`)** — kể cả khi Redis đang sống.
   Nếu Redis chết ngay sau đó, phiên vẫn dùng được. Khi đọc, Redis không trả về
   gì thì tra tiếp bộ nhớ trước khi kết luận token không hợp lệ.

#### `web/AuthHandler.java`

Xử lý `/api/register`, `/api/login`, `/api/logout`, `/api/me`.

Kiểm tra dữ liệu đầu vào (tên ≥ 3 ký tự, mật khẩu ≥ 6 ký tự), rồi đặt cookie:

```
Set-Cookie: SESSION=<token>; Path=/; HttpOnly; SameSite=Strict
```

- `HttpOnly` — JavaScript không đọc được cookie, chống đánh cắp qua XSS
- `SameSite=Strict` — trình duyệt không gửi cookie khi request đến từ site khác, chống CSRF

#### `web/ChatHandler.java`

Xử lý `/api/chat` và `/api/history`. Đây là file có logic dày nhất:

1. Kiểm tra đăng nhập
2. **Kiểm tra rate limit trước** — chặn spam trước khi làm bất cứ việc gì tốn kém
3. Chưa có `sessionId` → tự tạo đoạn chat mới, lấy câu hỏi làm tiêu đề
4. Lấy 20 tin gần nhất làm ngữ cảnh (**lấy trước khi lưu câu hỏi mới**, để
   không gửi trùng chính nó)
5. Nếu là câu hỏi đầu tiên → thử lấy từ cache
6. Không có trong cache → gọi Gemini, rồi lưu vào cache
7. Lưu câu hỏi + câu trả lời vào MySQL

#### `web/SessionHandler.java`

CRUD đoạn chat. Tự phân tích đường dẫn `/api/sessions/{id}` vì
`com.sun.net.httpserver` không có cơ chế route tham số như Spring.

**Mọi thao tác đều kiểm tra `belongsTo` trước**, và trả về `404` (không phải `403`)
khi không có quyền — để người ngoài không đoán được đoạn chat đó có tồn tại hay không.

#### `web/Multipart.java`

Đọc dữ liệu form có kèm file (`multipart/form-data`).

`com.sun.net.httpserver` không hỗ trợ sẵn định dạng này nên phải tự phân tích:
tách các phần theo `boundary`, lấy tên trường, tên file và nội dung.

#### `web/UploadHandler.java`

Trả về file người dùng đã tải lên (`GET /uploads/{tên-file}`).

**Bắt buộc đăng nhập.** Tên file trên đĩa là UUID do hệ thống sinh nên không
đoán được, và `FileStorage` từ chối mọi tên không đúng định dạng UUID.

#### `web/StaticHandler.java`

Phục vụ file giao diện từ trong jar. Chặn đường dẫn chứa `..` để người ngoài
không đọc được file ngoài thư mục `static`.

---

### 5.6. Giao diện (`resources/static/`)

#### `index.html`

Hai màn hình nằm cùng một trang, ẩn/hiện bằng thuộc tính `hidden`:

- `#authScreen` — đăng nhập / đăng ký
- `#appScreen` — sidebar + khu vực chat

Cùng với hộp thoại xác nhận xoá (`#confirmDialog`) và thông báo ngắn (`#toast`).

#### `style.css`

Dùng biến CSS để định nghĩa màu, khoảng cách, bo góc ở một chỗ duy nhất:

```css
:root {
  --color-primary: #7C3AED;   /* tím */
  --color-accent:  #22D3EE;   /* cyan */
  --space-4: 16px;
  --radius: 12px;
}
```

**Đổi màu chủ đạo của cả ứng dụng = sửa một dòng.**

Có một dòng nhìn thì lạ nhưng rất quan trọng:

```css
[hidden] { display: none !important; }
```

Không có dòng này, `.dialog-backdrop { display: grid }` sẽ đè lên `hidden`,
khiến hộp thoại dù đã ẩn vẫn **chặn toàn bộ click trên trang**. Đây là lỗi thật
đã từng xảy ra trong dự án.

#### `markdown.js`

Chuyển Markdown mà Gemini trả về thành HTML: tiêu đề, khối code (kèm nút
"Sao chép"), danh sách, bảng, trích dẫn, in đậm/nghiêng, liên kết.

**Điểm an toàn quan trọng:** hàm `renderMarkdown()` escape toàn bộ HTML
**trước khi** parse. Nội dung do Gemini trả về được coi là không đáng tin —
nếu nó trả về `<script>` thì phải hiện ra dưới dạng chữ, không được chạy.

Viết tay thay vì dùng thư viện để giữ dự án tối thiểu dependency.

#### `app.js`

Toàn bộ logic giao diện, không dùng framework.

Có một điểm đáng học: hàm `backToAuth()` khi đăng xuất **không dùng
`location.reload()`**. Lý do là trình duyệt có thể phục vụ lại trang từ bộ nhớ
đệm kèm cookie cũ, khiến người dùng bị đưa thẳng vào màn chat dù đã đăng xuất.
Thay vào đó, hàm này đổi trạng thái trực tiếp và dọn sạch dữ liệu phiên trước.

---

## 6. Cơ sở dữ liệu

### Sơ đồ quan hệ

```
   users                sessions                 messages
  ─────────            ──────────               ──────────
  id       PK  ──┐     id          PK  ──┐      id          PK
  username UQ    └──<  user_id     FK    └──<   session_id  FK
  password_hash        title                    user_id     FK
  created_at           created_at               role
                       updated_at               content
                                                created_at
```

- Một `user` có nhiều `session`
- Một `session` có nhiều `message`
- `role` chỉ nhận `'user'` (người hỏi) hoặc `'model'` (Gemini trả lời)

### Ràng buộc xoá dây chuyền

Cả hai khoá ngoại đều đặt `ON DELETE CASCADE`:

- Xoá một **user** → xoá hết session và message của họ
- Xoá một **session** → xoá hết message bên trong

Nhờ vậy không bao giờ còn dữ liệu mồ côi.

### Xem dữ liệu bằng DBeaver

| Trường | Giá trị (chạy trực tiếp) | Giá trị (chạy Docker) |
|--------|--------------------------|------------------------|
| Driver | MySQL | MySQL |
| Host | `localhost` | `localhost` |
| Port | `3306` | `3307` |
| Database | `chatbot_db` | `chatbot_db` |
| Username | `root` | `root` |
| Password | (mật khẩu MySQL của bạn) | `rootpassword` |

> Cổng khác nhau là có chủ ý: Docker dùng **3307** để không đụng MySQL
> đã cài sẵn trên máy.

Nếu DBeaver báo lỗi timezone, vào tab **Driver properties** đặt
`serverTimezone` = `Asia/Ho_Chi_Minh`.

---

## 7. Redis dùng làm gì

Redis đảm nhận 4 việc:

### 7.1. Lưu phiên đăng nhập

Trước đây phiên nằm trong RAM của Java — **restart server là mọi người bị đăng
xuất**. Giờ phiên nằm trong Redis nên sống qua restart, và tự hết hạn sau 7 ngày.

### 7.2. Cache lịch sử tin nhắn

Mỗi lần gửi tin đều cần lấy 20 tin gần nhất làm ngữ cảnh. Cache giúp không phải
hỏi MySQL mỗi lần.

### 7.3. Cache câu trả lời của Gemini

Cùng một câu hỏi (ở đầu đoạn chat) thì trả lại kết quả cũ, không gọi API.

Đo thực tế: **3942ms → 1119ms**, nhanh hơn ~3.5 lần và không tốn quota.

### 7.4. Giới hạn số lần gọi

Mặc định 20 tin nhắn/phút/người. Vượt quá trả về HTTP `429`.

### Xem dữ liệu trong Redis

```bash
docker exec -it chatbot-redis redis-cli

KEYS session:*        # các phiên đang hoạt động
KEYS history:*        # lịch sử đang được cache
KEYS reply:*          # câu trả lời đã cache
KEYS rate:*           # bộ đếm rate limit
TTL session:abc123    # xem còn sống bao lâu (giây)
FLUSHALL              # xoá sạch (chỉ dùng khi dev)
```

### Tắt Redis khi cần

Đặt `REDIS_ENABLED=false` trong `.env`. Ứng dụng vẫn chạy đầy đủ, chỉ chậm hơn
và phiên đăng nhập sẽ mất khi restart.

---

### 7.5. Đính kèm file

Người dùng gửi kèm ảnh, PDF hoặc file văn bản theo ba cách: bấm nút kẹp giấy,
kéo thả vào cửa sổ, hoặc dán ảnh từ clipboard (Ctrl+V).

| Loại file | Cách gửi cho Gemini |
|-----------|---------------------|
| Ảnh (png, jpg, webp, gif) | Dữ liệu nhị phân — Gemini "nhìn" được ảnh |
| PDF | Dữ liệu nhị phân — Gemini đọc được nội dung |
| Văn bản (txt, md, java, json…) | Đọc nội dung, ghép vào câu hỏi (tối đa 30.000 ký tự) |

File lưu trong thư mục `uploads/` với tên UUID. Database chỉ lưu đường dẫn và
tên gốc. Thư mục này nằm trong `.gitignore`; khi chạy Docker nó được gắn vào
volume `uploads-data` nên không mất khi container khởi động lại.

**Không cache câu trả lời** cho tin nhắn có đính kèm — cùng một câu hỏi nhưng
file khác nhau thì kết quả phải khác.

## 8. Danh sách API

Mọi API đều nhận và trả JSON. Lỗi trả về dạng `{"error": "mô tả"}`.

### Xác thực

| Method | Đường dẫn | Body | Trả về |
|--------|-----------|------|--------|
| POST | `/api/register` | `{username, password}` | `{userId, username}` + cookie |
| POST | `/api/login` | `{username, password}` | `{userId, username}` + cookie |
| POST | `/api/logout` | — | `{ok: true}` |
| GET | `/api/me` | — | `{userId, username}` |

### Đoạn chat

| Method | Đường dẫn | Body | Trả về |
|--------|-----------|------|--------|
| GET | `/api/sessions` | — | `{sessions: [...]}` |
| POST | `/api/sessions` | `{title}` | `{id, title, messageCount}` |
| PATCH | `/api/sessions/{id}` | `{title}` | `{id, title}` |
| DELETE | `/api/sessions/{id}` | — | `{ok: true}` |

### Hội thoại

| Method | Đường dẫn | Body | Trả về |
|--------|-----------|------|--------|
| POST | `/api/chat` | `{message, sessionId?}` hoặc form-data | `{reply, sessionId, cached, remaining, attachment?}` |
| GET | `/uploads/{tên-file}` | — | Nội dung file (cần đăng nhập) |
| GET | `/api/history?sessionId=N` | — | `{messages: [...]}` |

> Bỏ trống `sessionId` khi gọi `/api/chat` → hệ thống tự tạo đoạn chat mới và
> trả về `newSession: true` kèm `title`.
>
> **Gửi kèm file:** dùng `multipart/form-data` với các trường `message`,
> `sessionId` và `file` thay cho JSON.

### Mã lỗi

| Mã | Ý nghĩa |
|----|---------|
| 400 | Dữ liệu gửi lên không hợp lệ |
| 401 | Chưa đăng nhập hoặc phiên hết hạn |
| 404 | Không tìm thấy — **hoặc không có quyền truy cập** |
| 409 | Tên đăng nhập đã tồn tại |
| 429 | Gửi quá nhanh, vượt rate limit |
| 500 | Lỗi phía máy chủ |

---

## 9. Luồng hoạt động

### Khi người dùng gửi một tin nhắn

```
Trình duyệt: POST /api/chat {message, sessionId}
      |
      v
ChatHandler
      |
      ├─ 1. Http.currentUserId()  -> chưa đăng nhập? trả 401
      |
      ├─ 2. ChatCache.allowRequest()  -> vượt giới hạn? trả 429
      |
      ├─ 3. Chưa có sessionId?
      |        -> SessionDAO.create() với tiêu đề lấy từ câu hỏi
      |      Có rồi?
      |        -> SessionDAO.belongsTo() kiểm tra quyền, sai thì trả 404
      |
      ├─ 4. MessageDAO.getHistory()   [Redis -> MySQL nếu cache trống]
      |        -> lấy 20 tin gần nhất
      |
      ├─ 5. MessageDAO.saveMessage()  lưu câu hỏi, xoá cache lịch sử
      |
      ├─ 6. Là câu hỏi đầu tiên?
      |        -> ChatCache.getReply()  thử lấy câu trả lời cũ
      |
      ├─ 7. Không có trong cache?
      |        -> GeminiService.askGemini()   [gọi API, ~3-4 giây]
      |        -> ChatCache.putReply()        lưu lại cho lần sau
      |
      ├─ 8. MessageDAO.saveMessage()  lưu câu trả lời
      |
      └─ 9. SessionDAO.touch()  đưa đoạn chat lên đầu danh sách
                |
                v
      {reply, sessionId, cached, remaining}
```

### Khi Redis gặp sự cố

| Chức năng | Khi Redis chết |
|-----------|----------------|
| Đăng nhập | Vẫn chạy (dùng bộ nhớ, mất phiên khi restart) |
| Lịch sử chat | Vẫn chạy (đọc thẳng MySQL, chậm hơn) |
| Cache câu trả lời | Không có, luôn gọi API thật |
| Rate limit | Không giới hạn |

**Toàn bộ chức năng chính vẫn hoạt động.** Đây là điều đã được kiểm chứng bằng
cách tắt hẳn container Redis rồi chạy lại đủ luồng.

---

## 10. Quy ước khi viết code

### Đặt tên

| Loại | Quy ước | Ví dụ |
|------|---------|-------|
| Lớp | PascalCase | `SessionHandler` |
| Hàm, biến | camelCase | `getUserId`, `sessionId` |
| Hằng số | UPPER_SNAKE | `CONTEXT_SIZE` |
| Bảng, cột SQL | snake_case | `user_id`, `created_at` |

### Những điều bắt buộc

1. **Luôn dùng `PreparedStatement`**, không nối chuỗi SQL — tránh SQL injection:
   ```java
   // ĐÚNG
   stmt = conn.prepareStatement("SELECT * FROM users WHERE id = ?");
   stmt.setInt(1, userId);

   // SAI - có thể bị tấn công
   stmt.executeQuery("SELECT * FROM users WHERE id = " + userId);
   ```

2. **Luôn dùng try-with-resources** để đóng kết nối:
   ```java
   try (Connection conn = DBConnection.getConnection();
        PreparedStatement stmt = conn.prepareStatement(sql)) {
       // ...
   }   // tự đóng, kể cả khi có lỗi
   ```

3. **Kiểm tra quyền sở hữu trước mọi thao tác** lên đoạn chat:
   ```java
   if (!sessionDAO.belongsTo(sessionId, userId)) {
       Http.sendError(ex, 404, "Khong tim thay doan chat");
       return;
   }
   ```

4. **Bên JavaScript dùng `textContent`, không dùng `innerHTML`** khi hiển thị
   nội dung do người dùng hoặc AI tạo ra — tránh XSS:
   ```javascript
   bubble.textContent = content;    // ĐÚNG
   bubble.innerHTML = content;      // SAI
   ```

5. **Thêm biến cấu hình mới** → cập nhật cả 3 nơi: `.env`, `.env.example`,
   và phần environment trong `docker-compose.yml`.

6. **Tên bảng trong SQL luôn viết thường** (`users`, không phải `USERS`).
   MySQL trên Linux (tức là trong Docker) **phân biệt hoa/thường** tên bảng,
   trong khi trên Windows thì không. Viết hoa sẽ chạy tốt trên máy bạn nhưng
   hỏng khi deploy — đây là lỗi thật đã xảy ra trong dự án.

### Comment

Viết tiếng Việt không dấu trong file `.java` (tránh lỗi font trên console
Windows), tiếng Việt có dấu trong file `.js`/`.css`/`.html`.

Comment giải thích **tại sao**, không mô tả lại code:

```java
// ĐÚNG - giải thích lý do
// Lay ngu canh truoc khi luu cau hoi moi, de khong gui trung chinh no
List<Message> history = messageDAO.getHistory(sessionId);

// KHÔNG CẦN - code đã tự nói
// Lay lich su
List<Message> history = messageDAO.getHistory(sessionId);
```

---

## 11. Gỡ lỗi thường gặp

### `Unknown database 'chatbot_db'`

Chưa tạo database:
```sql
CREATE DATABASE chatbot_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### `Communications link failure`

MySQL chưa chạy, hoặc sai cổng trong `DB_URL`. Kiểm tra:
```bash
docker compose ps          # nếu chạy bằng Docker
netstat -ano | grep 3306   # nếu chạy trực tiếp
```

### `[Redis] Khong ket noi duoc`

Redis chưa chạy. Ứng dụng vẫn hoạt động nhưng không có cache:
```bash
docker compose up -d redis
```

### `UnsupportedClassVersionError`

Lệnh `java` trên PATH cũ hơn bản Maven dùng để build. Dùng:
```bash
"$JAVA_HOME/bin/java" -jar target/chatbot-1.0-SNAPSHOT.jar
```

### `Loi: Chua cau hinh GEMINI_API_KEY`

Chưa dán API key vào `.env`, hoặc còn để giá trị mẫu.
Lấy key tại <https://aistudio.google.com/apikey>.

### Tiếng Việt hiển thị thành `?????`

- Trong MySQL: bảng phải là `utf8mb4`, và `DB_URL` phải có `characterEncoding=UTF-8`
  (**không phải** `utf8mb4` — đây là tên charset phía Java, không phải phía MySQL)
- Trên console Windows: chạy `chcp 65001` trước

### `Table 'chatbot_db.USERS' doesn't exist` (chỉ gặp trong Docker)

Câu SQL viết hoa tên bảng. MySQL trên Linux phân biệt hoa/thường — phải dùng
`users`, `sessions`, `messages` viết thường. Xem mục [Quy ước](#10-quy-ước-khi-viết-code).

### `Communications link failure` khi chạy `docker compose up` lần đầu

Ứng dụng khởi động trước khi MySQL kịp sẵn sàng. Đã xử lý bằng hai cách:

- `docker-compose.yml` dùng healthcheck chạy `SELECT 1` thay vì `mysqladmin ping`
  (ping trả OK ngay cả khi MySQL còn đang khởi tạo)
- `DBConnection.initTables()` tự thử lại 10 lần, mỗi lần cách 3 giây

Nếu vẫn gặp, tăng `DB_INIT_RETRIES` trong `.env`.

### Đổi mã nguồn mà không thấy thay đổi

File giao diện được đóng gói vào jar. Phải build lại:
```bash
mvn clean package
```

### Build lỗi `Failed to delete ...jar`

Server đang chạy và giữ file. Tắt trước:
```bash
taskkill /F /IM java.exe     # Windows
```

---

## 12. Hạn chế và hướng phát triển

### Hạn chế hiện tại

| Vấn đề | Ảnh hưởng | Hướng khắc phục |
|--------|-----------|-----------------|
| Mật khẩu SHA-256 không salt | Yếu trước tấn công rainbow table | Chuyển sang BCrypt |
| Chạy HTTP, không HTTPS | Dữ liệu truyền đi không mã hoá | Đặt sau nginx có SSL |
| Không phân trang lịch sử | Đoạn chat rất dài sẽ tải chậm | Thêm `LIMIT` / `OFFSET` |
| Không có test tự động | Sửa code dễ làm hỏng chỗ khác | Thêm JUnit |
| Chưa tô màu cú pháp trong khối code | Code hiển thị một màu | Thêm thư viện highlight |
| File tải lên không bị dọn | Thư mục `uploads/` phình dần | Thêm tác vụ xoá file của đoạn chat đã xoá |

### Có thể làm tiếp

- Hiển thị câu trả lời theo kiểu gõ dần (streaming)
- Xuất đoạn chat ra file PDF / Markdown
- Tìm kiếm trong lịch sử hội thoại
- Chế độ sáng / tối

---

## Liên hệ

Có chỗ nào trong tài liệu chưa rõ, hoặc phát hiện thông tin đã lỗi thời,
hãy tạo issue trên repository hoặc nhắn trong nhóm.
