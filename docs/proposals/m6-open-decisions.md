# Proposal: Các quyết định còn mở của M6

Ngày soạn: 2026-09-21
Trạng thái: **Đề xuất có một số quyết định đã được ghi nhận; các mục còn lại chưa phải yêu cầu/BR đã được chấp nhận**
Người chủ trì: **TV6 – M6, DevOps & Tích hợp**

Ngày 2026-10-20, TV6 ghi nhận phản hồi của TV3 (Hưng) cho M6-D03, D04, D05,
D06, D08 và D09. Đây là ngày ghi nhận trong tài liệu; ngày gửi phản hồi gốc
không được cung cấp. Các mục cần thêm phê duyệt bắt buộc hoặc xác nhận từ owner
liên quan vẫn được đánh dấu rõ bên dưới.

Ngày 2026-10-21, TV6 ghi nhận TV2 chấp thuận các phương án M6-D01, D02 và D07;
TV2 chấp thuận D08 với yêu cầu áp dụng quyền `OPERATOR` cho toàn bộ `/api/m6/**`,
chỉ ngoại lệ các API chỉ-đọc mà Student thực sự cần. Hiện chưa tìm thấy frontend
hoặc use case nào yêu cầu Student gọi API M6 chỉ-đọc, nên chưa có ngoại lệ cụ thể.
TV4 cũng chấp thuận proposal data model M6-D10; migration vẫn cần được triển
khai theo trách nhiệm owner.

Theo chỉ đạo TV6 ngày 2026-10-21, các proposal D04, D11 và D12 được chọn làm
implementation baseline của phía M6. Đây chưa phải xác nhận của các approver và
consumer được chỉ định trong bảng: contract liên module cần ghi nhận phản hồi của
TV3/TV4 và các consumer trước khi tuyên bố đã chốt toàn hệ thống.

## 1. Mục đích và cách phản hồi

Tài liệu ghi các đề xuất, phản hồi và trạng thái phê duyệt cho FR, BR, use case,
state machine, class diagram và code của M6. TV6 có thể chọn proposal làm
implementation baseline cho riêng M6; quyết định ảnh hưởng module khác chỉ được
coi là contract toàn hệ thống sau khi approver/consumer được chỉ định xác nhận.
Người nhận phản hồi bằng một trong ba dạng:

- `Đồng ý phương án đề xuất`;
- `Chọn phương án khác: ...`;
- `Chưa quyết định được vì cần: ...`.

TV6 cập nhật kết quả vào cột Quyết định cuối. TV6 có thể chuyển proposal đã chọn
thành baseline implement riêng phía M6; chỉ gọi đó là contract liên module/toàn
hệ thống sau khi approver và consumer được chỉ định xác nhận. Nếu các bên không
thống nhất, TV1 chốt phạm vi; TV3 chốt ranh giới kỹ thuật.

## 2. Bảng định tuyến quyết định

