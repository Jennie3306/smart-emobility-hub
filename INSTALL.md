# Cài đặt môi trường

Làm một lần ở tuần 1. Mất khoảng 30–45 phút tùy tốc độ mạng.
Ai kẹt bước nào thì chụp màn hình lỗi gửi vào nhóm, đừng tự mò lâu.

## Cần cài những gì

| Phần mềm | Phiên bản | Dùng để làm gì | Bắt buộc |
|---|---|---|---|
| JDK (Java) | **21** | Chạy backend | Cả nhóm |
| IntelliJ IDEA Community | mới nhất | Viết và chạy code Java | Cả nhóm |
| Node.js | **20 LTS** | Chạy frontend | Cả nhóm |
| Git | mới nhất | Làm việc với repo | Cả nhóm |
| Docker Desktop | mới nhất | Chạy PostgreSQL | Cả nhóm |
| VS Code | mới nhất | Viết frontend (dễ hơn IntelliJ) | Ai làm frontend |
| DBeaver hoặc pgAdmin | mới nhất | Xem dữ liệu trong CSDL | TV4 |

**Không cần cài Maven riêng.** IntelliJ đã có Maven bên trong. Ai thích gõ lệnh ngoài terminal
thì cài thêm bằng `winget install Apache.Maven` (Windows) hoặc `brew install maven` (macOS).

---

## Windows

Cách nhanh nhất là dùng winget, mở **PowerShell** rồi chạy từng dòng:

```powershell
winget install EclipseAdoptium.Temurin.21.JDK
winget install JetBrains.IntelliJIDEA.Community
winget install OpenJS.NodeJS.LTS
winget install Git.Git
winget install Docker.DockerDesktop
winget install Microsoft.VisualStudioCode
```

**Đóng PowerShell và mở lại** sau khi cài xong, nếu không lệnh mới sẽ chưa nhận.

Nếu winget báo không tìm thấy gói, tải thủ công:
- JDK 21: adoptium.net (chọn Temurin 21, gói `.msi`, nhớ tick *Set JAVA_HOME*)
- IntelliJ IDEA **Community** (bản miễn phí): jetbrains.com/idea/download — kéo xuống phần Community Edition
- Node.js 20 LTS: nodejs.org
- Git: git-scm.com
- Docker Desktop: docker.com/products/docker-desktop

Docker Desktop trên Windows cần bật WSL 2. Cài xong nó thường tự hỏi và tự bật;
nếu báo lỗi thì mở PowerShell với quyền Admin và chạy `wsl --install`, rồi khởi động lại máy.

## macOS

```bash
brew install --cask temurin@21
brew install --cask intellij-idea-ce
brew install node@20
brew install git
brew install --cask docker
brew install --cask visual-studio-code
```

Mở Docker Desktop một lần để nó chạy nền.

## Linux (Ubuntu/Debian)

```bash
sudo apt update
sudo apt install -y openjdk-21-jdk git
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt install -y nodejs
# Docker: làm theo hướng dẫn tại docs.docker.com/engine/install/ubuntu/
sudo usermod -aG docker $USER   # rồi đăng xuất và đăng nhập lại
```

IntelliJ IDEA Community tải tại jetbrains.com hoặc cài bằng `sudo snap install intellij-idea-community --classic`.

---

## Kiểm tra sau khi cài

Mở terminal mới và chạy:

```bash
java -version     # phai hien 21.x
node -v           # phai hien v20.x
npm -v            # 10.x
git --version
docker -v
```

Nếu `java -version` hiện phiên bản khác 21, kiểm tra biến môi trường `JAVA_HOME`.
Trên Windows: Start → gõ "environment variables" → Environment Variables → sửa `JAVA_HOME`
trỏ tới thư mục JDK 21, ví dụ `C:\Program Files\Eclipse Adoptium\jdk-21...`.

---

## Chạy dự án lần đầu

```bash
git clone <url-repo>
cd smart-emobility-hub
cp .env.example .env          # Windows PowerShell: copy .env.example .env
```

**Bước 1 — CSDL.** Mở Docker Desktop trước, rồi:
```bash
docker compose up -d db
docker ps                      # thay bang emobility-db dang chay
```

