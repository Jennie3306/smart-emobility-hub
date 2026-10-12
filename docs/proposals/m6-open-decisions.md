# Proposal: Các quyết định còn mở của M6

Ngày soạn: 2026-09-21
Trạng thái: **Đề xuất, chưa phải yêu cầu/BR đã được chấp nhận**
Người chủ trì: **TV6 – M6, DevOps & Tích hợp**

## 1. Mục đích và cách phản hồi

Tài liệu này xin quyết định cho các điểm còn thiếu trước khi chốt FR, BR, use
case, state machine, class diagram và code của M6. Người nhận phản hồi bằng một
trong ba dạng:

- `Đồng ý phương án đề xuất`;
- `Chọn phương án khác: ...`;
- `Chưa quyết định được vì cần: ...`.

TV6 cập nhật kết quả vào cột Quyết định cuối. Chỉ sau khi người phê duyệt bắt
buộc đồng ý, nội dung mới được chuyển sang `Template_M6.md`, product docs và code.
Nếu các bên không thống nhất, TV1 chốt phạm vi; TV3 chốt ranh giới kỹ thuật.

## 2. Bảng định tuyến quyết định

| ID | Chủ đề | TV6 chịu trách nhiệm | Phê duyệt bắt buộc | Tham vấn | Quyết định cuối |
| --- | --- | --- | --- | --- | --- |
| M6-D01 | Actor báo cáo sự cố | Soạn FR/UC | TV2 | TV1 | Chưa chốt |
| M6-D02 | Chu kỳ simulator | Đề xuất cấu hình | TV2 | TV1, TV7 | Chưa chốt |
| M6-D03 | Chế độ chạy simulator | Thiết kế kỹ thuật | TV3 | TV7 | Chưa chốt |
| M6-D04 | Điều kiện phát hiện hỏng | Soạn BR và fixture | TV3 | TV3/M3, TV5/M5 | Chưa chốt |
| M6-D05 | Ý nghĩa tự khóa tài nguyên | Đề xuất boundary | TV3 | TV2/M2, TV3/M3, TV4/M4, TV5/M5 | Chưa chốt |
| M6-D06 | Vòng đời ChargingPoint | Soạn state table | TV3 | TV5/M5, TV7 | Chưa chốt |
| M6-D07 | Vòng đời Incident | Soạn state table | TV2 | TV3, TV7 | Chưa chốt |
| M6-D08 | Quyền xử lý Incident | Soạn UC/authorization | TV2 và TV3 | TV1 | Chưa chốt |
| M6-D09 | Event/payload lỗi hoặc trùng | Thiết kế xử lý | TV3 | TV7 | Chưa chốt |
| M6-D10 | Data model M6 | Soạn entity/quan hệ | TV4 | TV3 | Chưa chốt |
| M6-D11 | Lưu lịch sử telemetry | Đề xuất phạm vi | TV3 và TV4 | TV1, TV7 | Chưa chốt |
| M6-D12 | Mã lý do và payload event | Soạn contract | TV3 | M1, M2, M3, M4, M5, M8 | Chưa chốt |

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

**Cần TV2 quyết định:** actor và system boundary trên use-case diagram có chấp
nhận đúng ba ý trên không? Nếu Student được tham gia, cần cung cấp mục tiêu,
quyền, use case và dữ liệu bắt buộc.

### M6-D02 — Chu kỳ simulator

**Vấn đề:** FR yêu cầu cập nhật định kỳ nhưng chưa có giá trị đo được.

**TV6 đề xuất:** cấu hình `app.simulator.interval-ms`; mặc định demo là **5.000
ms**. Test không chờ scheduler thật mà gọi một simulation tick trực tiếp.

**Cần TV2 quyết định:** 5 giây có đáp ứng NFR “gần thời gian thực” cho bài tập
không?
**Cần TV1 xác nhận phạm vi demo:** tốc độ này có phù hợp video/demo không?
**TV7 tham vấn:** cách test không phụ thuộc thời gian thực có đủ kiểm chứng NFR
không?