| ID | Chủ đề | TV6 chịu trách nhiệm | Phê duyệt bắt buộc | Tham vấn | Quyết định cuối |
| --- | --- | --- | --- | --- | --- |
| M6-D01 | Actor báo cáo sự cố | Soạn FR/UC | TV2 | TV1 | **TV2 chấp thuận proposal: Operator báo cáo thủ công; Sensor/Simulator dùng adapter nội bộ; Student không tạo Incident (ghi nhận 2026-10-21).** |
| M6-D02 | Chu kỳ simulator | Đề xuất cấu hình | TV2 | TV1, TV7 | **TV2 chấp thuận chu kỳ cấu hình 5.000 ms cho demo (ghi nhận 2026-10-21); `app.simulator.interval-ms=5000` đã cấu hình trên hai nhánh local, scheduler vẫn tắt mặc định.** |
| M6-D03 | Chế độ chạy simulator | Thiết kế kỹ thuật | TV3 | TV7 | **Đã chốt với TV3 (Hưng, ghi nhận 2026-10-20): một service dùng chung với Manual Adapter và Auto Adapter.** |
| M6-D04 | Điều kiện phát hiện hỏng | Soạn BR và fixture | TV3 | TV3/M3, TV5/M5 | **TV6 chọn proposal làm M6 implementation baseline (2026-10-21): low battery không phải failure; reason code được kiểm tra theo resource trên hai nhánh local. TV3 đã chốt phần low-battery/VehicleFailed; TV5/M5 xác nhận điều kiện và reaction ChargerFailed còn mở.** |
| M6-D05 | Ý nghĩa tự khóa tài nguyên | Đề xuất boundary | TV3 | TV2/M2, TV3/M3, TV4/M4, TV5/M5 | **TV3 chốt ranh giới: M6 chỉ phát event, không gọi UPDATE module khác; `VehicleFailed` để M3 khóa xe và M2 hủy Reservation tương ứng. Phản ứng ChargerFailed của M4/M5 chưa được TV3 xác nhận.** |
| M6-D06 | Vòng đời ChargingPoint | Soạn state table | TV3 | TV5/M5, TV7 | **TV3 chọn phương án C: M6 sở hữu health, M5 sở hữu allocation/session. Transition contract cần TV5/M5 phối hợp trước khi chốt state table.** |
| M6-D07 | Vòng đời Incident | Soạn state table | TV2 | TV3, TV7 | **TV2 chấp thuận `Reported → Assigned → InProgress → Resolved → Closed`; chỉ `Resolved → InProgress` được reopen; `Closed` terminal (ghi nhận 2026-10-21).** |
| M6-D08 | Quyền xử lý Incident | Soạn UC/authorization | TV2 và TV3 | TV1 | **TV2 chấp thuận cùng TV3: không thêm role kỹ thuật viên; khóa `/api/m6/**` cho `OPERATOR`, chỉ mở ngoại lệ API chỉ-đọc khi có nhu cầu Student cụ thể (ghi nhận 2026-10-21). Catch-all đã được triển khai trên nhánh tích hợp; chưa xác định endpoint ngoại lệ nào cần thiết.** |
| M6-D09 | Event/payload lỗi hoặc trùng | Thiết kế xử lý | TV3 | TV7 | **Đã chốt với TV3: dùng mã lỗi 400/404/409 toàn hệ thống; consumer dedupe theo `(consumerKey, eventId)` với key ổn định; transaction/error handling theo quyết định chi tiết bên dưới. Consumer/M3 code đã triển khai trên nhánh tích hợp local; chưa có trên nhánh feature M6.** |
| M6-D10 | Data model M6 | Soạn entity/quan hệ | TV4 | TV3 | **TV6 chọn proposal làm baseline; TV4 chấp thuận proposal (ghi nhận 2026-10-21). Flyway migration chưa được triển khai.** |
| M6-D11 | Lưu lịch sử telemetry | Đề xuất phạm vi | TV3 và TV4 | TV1, TV7 | **TV6 chọn latest-state-only + Incident, không lưu raw telemetry theo từng tick làm M6 implementation baseline (2026-10-21); xác nhận phạm vi từ TV3/TV4/TV1 còn mở. Schema addendum latest-state đã chuẩn bị để TV4 duyệt; chưa có entity/migration.** |
| M6-D12 | Mã lý do và payload event | Soạn contract | TV3 | M1, M2, M3, M4, M5, M8 | **TV6 chọn uppercase String reason codes và giữ payload event hiện có làm M6 implementation baseline (2026-10-21); reason-code validation đã thêm trên hai nhánh local; review TV3 và xác nhận đủ payload từ consumers còn mở.** |

## 3. Proposal chi tiết

### M6-D01 — Actor nào được báo cáo sự cố?

**Vấn đề:** Đề bài xác định Operator và Sensor/Simulator nhưng chưa nói sinh
viên có được tạo incident hay không.

**TV6 đề xuất:**

- Operator tạo báo cáo thủ công.
- Sensor/Simulator tạo báo cáo tự động qua adapter/service nội bộ, không gọi API
  HTTP dành cho Operator và không cần phiên đăng nhập người dùng.
- Student không tạo incident trong phiên bản hiện tại; chức năng phản ánh của
  sinh viên nằm ngoài phạm vi cho tới khi nhóm bổ sung requirement.

**TV2 chấp thuận (ghi nhận 2026-10-21):** Operator báo cáo thủ công;
Sensor/Simulator tạo Incident qua adapter/service nội bộ; Student không tạo
Incident trong phiên bản hiện tại. Nếu sau này thêm chức năng Student, cần một
requirement riêng nêu mục tiêu, quyền, use case và dữ liệu bắt buộc.

### M6-D02 — Chu kỳ simulator

**Vấn đề:** FR yêu cầu cập nhật định kỳ nhưng chưa có giá trị đo được.

**TV6 đề xuất:** cấu hình `app.simulator.interval-ms`; mặc định demo là **5.000
ms**. Test không chờ scheduler thật mà gọi một simulation tick trực tiếp.