**Bước 2 — Backend.** Cách dễ nhất:
1. Mở IntelliJ → **Open** → chọn thư mục `backend` (chọn đúng thư mục `backend`, không phải thư mục gốc)
2. Đợi IntelliJ tải dependency — lần đầu 3–10 phút, xem thanh tiến trình dưới đáy
3. Mở `src/main/java/com/hcmut/emobility/EmobilityApplication.java`, bấm nút ▶ xanh cạnh hàm `main`
4. Console hiện `Started EmobilityApplication` là xong

Kiểm tra: mở http://localhost:8080/swagger-ui.html — thấy danh sách API là backend đã chạy.

Ai quen terminal thì dùng `cd backend && mvn spring-boot:run` (cần cài Maven).

**Bước 3 — Frontend.**
```bash
cd frontend
npm install        # lần đầu 2–5 phút
npm run dev
```
Mở http://localhost:5173, đăng nhập `student1` / `123456`.

**Về `backend/Dockerfile` và `frontend/Dockerfile`:** hai file này chỉ dùng cho môi trường demo
ở cuối kỳ (xem SETUP.md mục D2). Lúc đang code thì không đụng tới, cứ chạy backend bằng IntelliJ
và frontend bằng `npm run dev`.

---

## Lỗi hay gặp

| Lỗi | Nguyên nhân | Cách sửa |
|---|---|---|
| `mvn : The term 'mvn' is not recognized` | Chưa cài Maven | Chạy backend bằng IntelliJ, hoặc `winget install Apache.Maven` rồi mở lại terminal |
| `Connection refused: localhost:5432` | CSDL chưa chạy | Mở Docker Desktop, rồi `docker compose up -d db` |
| `FATAL: invalid value for parameter "TimeZone": "Asia/Saigon"` | Windows tiếng Việt đặt múi giờ JVM là tên cũ, PostgreSQL không nhận | Đã xử lý sẵn trong `EmobilityApplication.main()`. Nếu vẫn gặp, kiểm tra dòng `TimeZone.setDefault(...)` còn nguyên không |
| `Port 8080 is already in use` | Cổng đang bị chiếm | Tắt tiến trình cũ, hoặc đổi `server.port` trong `application.yml` |
| `Port 5432 is already in use` | Máy đã cài sẵn PostgreSQL | Đổi cổng trong `.env` (`POSTGRES_PORT=5433`) và trong `application.yml` |
| `Schema validation: missing table` | Chưa chạy migration | Xóa container CSDL rồi tạo lại: `docker compose down -v && docker compose up -d db` |
| `401 Unauthorized` sau khi đăng nhập | Thiếu cookie phiên | Kiểm tra `withCredentials: true` trong `frontend/src/shared/api/client.ts` |
| Mở Swagger bị đá về trang "Please sign in" | `/swagger-ui.html` chưa được cho qua Security | Kiểm tra `SecurityConfig` có dòng `.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()` |
| Lỗi 401/403 trả về trang HTML "Whitelabel Error Page" thay vì JSON | Lỗi từ bộ lọc Security không đi qua `GlobalExceptionHandler` | Kiểm tra `SecurityConfig` có khối `.exceptionHandling(...)` ghi JSON |
| Chạy bằng nút ▶ không thấy log, chạy bằng Maven thì có | IntelliJ đẩy log sang cửa sổ Services | `Alt + 8` mở Services, bấm vào **dòng con** `EmobilityApplication` (không phải dòng cha `Application`). Hoặc **Window → Restore Default Layout** rồi chạy lại |
| `npm install` lỗi quyền trên Windows | Chạy trong thư mục OneDrive | Chuyển repo ra ngoài OneDrive, ví dụ `D:\261\CNPM\` |
| IntelliJ không nhận JDK 21 | Chưa chọn SDK | File → Project Structure → Project → SDK → chọn 21 |
| Docker Desktop không khởi động (Windows) | Chưa bật WSL 2 | PowerShell (Admin): `wsl --install`, khởi động lại máy |

## Khi không chạy được nữa, làm sạch rồi chạy lại

```bash
docker compose down -v          # xóa CSDL cũ và dữ liệu
docker compose up -d db         # tạo lại, Flyway sẽ chạy migration từ đầu

cd frontend
rm -rf node_modules             # Windows: rmdir /s /q node_modules
npm install
```