### M6-D03 — Simulator chạy tự động hay thủ công?

**Vấn đề:** Chạy random liên tục khó tái hiện test và video; chỉ chạy thủ công
thì chưa thể hiện FR non-interactive.

**TV6 đề xuất:** hỗ trợ cả hai chế độ trên cùng simulation service:

- scheduled mode cho FR non-interactive;
- manual scenario/tick cho test và demo xác định;
- random luôn nhận seed cấu hình;
- scheduler tắt trong test, bật bằng cấu hình ở dev/demo.

**Cần TV3 quyết định:** thiết kế hai adapter dùng chung một service có phù hợp
kiến trúc không? Không bổ sung message broker hoặc service riêng.

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

**Cần TV3 quyết định:** ranh giới “failure fact” và event contract.
**Cần M3 xác nhận:** `VehicleFailed` có đủ để M3 chuyển xe sang Maintenance.
**Cần M5 xác nhận:** các điều kiện charger failure có đủ để M5 dừng/phân lại
phiên sạc.

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

**Cần TV3 quyết định:** owner của trạng thái thiết bị và định nghĩa “lock” tại
boundary.
**Cần M2/M3/M4/M5 xác nhận:** mỗi module có nhận trách nhiệm phản ứng tương ứng
không?

### M6-D06 — Vòng đời và owner của ChargingPoint

**Vấn đề:** Template giao state machine ChargingPoint cho M6, trong khi M5 dùng
cổng sạc để vận hành ChargingSession.

**TV6 đề xuất state table:**

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

**Quyết định kiến trúc cần TV3 chọn:**

- A — M6 sở hữu toàn bộ ChargingPoint; cần contract để M5 thông báo bắt đầu/kết
  thúc sử dụng.
- B — M5 sở hữu ChargingPoint; M6 chỉ sở hữu Incident và phát failure event.
- C — tách device health do M6 sở hữu khỏi charging allocation/session do M5 sở
  hữu.

**TV6 khuyến nghị C** vì giảm ghi chéo, nhưng cần cập nhật state machine/report
để phân biệt health và usage. TV5/M5 phải xác nhận mô hình vẫn đủ cho lập lịch.

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

**Cần TV2 quyết định:** đây có đúng business process và use-case exception flow
không? TV3 kiểm tra tính hiện thực; TV7 xác nhận đủ cho state-transition test.

### M6-D08 — Ai được chuyển trạng thái Incident?

**Hiện trạng:** SecurityConfig đã yêu cầu role `OPERATOR` cho
`/api/m6/incidents/**`; hệ thống hiện chỉ có STUDENT và OPERATOR.

**TV6 đề xuất:**

- Sensor/Simulator chỉ tạo Reported incident qua adapter nội bộ, không
  assign/resolve/close và không đi qua endpoint HTTP của Operator.
- Mọi OPERATOR được report, assign, start, resolve và close trong phạm vi bài tập.
- Không thêm role kỹ thuật viên ở phiên bản hiện tại.
- Student không truy cập API quản lý incident.

**Cần TV2 và TV3 quyết định:** chấp nhận mô hình quyền đơn giản này hay phải bổ
sung actor/role/ownership rule. Thêm role mới phải được TV1 chấp nhận vì tăng
phạm vi toàn hệ thống.

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

**Cần TV3 quyết định:** mã lỗi, transaction boundary và hành vi idempotent.
**TV7 tham vấn:** mỗi dòng phải có test case và expected result tương ứng.

**Đề xuất riêng về dedupe giữa nhiều consumer (chưa triển khai, chờ TV3):**

```java
boolean markIfNew(String consumerKey, UUID eventId)
```

- Dedupe theo cặp `(consumerKey, eventId)` để một consumer không chặn consumer
  khác khi cùng nhận một event.