**TV2 chấp thuận (ghi nhận 2026-10-21):** dùng cấu hình `app.simulator.interval-ms`
với chu kỳ demo **5.000 ms**; test gọi tick trực tiếp, không chờ đồng hồ thật.
Code hiện chưa khai báo interval này và scheduler vẫn tắt mặc định; đây là phần
cần triển khai, không phải quyết định còn mở.

### M6-D03 — Simulator chạy tự động hay thủ công?

**Vấn đề:** Chạy random liên tục khó tái hiện test và video; chỉ chạy thủ công
thì chưa thể hiện FR non-interactive.

**TV6 đề xuất:** hỗ trợ cả hai chế độ trên cùng simulation service:

- scheduled mode cho FR non-interactive;
- manual scenario/tick cho test và demo xác định;
- random luôn nhận seed cấu hình;
- scheduler tắt trong test, bật bằng cấu hình ở dev/demo.

**Quyết định TV3 (Hưng, ghi nhận 2026-10-20):** chấp nhận kiến trúc một
service dùng chung với `Manual Adapter` và `Auto Adapter`. Không thêm service
riêng hoặc message broker. TV7 vẫn được tham vấn về khả năng kiểm thử.

### M6-D04 — Khi nào phát VehicleFailed hoặc ChargerFailed?

**Vấn đề:** Pin thấp đã có `VehicleLowBattery`; không được biến pin thấp thành
hỏng xe. “Hỏng” hiện chưa có điều kiện rõ ràng.

**TV6 đề xuất:**

- `VehicleFailed`: chỉ từ manual incident hoặc simulator scenario mang lỗi thiết
  bị; không phát chỉ vì pin thấp.
- `ChargerFailed`: từ manual incident hoặc sensor/simulator báo thiết bị
  offline, quá nhiệt hoặc lỗi nguồn.
- Probability ngẫu nhiên là cấu hình demo, không phải BR nghiệp vụ; test dùng
  scenario xác định.

**Quyết định TV3 (Hưng, ghi nhận 2026-10-20):** pin thấp không phải
`VehicleFailed`; M6 reading cập nhật state và chỉ phát `ResourceStateUpdated`.
M3 phát `VehicleLowBattery` trong luồng trả xe khi pin dưới
`app.charging.low-battery-threshold` (giá trị cấu hình hiện tại là 20%).
`VehicleFailed` làm M3 chuyển `RentalVehicle` sang `MAINTENANCE`. M3 không xử
lý `ChargerFailed` vì phạm vi hiện tại của M3 là nhận/trả xe.

Phản hồi xác nhận tín hiệu `VehicleLowBattery`, không chốt thêm payload hay
thay đổi danh sách event outbound M6; ownership/payload liên module tiếp tục
theo contract M3 và M6-D12.

**Còn chờ TV5/M5:** xác nhận điều kiện/reason phát `ChargerFailed` và phản ứng
của M5 với phiên sạc. Không suy diễn các điều kiện trong proposal thành BR đã
được phê duyệt liên module. Theo chỉ đạo TV6 ngày 2026-10-21, M6 implement các
fixture/reason codes đã đề xuất: `OFFLINE`, `OVERHEAT`, `POWER_FAULT` và
`MANUAL_REPORT`; code kiểm tra allowlist theo resource, không tự thêm ngưỡng
hay hành vi khác. Việc tạo/persist Incident từ Operator hoặc failure reading
chờ TV2 chốt severity, severity mapping tự động và BR-M6-06.

### M6-D05 — “Tự khóa tài nguyên” nghĩa là gì?

**Vấn đề:** M6 không được sửa dữ liệu thuộc module khác, nhưng FR-M6-05 yêu cầu
tự khóa tài nguyên đang có sự cố.

**TV6 đề xuất:**

- M6 luôn tạo/cập nhật Incident và đánh dấu health của thiết bị M6 sở hữu.
- Với RentalVehicle, M6 phát `VehicleFailed`; M3 khóa bằng trạng thái Maintenance;
  M2 tự xử lý reservation.
- Với ChargingPoint, quyền sở hữu phải được chốt ở M6-D06; M6 phát
  `ChargerFailed`; M5 tự xử lý ChargingSession và M4 tự xử lý parking.
- M6 không gọi service hoặc ghi bảng của M2/M3/M4/M5.

