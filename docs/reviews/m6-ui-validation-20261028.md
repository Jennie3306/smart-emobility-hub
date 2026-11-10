# M6 UI — implementation và verification ngày 28/10/2026

Đã triển khai `/m6/operations` trên `feature/m6-iot-simulator-event-bus`, HEAD
`5583c1c`, working tree chưa commit (gồm D07 domain từ lượt trước). Simulator
dùng API thật. Incident là UI fixture riêng, không có Incident service/API/DB.
Code mới chỉ ở feature worktree; tài liệu và evidence được đồng bộ về checkout
tích hợp. Không merge thêm nhánh hoặc sửa code M3/shared backend trong lượt này.

## Hành vi đã triển khai

- GET `/api/m6/simulator/status`: trạng thái run, scheduler read-only, interval
  null, latestRun null, loading/error/retry. Khi GET lỗi, chặn chạy trên trạng thái
  chưa xác nhận và ghi rõ dữ liệu cũ có thể stale. Không tự bật scheduler/polling.
- POST `/api/m6/simulator/scenarios/{scenario}`: chỉ `NORMAL_TICK`,
  `VEHICLE_FAILURE`, `CHARGER_FAILURE`; body chứa numeric Long seed. UI giữ
  nguyên Long bằng chuỗi trong view model và numeric literal trong JSON request,
  kể cả ngoài phạm vi số nguyên an toàn của JavaScript. Không thêm tick count.
- Hiển thị run ID, scenario/seed, start/end, reading/processed/duplicate/rejected
  counts, error và outbound ID/type/resource theo đúng DTO. Đây là output một
  run, không phải event stream toàn hệ thống. Không bịa reason/hub/state riêng
  cho từng outbound entry khi response không cung cấp.
- 400: giữ input, chỉ lỗi và details; 401: shared Axios client chuyển `/login`;
  403: giải thích yêu cầu Operator và link login; 409: báo bận, GET lại trạng thái,
  không tự retry POST. Chặn double click khi POST đang pending. 5xx/network có
  thông báo và thử lấy trạng thái run gần nhất; không hiển thị thành công giả.
- Incident fixture: năm trạng thái D07, bộ lọc status/type/query hoạt động,
  detail, assignee mẫu, history chỉ trong trang, Reopen ở Resolved và Closed
  terminal; reset/reload đặt lại. Bản mẫu report chỉ preview, không thêm row hoặc
  gửi request. Severity luôn ghi Chờ TV2; không chọn vocabulary/default/mapping,
  không quyết định duplicate behavior, không ghi version/timestamp hoặc health.

Các file chính ở `frontend/src/modules/m6simulator/`: API adapter, page,
SimulatorPanel, IncidentFixturePanel, fixtures và CSS scoped. Route/nav chỉ thêm
M6; shared auth/client không thay đổi. M1/M3 routes được giữ nguyên. Dev dependencies
thêm Testing Library, user-event và jsdom; lockfile được cập nhật.

## Môi trường và commands

Java 21.0.10 / Maven 3.9.9, Node 22.21.0, React 18 / Vitest 2.1.9. Chạy ngày
28/10/2026 từ 08:32 (UTC+7). Production frontend được build lại qua
Dockerfile Node 20, phục vụ bằng nginx; backend image Java 21; PostgreSQL 16.
Các bước clean validation theo CI/demo runbook đã chạy: backend verify,
frontend npm ci + test + build, Compose config. Test command có 28 case thực,
không dùng no-test exit code làm proof.

```sh
# Trong frontend/
npm ci
npm run test -- --run
npm run build

# Trong backend/, dùng JDK 21
mvn -B verify

# Trong root feature checkout
docker compose -f docker-compose.yml -f docker-compose.demo.yml config
docker compose -p m6-ui-proof-20261028 -f docker-compose.yml \
  -f docker-compose.demo.yml \
  -f DESCRIPTION/Template_report/Evidence/M6_IoT/records/compose-override.yml up -d --build
```

[Override đã dùng](../../DESCRIPTION/Template_report/Evidence/M6_IoT/records/compose-override.yml) đặt container
DB riêng, cổng loopback DB 55438/backend 18089/frontend 18088, CORS cho frontend
đó và volume riêng theo project. Không dùng DB hoặc container dự án đang làm.
Tài khoản `operator1`/`student1` từ DemoUserInitializer; session cookie được trình
duyệt gửi qua nginx. Không lưu cookie vào evidence. Trước production smoke có
khởi động thử host Java/Vite trên cùng DB biệt lập; formal browser smoke dưới
đây dùng URL nginx `http://127.0.0.1:18088`.

## Kết quả và phạm vi proof