- Giữ `markIfNew(UUID)` tạm thời để tương thích branch hiện tại và đánh dấu
  deprecated sau khi TV3 duyệt.
- Handler M3 dùng key ổn định theo loại xử lý, ví dụ `m3.vehicle-failed` và
  `m3.reservation-expired`.
- TV3 xác nhận store, consumer keys, tương thích API cũ, và expected result:
  cùng event ID với hai key khác nhau đều được xử lý; lần lặp lại với cùng key
  là no-op.

Đề xuất này không làm thay đổi `ProcessedEventStore` hoặc handler M3. Producer
M6 có test độc lập và không tuyên bố consumer-side namespaced dedupe đã hoàn tất.

### M6-D10 — Data model M6

**TV6 đề xuất entity `Incident`:**

- `id`, `resourceType`, `resourceId`, `hubId`;
- `reasonCode`, `description`, `severity`, `status`;
- `reporterType`, `reporterUserId`, `assigneeUserId`;
- `reportedAt`, `assignedAt`, `startedAt`, `resolvedAt`, `closedAt`;
- `version` cho optimistic locking.

**Đề xuất dữ liệu thiết bị:** phụ thuộc M6-D06. Nếu M6 sở hữu health của
ChargingPoint, cần tối thiểu `id`, `code`, `hubId`, `healthState`, `updatedAt`,
`version` và quan hệ tới Incident.

**Handoff TV4 theo PLAN (15), vẫn là đề xuất chờ duyệt:**

| Entity | Thuộc tính và nullability đề xuất | Khóa/quan hệ/index đề xuất |
| --- | --- | --- |
| `Incident` | `id: Long NOT NULL`; `resourceType: ResourceType NOT NULL`; `resourceId: Long NOT NULL`; `hubId: Long NOT NULL`; `reasonCode: String NOT NULL`; `description: String NULL`; `severity/status/reporterType` NOT NULL; `reporterUserId/assigneeUserId: Long NULL`; `reportedAt: OffsetDateTime NOT NULL`; `assignedAt/startedAt/resolvedAt/closedAt: OffsetDateTime NULL`; `version: Long NOT NULL` | Generated PK; FK to `hub.id` and optional user FKs need TV4 decision; no polymorphic FK for resource until TV4 chooses representation. Candidate indexes `(resource_type, resource_id, status)`, `(hub_id, status)`, `(assignee_user_id, status)`. |
| `ChargingPoint` | `id: Long NOT NULL`; `code: String NOT NULL`; `hubId: Long NOT NULL`; `state: ChargingPointState NOT NULL`; `updatedAt: OffsetDateTime NOT NULL`; `version: Long NOT NULL` | PK `id`; candidate unique constraint on `code`; FK to `hub.id` needs TV4 decision. TV3/TV5 must settle whether `state` combines health and usage or is split. |
| `ProcessedDeviceEvent` | `eventId: UUID NOT NULL`; `processedAt: OffsetDateTime NOT NULL`; `result: String NOT NULL` | Candidate PK/idempotency key `eventId`; no resource FK. Retention, transaction coupling to state/Incident writes, and persistence decision remain open. |

These tables are a review package only. They do not create an accepted schema or
authorize TV6 to add Flyway files. The walking skeleton keeps all M6 state and
inbound dedupe in memory.

**Cần TV4 quyết định:** tên bảng/cột, FK, cardinality, index và migration order.
TV6 không tự tạo Flyway migration.
**Cần TV3 review:** entity/class boundary có khớp kiến trúc không?

### M6-D11 — Có lưu lịch sử telemetry không?

**TV6 đề xuất cho MVP:**