**Quyết định TV3 (Hưng, ghi nhận 2026-10-20):** M6 không gọi UPDATE hoặc ghi
trực tiếp dữ liệu thuộc M2/M3/M4/M5. M6 phát `VehicleFailed`; M3 tự chuyển xe
sang `MAINTENANCE`, còn M2 tự hủy Reservation tương ứng.

Phản ứng của M4/M5 với `ChargerFailed` không được chốt trong phản hồi TV3 này;
phối hợp với các owner liên quan trước khi ghi nhận hành vi consumer đó là
contract đã xác nhận.

Trong code hiện tại, M3 handler vừa chuyển xe sang `MAINTENANCE` vừa gọi
`clearReservation()` trên `RentalVehicle`. Trước khi chỉnh handler theo ranh giới
mới, TV3/M3 cần xác nhận quan hệ giữa trường này và Reservation do M2 sở hữu để
tránh xử lý trùng hoặc xóa nhầm dữ liệu.

### M6-D06 — Vòng đời và owner của ChargingPoint

**Vấn đề:** Template giao state machine ChargingPoint cho M6, trong khi M5 dùng
cổng sạc để vận hành ChargingSession.

**State table dưới đây là proposal ban đầu, chưa được chốt và phải được sửa để
tách health state khỏi usage/allocation state theo phương án C:**

| Từ | Sự kiện | Sang | Owner hành động |
| --- | --- | --- | --- |
| Available | phiên sạc bắt đầu | InUse | Cần TV3/M5 chốt contract |
| InUse | phiên sạc kết thúc | Available | Cần TV3/M5 chốt contract |
| Available hoặc InUse | thiết bị hỏng | Faulty | M6 |
| Faulty | bắt đầu sửa | UnderRepair | M6 |
| UnderRepair | sửa thành công | Available | M6 |
| UnderRepair | sửa thất bại | Faulty | M6 |

Không cho phép `Faulty → Available` trực tiếp. Không cho cấp phát cổng ở Faulty
hoặc UnderRepair.

**Quyết định TV3 (Hưng, ghi nhận 2026-10-20):** M6 sở hữu device health;
M5 sở hữu charging allocation/session. Đây là phương án C và chốt ownership
split.

TV6 cập nhật report/state model để phân biệt health và usage. Hợp đồng để M5
thông báo bắt đầu/kết thúc sử dụng và các transition chi tiết vẫn cần phối hợp
với TV5/M5; TV7 được tham vấn về bộ kiểm thử state transition.

### M6-D07 — Vòng đời Incident

**TV6 đề xuất:**

```text
Reported → Assigned → InProgress → Resolved → Closed
                         ^             |
                         +--- Reopen --+
```

- Chỉ `Resolved → InProgress` được reopen.
- `Closed` là terminal, không reopen; cần tạo Incident mới và liên kết incident
  cũ nếu lỗi tái diễn.
- Không bỏ qua trạng thái trung gian, ngoại trừ auto-assign có thể thực hiện
  `Reported → Assigned` ngay trong cùng transaction.

**TV2 chấp thuận (ghi nhận 2026-10-21):** giữ lifecycle như đề xuất. `Closed` là
terminal; chỉ cho phép reopen từ `Resolved` về `InProgress`. State machine và
endpoint Incident vẫn chưa được triển khai.

### M6-D08 — Ai được chuyển trạng thái Incident?

**Hiện trạng:** SecurityConfig đã yêu cầu role `OPERATOR` cho
`/api/m6/incidents/**`; hệ thống hiện chỉ có STUDENT và OPERATOR.

**TV6 đề xuất:**

- Sensor/Simulator chỉ tạo Reported incident qua adapter nội bộ, không
  assign/resolve/close và không đi qua endpoint HTTP của Operator.
- Mọi OPERATOR được report, assign, start, resolve và close trong phạm vi bài tập.
- Không thêm role kỹ thuật viên ở phiên bản hiện tại.
- Student không truy cập API quản lý incident.

**TV3 (ghi nhận 2026-10-20) và TV2 (ghi nhận 2026-10-21) chấp thuận:** không
thêm role kỹ thuật viên. TV2 yêu cầu khóa toàn bộ `/api/m6/**` cho `OPERATOR`,
ngoại trừ endpoint chỉ-đọc mà Student thực sự cần. Hiện chưa thấy frontend hoặc
use case Student gọi API M6, nên chưa khai báo ngoại lệ; nếu xuất hiện nhu cầu,
phải xác định endpoint cụ thể trước khi mở quyền. Sensor/Simulator tiếp tục tạo
Incident qua adapter nội bộ, không qua endpoint Operator.

