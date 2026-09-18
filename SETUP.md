# Hướng dẫn khởi động repo

## A. TV1 — dựng repo

1. Tạo repo GitHub `smart-emobility-hub`, đẩy toàn bộ khung này lên nhánh `main`.
2. Settings → Collaborators: mời 7 thành viên còn lại.
3. Tạo `frontend/package-lock.json` rồi commit:
   ```bash
   cd frontend && npm install
   git add package-lock.json && git commit -m "chore: them package-lock.json"
   ```
4. Chạy thử toàn bộ theo mục B, rồi nhờ một người khác chạy lại trên máy họ.
5. **Tuần 1 chưa cần đặt ruleset** — đang làm một mình, đặt luật chỉ tự chặn chính mình.
   Sang tuần 2, khi cả nhóm đã vào repo, mới tạo theo mục A3.

## A2. Những chỗ BẮT BUỘC phải điền trước khi dùng

| Chỗ | Điền gì | Ai |
|---|---|---|
| `.github/CODEOWNERS` | Thay `@tv1 … @tv8` bằng username GitHub thật (tuần 2, khi bật ruleset) | TV1 |
| `README.md`, bảng module | Thay TV1 … TV8 bằng tên thật nếu muốn | TV1 |
| `SETUP.md` mục B, `git clone <url-repo>` | Đường dẫn repo thật sau khi tạo | TV1 |
| `.env` | Copy từ `.env.example`; đổi mật khẩu CSDL nếu muốn | Mỗi người |

Ngoài bốn chỗ trên, **không có giá trị giả nào khác trong repo**: mật khẩu tài khoản demo
được mã hóa lúc chạy bởi `shared/auth/DemoUserInitializer`, không có chuỗi hash đặt cứng.

## A3. Ruleset bảo vệ nhánh `main` (làm ở tuần 2)

Settings → Rules → Rulesets → New branch ruleset:

- **Ruleset Name:** `main-protection`, **Enforcement status:** Active
- **Bypass list:** Add bypass → Roles → **Repository admin**
  *(làm bước này trước, để chủ repo không tự khóa chính mình khi gấp)*
- **Target branches:** Add target → Include default branch
- **Rules** — tick 4 mục:
  - Restrict deletions
  - Block force pushes
  - Require a pull request before merging → Required approvals = **1**, tick Require review from Code Owners
  - Require status checks to pass → chọn `backend` và `frontend`

Hai lưu ý: `backend` và `frontend` chỉ hiện trong danh sách **sau khi CI đã chạy ít nhất một lần**;
và Code Owners chỉ có tác dụng khi `.github/CODEOWNERS` đã điền username thật.

Các mục còn lại (linear history, signed commits, code scanning, code quality, code coverage)
không cần cho bài tập lớn.

## B. Mọi thành viên — chạy lần đầu

```bash
git clone <url-repo> && cd smart-emobility-hub
cp .env.example .env

docker compose up -d db            # PostgreSQL 16 tại cổng 5432

cd backend && mvn spring-boot:run
# http://localhost:8080/swagger-ui.html  → xem toàn bộ API
# Không có lệnh mvn? Mở thư mục backend bằng IntelliJ rồi bấm ▶ ở EmobilityApplication.java

cd ../frontend && npm install && npm run dev
# http://localhost:5173   → đăng nhập student1 / 123456
```

Kiểm tra nhanh: `java -version` (21), `node -v` (20), `docker -v`.
Chưa cài được gì thì xem [INSTALL.md](INSTALL.md).

## C. Quy trình làm việc hằng ngày

```bash
git checkout main && git pull
git checkout -b feature/m5-charging-queue

# ... code trong package/thư mục của mình ...

# Chạy test trước khi push: IntelliJ → chuột phải src/test → Run Tests
# hoặc: cd backend && mvn test
git add . && git commit -m "[M5] them hang doi uu tien sac"
git push -u origin feature/m5-charging-queue
```

Mở pull request, điền checklist, chờ TV1 duyệt và CI xanh rồi merge.

## D. Thêm một module mới — làm theo M3

M3 (`m3vehicle`) là module mẫu hoàn chỉnh. Copy cấu trúc của nó:

| Thành phần | File mẫu trong M3 |
|---|---|
| Enum trạng thái | `domain/VehicleState.java` — có hàm `canMoveTo` chặn chuyển trạng thái sai |
| Entity | `domain/RentalVehicle.java` — có `@Version` cho optimistic lock |
| Repository | `repository/RentalVehicleRepository.java` |
| Service | `service/VehicleService.java` — nghiệp vụ + phát sự kiện |
| Nhận sự kiện | `events/VehicleEventHandler.java` — có chống xử lý trùng và bắt lỗi |
| DTO | `dto/ReturnVehicleRequest.java`, `dto/VehicleResponse.java` |
| Controller | `controller/VehicleController.java` — đường dẫn `/api/m3/...` |
| Test | `src/test/.../VehicleStateTest.java` — state transition testing |
| Trang React | `frontend/src/modules/m3vehicle/` — `api/` và `pages/` |

Thêm route mới vào `frontend/src/shared/router.tsx` và mục menu trong `AppLayout.tsx`
— chỉ thêm dòng của mình, không sửa dòng của người khác.

## E. Ba quy tắc không được phá

1. **Không import service của module khác.** Cần module khác phản ứng thì phát sự kiện
   trong `shared/events`; bên nhận tự viết handler trong package của họ.
   Ngoại lệ duy nhất: M4 gọi interface public `ReservationCore` của M2.
2. **Không sửa `shared/` và `db/migration`.** `shared/` là của TV3, migration là của TV4.
   Cần thay đổi thì mở issue hoặc nhắn, đừng tự sửa.
3. **Không đổi `ddl-auto`.** Để `validate`; schema chỉ thay đổi qua file Flyway của TV4,
   nhờ vậy ERD trong report luôn khớp cơ sở dữ liệu thật.

## F. Việc còn thiếu, chia cho ai

Khung này đã chạy được: đăng nhập, phân quyền, sự kiện, WebSocket, Swagger, module mẫu M3.
Những phần dưới đây là nghiệp vụ, chưa làm:

| Việc | Ai | Khi nào |
|---|---|---|
| Migration V3+ cho bảng của từng module (theo ERD) | TV4 nhận yêu cầu từ chủ module | Tuần 5 |
| Bộ giả lập IoT + phát `ResourceStateUpdatedEvent` | TV6 | Tuần 4–5 |
| Interface public `ReservationCore` cho M4 dùng | TV2 | Tuần 5 |
| Design system đầy đủ (token, component chung) | TV5 | Tuần 4 |
| Gửi STOMP từ backend cho dashboard | TV1 | Tuần 5–6 |