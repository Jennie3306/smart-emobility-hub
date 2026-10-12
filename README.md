# Smart E-Mobility Hub

Bài tập lớn Công nghệ Phần mềm — HK261. Hệ thống điều phối phương tiện điện, chỗ đỗ và cổng sạc
tại các Mobility Hub trong Khu đô thị ĐHQG-HCM, kèm chức năng What-if Simulation.

## Tech stack

| Lớp | Công nghệ |
|---|---|
| Backend | Java 21, Spring Boot 3.3 (Web, Data JPA, Security, Validation, WebSocket) |
| CSDL | PostgreSQL 16, migration bằng Flyway |
| Sự kiện giữa module | Spring Events (`ApplicationEventPublisher` + `@EventListener`) |
| Tác vụ định kỳ | Spring Scheduling (`@Scheduled`) |
| Frontend | React 18 + TypeScript + Vite, Tailwind CSS, React Router, Axios, Recharts |
| Realtime | STOMP over WebSocket (`@stomp/stompjs`) |
| Xác thực | Spring Security, session cookie (`JSESSIONID`) |
| Test | JUnit 5, Mockito, Spring Boot Test + MockMvc, Vitest |

## Module và người phụ trách

| Module | Nội dung | Phụ trách |
|---|---|---|
| M1 `m1monitoring` | Giám sát Hub, dashboard vận hành | TV1 |
| M2 `m2reservation` | Tìm & đặt xe, **Reservation core** | TV2 |
| M3 `m3vehicle` | Nhận/trả xe, vòng đời xe thuê | TV3 |
| M4 `m4parking` | Đặt chỗ đỗ (xe thuê & xe cá nhân) | TV4 |
| M5 `m5charging` | Lập lịch & ưu tiên sạc | TV5 |
| M6 `m6simulator` | Giả lập IoT, sự kiện, xử lý sự cố | TV6 |
| M7 `m7dispatch` | Điều chuyển xe giữa các Hub | TV7 |
| M8 `m8simulation` | What-if Simulation & khuyến nghị | TV8 |

`shared/` do TV3 sở hữu, `db/migration` do TV4 sở hữu — người khác không sửa.

## Chạy local

```bash
cp .env.example .env
docker compose up -d db                    # 1. CSDL (PostgreSQL)

cd backend && mvn spring-boot:run          # 2. Backend: http://localhost:8080
                                           #    Swagger: http://localhost:8080/swagger-ui.html

cd frontend && npm install && npm run dev  # 3. Frontend: http://localhost:5173
```

Không có lệnh `mvn` trong terminal cũng không sao — mở thư mục `backend` bằng IntelliJ IDEA
rồi bấm ▶ ở `EmobilityApplication.java`. Chi tiết cài đặt xem [INSTALL.md](INSTALL.md).

Tài khoản demo: `student1 / 123456` (STUDENT), `operator1 / 123456` (OPERATOR).

Chạy cả hệ thống bằng một lệnh (dùng để quay video demo, xem SETUP.md mục D2):

```bash
docker compose -f docker-compose.yml -f docker-compose.demo.yml up -d --build
```

## Quy ước

- Nhánh: `feature/m<so>-<mo-ta>`, ví dụ `feature/m5-charging-queue`
- Commit: `[M5] them hang doi uu tien sac`
- Merge vào `main` qua pull request, TV3 duyệt, CI phải xanh
- API: `/api/m<so>/<tai-nguyen>`, ngày giờ ISO-8601
- **Không import service của module khác** — giao tiếp qua Spring Events (`shared/events`),
  trừ M4 gọi interface public `ReservationCore` của M2
- Migration: chỉ TV4 tạo file `V<so>__<mo_ta>.sql`

## Tài liệu

- [INSTALL.md](INSTALL.md) — cài đặt môi trường.
- [SETUP.md](SETUP.md) — dựng repository và quy trình làm việc ban đầu.
- [Template M6](DESCRIPTION/Template_report/Template_M6.md) — báo cáo phân tích,
  thiết kế, bộ giả lập IoT và entity handoff của M6.
- [M6 DevOps & Tích hợp](docs/product/m6-devops-integration.md) — phạm vi và
  trách nhiệm của TV6.
- [CI và demo](docs/runbooks/ci-and-demo.md) — kiểm thử, chạy tích hợp và demo.
- [Các quyết định mở của M6](docs/proposals/m6-open-decisions.md) — các nội dung
  còn chờ chủ sở hữu module liên quan phê duyệt.