### M6-D09 — Payload sai, resource không tồn tại, event trùng

**TV6 đề xuất:**

| Tình huống | Kết quả |
| --- | --- |
| Request API thiếu/sai trường | HTTP 400, không tạo Incident |
| Resource ID không tồn tại khi gọi API | HTTP 404 |
| Chuyển trạng thái Incident sai | HTTP 409 |
| Cùng `eventId` được xử lý lại | No-op, ghi structured log |
| Sensor payload không hợp lệ | Từ chối, ghi log có reason; không tự đoán dữ liệu |
| Consumer event handler lỗi | Consumer bắt lỗi và log; không đặt logic consumer vào M6 |

Không thêm dead-letter queue trong phạm vi hiện tại vì stack đã chốt là Spring
Events trong tiến trình.

**Quyết định TV3 (Hưng, ghi nhận 2026-10-20):**

- Áp dụng quy ước HTTP 400/404/409 trên toàn hệ thống; resource không tồn tại
  trả 404 (ví dụ `Vehicle_Not_Found`).
- API service dùng transaction cho validate → đổi state → save → publish; event
  được phát trong transaction hoặc sau commit tùy semantics của `EventPublisher`
  hiện tại.
- Consumer handler có transaction riêng. Lỗi consumer không được rollback dữ
  liệu của publisher đã commit; handler phải catch và log lỗi, không rethrow làm
  gián đoạn listener khác.
- Consumer dedupe theo cặp `(consumerKey, eventId)`. Hai consumer khác key đều
  có thể xử lý cùng event; lần lặp lại với cùng key là no-op.
- M3 dùng key ổn định và cập nhật `VehicleEventHandler`. Thay đổi này đã được
  TV3 duyệt và đã được cài đặt trên nhánh tích hợp local.

TV7 vẫn được tham vấn để bổ sung test case/expected result. `EventPublisher` hiện
dùng Spring `ApplicationEventPublisher` đồng bộ; cách triển khai transaction
phải bảo đảm đúng ranh giới trên, không được coi nội dung proposal là bằng chứng
rằng code hiện tại đã đạt contract.

**Cài đặt dedupe giữa nhiều consumer trên nhánh tích hợp local:**

```java
boolean markIfNew(String consumerKey, UUID eventId)
```

- Dedupe theo cặp `(consumerKey, eventId)` để một consumer không chặn consumer
  khác khi cùng nhận một event.
- `ProcessedEventStore` dùng `markIfNew(String consumerKey, UUID eventId)`;
  API cũ `markIfNew(UUID)` đã được thay sau khi kiểm tra caller.
- Handler M3 dùng key ổn định theo loại xử lý, ví dụ `m3.vehicle-failed` và
  `m3.reservation-expired`, và `m3.dispatch-completed`.
- TV3 đã xác nhận store theo consumer key và key ổn định của M3; expected result
  là cùng event ID với hai key khác nhau đều được xử lý, lần lặp lại với cùng key
  là no-op. Handler chạy trong transaction `REQUIRES_NEW`, bắt và log lỗi mà
  không ném ngược về publisher.

`ProcessedEventStore` vẫn chỉ giữ dedupe trong process và có giới hạn LRU 10.000
key; restart làm mất dữ liệu. Producer-side inbound `DeviceReading` dedupe của
M6 là store riêng, không thay cho consumer-side namespaced dedupe. Các thay đổi
ở trên có mặt trên `codex/integrate-m6-m3-20261019`; chúng chưa được chuyển vào
`feature/m6-iot-simulator-event-bus`. Focused Maven run đạt 14 test và full
`mvn -B verify` đạt 37 test trên integration working tree; feature verify đạt
31 test với consumer/M3 D09 changes vẫn chưa có trên branch đó.

### M6-D10 — Data model M6

**Lựa chọn của TV6 (ghi nhận 2026-10-20):** dùng model đề xuất bên dưới làm
baseline cho M6-D10:

**Entity `Incident`:**

- `id`, `resourceType`, `resourceId`, `hubId`;
- `reasonCode`, `description`, `severity`, `status`;
- `reporterType`, `reporterUserId`, `assigneeUserId`;
- `reportedAt`, `assignedAt`, `startedAt`, `resolvedAt`, `closedAt`;
- `version` cho optimistic locking.