| Kiểm tra | Actual / kết quả | Evidence |
|---|---|---|
| Frontend component/API tests | **28 pass**: 12 seed/transport, 13 simulator interactions/errors, 3 Incident fixture flows | [Vitest log](../../DESCRIPTION/Template_report/Evidence/M6_IoT/records/frontend-tests-20261028.log) |
| Clean install / production build | npm ci exit 0; TypeScript + Vite build thành công | [Install](../../DESCRIPTION/Template_report/Evidence/M6_IoT/records/verification-excerpts-20261028.txt), [build](../../DESCRIPTION/Template_report/Evidence/M6_IoT/records/frontend-build-20261028.log) |
| Backend regression | **69 tests**, 0 failure/error/skip, BUILD SUCCESS; có ModuleBoundaryTest và 35 D07 tests | [Maven](../../DESCRIPTION/Template_report/Evidence/M6_IoT/records/backend-verify-20261028.log) |
| Compose | Config hợp lệ; ba service up, DB healthy; images build thành công | [Default config](../../DESCRIPTION/Template_report/Evidence/M6_IoT/records/verification-excerpts-20261028.txt), [build](../../DESCRIPTION/Template_report/Evidence/M6_IoT/records/verification-excerpts-20261028.txt), [ps](../../DESCRIPTION/Template_report/Evidence/M6_IoT/records/verification-excerpts-20261028.txt) |
| Live unauthenticated | Mở route M6 → GET status 401 → `/login` qua shared client | [Snapshot](../../DESCRIPTION/Template_report/Evidence/M6_IoT/records/verification-excerpts-20261028.txt) |
| Live Student | GET status 403 ACCESS_DENIED; UI hiển thị lỗi và chặn Run | [Snapshot](../../DESCRIPTION/Template_report/Evidence/M6_IoT/records/verification-excerpts-20261028.txt), [ảnh](../../DESCRIPTION/Template_report/Evidence/M6_IoT/screenshots/ui/02-student-forbidden.png) |
| Live Operator / NORMAL_TICK | Numeric seed `9223372036854775807` chính xác cả request/backend log và UI. 3 readings processed, 0 duplicate/reject, 3 ResourceStateUpdatedEvent | [Snapshot](../../DESCRIPTION/Template_report/Evidence/M6_IoT/records/verification-excerpts-20261028.txt), [runtime](../../DESCRIPTION/Template_report/Evidence/M6_IoT/records/verification-excerpts-20261028.txt) |
| Live VEHICLE_FAILURE | 1 reading processed, VehicleFailedEvent; M3 consumer đổi xe ID 1 AVAILABLE/version 0 → MAINTENANCE/version 1 trong DB biệt lập | [Snapshot](../../DESCRIPTION/Template_report/Evidence/M6_IoT/records/verification-excerpts-20261028.txt), [SQL trước](../../DESCRIPTION/Template_report/Evidence/M6_IoT/records/verification-excerpts-20261028.txt), [sau](../../DESCRIPTION/Template_report/Evidence/M6_IoT/records/verification-excerpts-20261028.txt) |
| Live CHARGER_FAILURE | 1 reading processed; ResourceStateUpdatedEvent + ChargerFailedEvent. Không có proof phản ứng runtime M4/M5 | [Snapshot](../../DESCRIPTION/Template_report/Evidence/M6_IoT/records/verification-excerpts-20261028.txt), [ảnh](../../DESCRIPTION/Template_report/Evidence/M6_IoT/screenshots/ui/05-simulator-live.png) |
| Live API payload sai | Browser request cùng session gửi seed null: 400 VALIDATION_FAILED, latestRun ID không đổi. UI bình thường chặn seed sai trước POST | [Request/result](../../DESCRIPTION/Template_report/Evidence/M6_IoT/records/verification-excerpts-20261028.txt) |
| UI HTTP 400 / 409 | Component tests và browser route injection kiểm tra hiển thị lỗi; 409 có GET refresh, không retry POST. **Không phải proof race 409 trên backend thật** | [400 injected](../../DESCRIPTION/Template_report/Evidence/M6_IoT/records/verification-excerpts-20261028.txt), [409 injected](../../DESCRIPTION/Template_report/Evidence/M6_IoT/records/verification-excerpts-20261028.txt), [ảnh 409](../../DESCRIPTION/Template_report/Evidence/M6_IoT/screenshots/ui/12-injected-409.png) |
| Fixture filters / reopen | Closed filter chỉ 1/5 row; FIX-004 Resolved → InProgress qua Reopen; chưa lưu/phát event. Toàn chuỗi, terminal/reset và draft không request còn có component tests | [Filter](../../DESCRIPTION/Template_report/Evidence/M6_IoT/records/verification-excerpts-20261028.txt), [Reopen](../../DESCRIPTION/Template_report/Evidence/M6_IoT/records/verification-excerpts-20261028.txt), [ảnh](../../DESCRIPTION/Template_report/Evidence/M6_IoT/screenshots/ui/08-incident-fixture.png) |
| Responsive | Xem desktop 1440×1000 và mobile 390×844; panel xếp dọc, bảng fixture cuộn ngang trong vùng riêng; body không overflow ngang | [Simulator mobile](../../DESCRIPTION/Template_report/Evidence/M6_IoT/screenshots/ui/09-mobile-simulator.png), [fixture mobile](../../DESCRIPTION/Template_report/Evidence/M6_IoT/screenshots/ui/10-mobile-fixture.png), [width simulator](../../DESCRIPTION/Template_report/Evidence/M6_IoT/records/verification-excerpts-20261028.txt), [width fixture](../../DESCRIPTION/Template_report/Evidence/M6_IoT/records/verification-excerpts-20261028.txt) |