- Không lưu toàn bộ raw telemetry theo từng tick.
- Lưu trạng thái mới nhất cần cho vận hành và lưu Incident có vòng đời đầy đủ.
- Event/runtime log là bằng chứng debug nhưng không phải bảng lịch sử nghiệp vụ.
- Nếu M1/M8 cần time series, mở quyết định mới với retention, volume, query và
  ownership cụ thể; không âm thầm thêm bảng không giới hạn.

**Cần TV3 và TV4 quyết định:** chấp nhận latest-state-only hay cần lịch sử.
**Cần TV1 xác nhận:** phạm vi MVP này đủ cho demo/report.
**TV7 tham vấn:** loại evidence nào cần giữ cho test execution.

### M6-D12 — Reason code và payload event

**TV6 đề xuất dùng uppercase string code** để giữ tương thích với các event class
hiện dùng `String`, chưa đổi thành shared enum.

- Vehicle failure: `MECHANICAL`, `SENSOR_OFFLINE`, `MANUAL_REPORT`.
- Charger failure: `OFFLINE`, `OVERHEAT`, `POWER_FAULT`, `MANUAL_REPORT`.
- Resource type cho `ResourceStateUpdated`: `VEHICLE`, `PARKING_SPACE`,
  `CHARGING_POINT`.

Payload giữ đúng contract hiện tại:

- `VehicleFailed`: `vehicleId`, `hubId`, `reason`.
- `ChargerFailed`: `chargingPointId`, `hubId`, `reason`.
- `ResourceStateUpdated`: `hubId`, `resourceType`, `resourceId`, `newState`.
- Mọi event kế thừa `eventId`, `occurredAt`.

**Cần TV3 quyết định:** code list, String hay enum, và có cần version event hay
không.
**Cần consumer xác nhận:** M1/M8 với ResourceStateUpdated; M2/M3 với
VehicleFailed; M4/M5 với ChargerFailed. Consumer chỉ xác nhận payload đủ dùng,
không chuyển logic của họ vào M6.

## 4. Gói gửi theo từng thành viên

Các đoạn dưới đây có thể copy riêng. Khi phản hồi, đề nghị người nhận ghi ID để
TV6 tổng hợp không nhầm.

### Gửi TV1 — PM và phạm vi

> Nhờ TV1 xác nhận phạm vi M6 ở các điểm: M6-D02 dùng simulator interval mặc
> định 5 giây cho demo; M6-D08 không thêm actor/role kỹ thuật viên; M6-D11 MVP
> chỉ lưu latest state + Incident, không lưu toàn bộ telemetry. Nếu các owner
> không thống nhất boundary, nhờ TV1 chốt phạm vi cuối. Vui lòng phản hồi từng ID
> bằng “đồng ý” hoặc phương án thay thế.

### Gửi TV2 — Requirements lead và M2 owner

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

### Gửi TV4 — Data lead và M4 owner

> Nhờ TV4 quyết định M6-D10 và đồng quyết định D11: schema Incident, dữ liệu
> health của ChargingPoint, FK/index/cardinality, optimistic locking và
> latest-state-only thay vì lưu raw telemetry. TV6 sẽ gửi entity/attribute; TV4
> tạo Flyway migration. Với vai trò M4, nhờ xác nhận M6-D05/D12: M4 tự xử lý
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

## 5. Sau khi nhận phản hồi

TV6 thực hiện theo thứ tự:

1. Điền Quyết định cuối và ngày/người chấp nhận cho M6-D01..D12.
2. Chuyển nội dung được chấp nhận thành FR-M6, BR-M6 và use-case exception flow.
3. Chốt state tables trước khi vẽ state/activity/sequence/class diagrams.
4. Gửi entity/attribute đã chốt cho TV4 và event delta cho TV3.
5. Viết code/test theo decision; mọi sai lệch ghi vào §4.4 của Template_M6.
6. Không giữ proposal này như product authority: sau khi chốt, promote quyết
   định lâu dài vào `docs/decisions/` hoặc `docs/product/`, rồi đánh dấu proposal
   là Accepted/Superseded.