**Dữ liệu thiết bị:** theo D06, M6 sở hữu health state của ChargingPoint; model
M6 giữ `id`, `code`, `hubId`, `healthState`, `updatedAt`, `version` và quan hệ
logic tới Incident. Usage/allocation/session state do M5 sở hữu.

**Schema baseline được TV4 chấp thuận ngày 2026-10-21:** Bảng dưới ghi lại model
đã gửi; TV4 không nêu thay đổi cụ thể nên không tự thêm hoặc suy diễn field.
Các migration/DDL vẫn chưa được tạo.

| Entity | Thuộc tính và nullability đề xuất | Khóa/quan hệ/index đề xuất |
| --- | --- | --- |
| `Incident` | `id: Long NOT NULL`; `resourceType: ResourceType NOT NULL`; `resourceId: Long NOT NULL`; `hubId: Long NOT NULL`; `reasonCode: String NOT NULL`; `description: String NULL`; `severity/status/reporterType` NOT NULL; `reporterUserId/assigneeUserId: Long NULL`; `reportedAt: OffsetDateTime NOT NULL`; `assignedAt/startedAt/resolvedAt/closedAt: OffsetDateTime NULL`; `version: Long NOT NULL` | Generated PK; `hub`/user relationships follow the approved proposal; no polymorphic FK for resource. Indexes `(resource_type, resource_id, status)`, `(hub_id, status)`, `(assignee_user_id, status)`. |
| `ChargingPoint` | `id: Long NOT NULL`; `code: String NOT NULL`; `hubId: Long NOT NULL`; `healthState` (type to be represented in schema); `updatedAt: OffsetDateTime NOT NULL`; `version: Long NOT NULL` | PK `id`; unique `code`; `hub` relationship follows the approved proposal. TV3 separated M6 health from M5 usage/session under D06; TV5/M5 still owns transition contract. |
| `ProcessedDeviceEvent` | `eventId: UUID NOT NULL`; `processedAt: OffsetDateTime NOT NULL`; `result: String NOT NULL` | Candidate PK/idempotency key `eventId`; no resource FK. Retention, transaction coupling to state/Incident writes, and persistence decision remain open. |

TV6 đã chọn các entity và thuộc tính trên làm baseline M6-D10; TV4 đã chấp thuận
proposal này ngày 2026-10-21. Đây là authority cho schema baseline. TV4 sở hữu
việc tạo Flyway migration; migration chưa tồn tại chỉ là khoảng trống triển khai,
không còn là bước chờ phê duyệt schema. Walking skeleton hiện vẫn giữ state và
inbound dedupe trong bộ nhớ.

**Triển khai tiếp:** TV4 tạo Flyway migration theo schema đã chấp thuận và phối
hợp nếu phát hiện ràng buộc vật lý không biểu diễn được proposal. Migration chưa
được tạo trong code hiện tại.

### M6-D11 — Có lưu lịch sử telemetry không?

**TV6 đề xuất và chọn làm M6 implementation baseline (2026-10-21):**

- Không lưu toàn bộ raw telemetry theo từng tick.
- Lưu trạng thái mới nhất cần cho vận hành và lưu Incident có vòng đời đầy đủ.
- Event/runtime log là bằng chứng debug nhưng không phải bảng lịch sử nghiệp vụ.
- Nếu M1/M8 cần time series, mở quyết định mới với retention, volume, query và
  ownership cụ thể; không âm thầm thêm bảng không giới hạn.

**Cần TV3 và TV4 quyết định:** chấp nhận latest-state-only hay cần lịch sử.
**Cần TV1 xác nhận:** phạm vi MVP này đủ cho demo/report.
**TV7 tham vấn:** loại evidence nào cần giữ cho test execution.

TV6 chỉ định baseline để M6 có thể tiếp tục triển khai mà không tạo raw history.
Các owner confirmations vẫn cần trước khi mô tả latest-state-only là policy đã
được toàn nhóm chấp thuận.

**Schema addendum để TV4 review trước khi implement D11:** M6 đề xuất bảng
`device_latest_state` có đúng một dòng cho mỗi `(resource_type, resource_id)`;
unique key trên cặp này; các cột `hub_id`, `state`, nullable `battery_level`,
nullable `failure_reason`, và `observed_at`. `resource_id` là logical reference,
không tạo polymorphic foreign key. Upsert thay thế snapshot hiện tại; không ghi
raw reading, tick history hay bảng telemetry. State health của ChargingPoint
tiếp tục thuộc entity M6 `ChargingPoint` theo D06/D10, không bị gộp vào snapshot
generic này.