[Network](../../DESCRIPTION/Template_report/Evidence/M6_IoT/records/verification-excerpts-20261028.txt) ghi ba POST 200 và
các GET 200; hai POST cuối 400/409 là route injection đã nêu, không đến backend.
[Console](../../DESCRIPTION/Template_report/Evidence/M6_IoT/records/verification-excerpts-20261028.txt) tại đoạn kiểm tra lỗi
chỉ ghi hai HTTP error tương ứng; không có uncaught React error trong đoạn đó.
[Schema](../../DESCRIPTION/Template_report/Evidence/M6_IoT/records/verification-excerpts-20261028.txt) chỉ có Flyway V1/V2 hiện hữu,
không có Incident hoặc M6 latest-state table. M6 state vẫn memory-only.

Đã rà component theo React checklist: state cập nhật bất biến, effect GET có
abort/cleanup và chống response cũ, POST không đặt trong effect, không có
unhandled rejection; dùng label/button/table semantics, focus-visible, responsive.
M6 route lazy-load riêng, không thêm UI framework hoặc đổi auth policy.

Sau smoke đã đóng browser riêng, dừng host processes của lượt này và chạy
`down --volumes --rmi local` **chỉ cho project m6-ui-proof-20261028**.
[Cleanup log](../../DESCRIPTION/Template_report/Evidence/M6_IoT/records/verification-excerpts-20261028.txt) ghi kết quả xóa tài nguyên.

## Việc chưa hoàn tất

Incident API/JPA/migration, durable latest-state, concurrency version, atomic
rollback/after-commit, severity/mapping/active duplicate và health recovery vẫn
chờ phần triển khai/quyết định tương ứng. Live backend contention 409 chưa được
tái hiện ở lượt này; UI xử lý 409 đã có component/browser injected proof.
M2/M4/M5/M8 runtime và bản ghép các nhánh toàn nhóm chưa được kiểm thử ở đây.

24 case lịch sử giữ nguyên. TC-M6-25–63 vẫn **Not run**: fixture/filter/lifecycle
UI không thay thế oracle Incident API/DB, và database thay đổi ở M3 không chứng
minh M6 latest-state persistence. Sau khi có implementation và migration, chạy
đúng từng case rồi bổ sung actual/P-F; không tự chuyển Pass từ evidence một phần.

## Ghi nhận sau phản hồi — 29/10/2026

Đây là biên bản lịch sử của revision/ngày chạy ghi ở đầu, không phải một lượt
verify mới. TV2 đã chốt severity/mapping/BR-M6-06 sau snapshot code/evidence này;
TV3 chấp nhận D11 kiến trúc và payload đủ cho M3, không duyệt thay TV4 schema
hoặc TV3 race mechanism mới. Xem [quyết định và provenance](../../DESCRIPTION/Template_report/Evidence/M6_IoT/README.md).
Các câu “chờ TV2” trong finding/ảnh mô tả baseline lúc review; code cần cập nhật
ở lượt sau. Evidence đã chuyển sang bộ tuyển chọn; raw output vẫn giữ local.

## Implementation follow-up — 29/10/2026

Hai gap sau phản hồi đã sửa trong code feature: adapter MANUAL_REPORT bị reject
(7edb551), UI fixture dùng bốn severity và chọn bắt buộc không default (b156344).
Fresh backend verify 71 + frontend 32 tests pass; xem [evidence mới](../../DESCRIPTION/Template_report/Evidence/M6_IoT/README.md).
Biên bản/ảnh phía trên vẫn mô tả đúng baseline lịch sử; fixture UI và GET status
stub của lượt 29/10 không được coi là proof Incident API/DB hoặc mapping đầy đủ.