> **Owner gate:** đây là schema addendum mới ngoài proposal D10 TV4 đã chấp thuận.
> Chưa có xác nhận của TV4 và chưa có entity/repository/Flyway migration; TV4
> cần duyệt addendum và sở hữu migration trước khi M6 bật latest-state
> persistence. Không sửa migration D10 hiện tại thay cho xác nhận đó.

### M6-D12 — Reason code và payload event

**TV6 đề xuất và chọn làm M6 implementation baseline (2026-10-21):** dùng
uppercase String reason codes để giữ tương thích với event class hiện có; không
đổi sang shared enum và không thêm field/version vào payload hiện hành.

- Vehicle failure: `MECHANICAL`, `SENSOR_OFFLINE`, `MANUAL_REPORT`.
- Charger failure: `OFFLINE`, `OVERHEAT`, `POWER_FAULT`, `MANUAL_REPORT`.
- Resource type cho `ResourceStateUpdated`: `VEHICLE`, `PARKING_SPACE`,
  `CHARGING_POINT`.

Payload giữ đúng contract hiện tại:

- `VehicleFailed`: `vehicleId`, `hubId`, `reason`.
- `ChargerFailed`: `chargingPointId`, `hubId`, `reason`.
- `ResourceStateUpdated`: `hubId`, `resourceType`, `resourceId`, `newState`.
- Mọi event kế thừa `eventId`, `occurredAt`.

**Cần TV3 review contract:** code list, String representation và giữ nguyên
shape/payload hiện có.
**Cần consumer xác nhận payload đủ dùng:** M1/M8 với ResourceStateUpdated; M2/M3 với
VehicleFailed; M4/M5 với ChargerFailed. Consumer chỉ xác nhận payload đủ dùng,
không chuyển logic của họ vào M6.

Đến khi nhận được review/ack này, các code/payload trên là baseline để implement
producer phía M6, chưa phải bằng chứng rằng mọi consumer đã tích hợp.

## 4. Lịch sử gói đã gửi theo từng thành viên

Các đoạn dưới đây là nội dung đã gửi trước khi nhận các phản hồi ngày
2026-10-21; không xem chúng là yêu cầu còn chờ đối với TV2/TV4. Kết quả mới nhất
được ghi ở bảng quyết định và phần chi tiết từng ID. Những ID chưa chốt tiếp tục
cần trao đổi với owner được nêu trong bảng.

### Gửi TV1 — PM và phạm vi

> Nhờ TV1 xác nhận phạm vi M6 ở các điểm: M6-D02 dùng simulator interval mặc
> định 5 giây cho demo; M6-D08 không thêm actor/role kỹ thuật viên; M6-D11 MVP
> chỉ lưu latest state + Incident, không lưu toàn bộ telemetry. Nếu các owner
> không thống nhất boundary, nhờ TV1 chốt phạm vi cuối. Vui lòng phản hồi từng ID
> bằng “đồng ý” hoặc phương án thay thế.

### Đã gửi TV2 — Requirements lead và M2 owner

> Nhờ TV2 quyết định/review M6-D01, D02, D07, D08: actor báo cáo sự cố; NFR chu
> kỳ simulator 5 giây; Incident flow có reopen từ Resolved về InProgress nhưng
> Closed là terminal; quyền API incident chỉ dành cho OPERATOR, Sensor chỉ tạo
> Reported. Đồng thời xác nhận ở M6-D05 rằng M2 sẽ tự xử lý reservation sau
> VehicleFailed, M6 không sửa reservation. Vui lòng trả lời theo từng ID.

### Gửi TV3 — Architect, M3 và shared owner

> Nhờ TV3 phê duyệt M6-D03, D04, D05, D06, D08, D09, D11, D12. Điểm cần chốt
> nhất là ownership ChargingPoint: A=M6 sở hữu toàn bộ, B=M5 sở hữu, C=tách
> device health của M6 khỏi allocation/session của M5; TV6 đề xuất C. Nhờ xác
> nhận event contract, transaction/error/idempotency, latest-state-only và danh
> sách reason code. Với vai trò M3, nhờ xác nhận VehicleFailed đủ để M3 chuyển
> xe sang Maintenance. M6 sẽ không sửa `shared/events` trước khi được duyệt.

### Đã gửi TV4 — Data lead và M4 owner

> TV6 đã chọn proposal Incident, ChargingPoint health state và ProcessedDeviceEvent
> làm baseline M6-D10. Nhờ TV4 xác nhận physical schema/quan hệ/FK/index,
> optimistic locking, yêu cầu persistence và migration tương ứng; đồng thời
> phối hợp quyết định D11 về latest-state-only hay lưu raw telemetry. TV4 sở hữu
> Flyway migration. Với vai trò M4, nhờ xác nhận M6-D05/D12: M4 tự xử lý
> parking liên quan sau ChargerFailed và payload hiện tại có đủ dùng không.

### Gửi TV5 — M5 và UI lead

> Nhờ TV5/M5 tham vấn M6-D04, D05, D06, D12: điều kiện charger failure; M5 tự
> dừng/phân lại ChargingSession sau ChargerFailed; lựa chọn ownership
> ChargingPoint; payload/reason code có đủ cho M5 không. TV6 đề xuất tách device
> health do M6 sở hữu khỏi allocation/session do M5 sở hữu. Sau khi chốt UC,
> TV6 sẽ gửi screen specification để TV5 tạo mockup incident management.

### Gửi TV6 — Việc tự chịu trách nhiệm

> TV6 soạn bản cuối của cả M6-D01..D12, tổng hợp phản hồi, cập nhật Template_M6,
> triển khai simulator/incident/event producer và viết test. TV6 không tự quyết
> thay owner ở các boundary, không sửa migration/shared contract khi chưa được
> duyệt, và không chờ consumer code để bắt đầu unit test bằng fixture.

### Gửi TV7 — Test lead

> Nhờ TV7 review tính kiểm thử của M6-D02, D03, D06, D07, D09, D11: scheduler
> không dùng wall-clock trong unit test; random có seed; state tables có positive
> và negative transitions; duplicate event là no-op; lỗi API có 400/404/409;
> evidence nào cần giữ khi không lưu raw telemetry. TV7 không cần quyết định
> product policy nhưng cần xác nhận mỗi rule có thể ánh xạ sang TC-M6.

### Gửi TV8 — M8 owner

> Nhờ TV8 xác nhận M6-D11/D12: M8 có đủ dữ liệu để tạo snapshot từ latest state
> và `ResourceStateUpdated(hubId, resourceType, resourceId, newState)` hay bắt
> buộc cần lịch sử telemetry/trường bổ sung. M8 không ghi ngược simulation state
> vào dữ liệu vận hành. Nếu cần thêm payload, vui lòng nêu trường và use case cụ
> thể để TV3 duyệt contract.

### Gửi TV1/M1 — Monitoring consumer

> Với vai trò M1, nhờ xác nhận M6-D12: payload ResourceStateUpdated hiện tại có
> đủ cập nhật dashboard hay cần thêm trường cụ thể. M1 tự xử lý dashboard/WS;
> M6 chỉ phát event. Nếu cần timestamp, event đã có `occurredAt` từ DomainEvent.

## 5. Việc tiếp theo sau các phản hồi đã nhận

TV6 thực hiện theo thứ tự; D01/D02/D07/D08 đã được TV2 chốt và proposal D10 đã
được TV4 chấp thuận. Tuy vậy, phần incident/latest-state triển khai vẫn phụ
thuộc các owner gate dưới đây:

1. Hoàn thiện phần độc lập đã được duyệt: reason-code allowlist D04/D12, interval
   D02, và catch-all OPERATOR rule D08; cập nhật tài liệu theo đúng branch state.
2. Chờ TV2 xác nhận severity vocabulary, severity mapping cho Incident tự động,
   và quyết định xử lý Incident active trùng BR-M6-06 trước khi bật tạo Incident
   từ Operator hoặc failure reading.
3. Chờ TV4 duyệt schema addendum latest-state ở D11 và triển khai migration
   trước khi bật persistence. TV4 vẫn sở hữu migration cho schema D10 đã duyệt.
4. Tiếp tục ghi nhận phối hợp TV5/M5 về condition/reaction `ChargerFailed`, TV5/M5
   về D06, và TV3/consumers về payload D12.
5. Viết code/proof sau khi các gate cần thiết được giải quyết; mọi sai lệch ghi
   vào §4.4 của Template_M6.
6. Không giữ proposal này như product authority: sau khi chốt, promote quyết
   định lâu dài vào `docs/decisions/` hoặc `docs/product/`, rồi đánh dấu proposal
   là Accepted/Superseded.
