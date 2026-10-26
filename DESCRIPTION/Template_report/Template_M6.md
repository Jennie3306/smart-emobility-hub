**TEMPLATE MODULE M6**

**Bộ giả lập IoT, sự kiện & xử lý sự cố**

*Smart E-Mobility Hub — Công nghệ Phần mềm HK261 — Người phụ trách: TV6*

| **Người phụ trách** | TV6 — DevOps, Tích hợp & Dựng video demo Họ tên: ................................. |
|----|----|
| **Phạm vi module** | Sinh dữ liệu cảm biến giả lập; cập nhật trạng thái tài nguyên; ghi nhận và theo dõi sự cố (xe hỏng, cổng sạc ngừng hoạt động); **phát sự kiện** cho các module liên quan. **Không** tự xử lý đặt chỗ hay phiên sạc của module khác. |
| **Cách dùng file này** | Điền lần lượt từ mục 1 đến mục 9, cập nhật sau mỗi vòng. Chữ nghiêng xám là gợi ý, điền xong thì xóa. Nội dung trong file này chính là nguyên liệu để viết file .tex của module trên Prism. |

> **Trạng thái tài liệu (cập nhật 21/10/2026):** TV2 đã chốt M6-D01, D02 (chu kỳ
> demo 5.000 ms), D07 và D08; TV3 đã chốt D03, D05, D06, D09; TV4 đã chấp thuận
> proposal schema D10. D08 yêu cầu toàn bộ `/api/m6/**` dùng role `OPERATOR`, chỉ
> ngoại lệ API chỉ-đọc khi có nhu cầu Student cụ thể; hiện chưa xác định ngoại lệ.
> TV6 đã chọn proposal D04/D11/D12 làm baseline implement phía M6; allowlist mã
> lỗi D04/D12 đang được kiểm tra theo loại resource. D04 vẫn cần TV5/M5 xác nhận
> ChargerFailed, D11 addendum schema latest-state cần TV4 duyệt, D12 cần
> TV3 review và consumer xác nhận payload trước khi coi là contract toàn hệ thống.
> Các ID khác vẫn theo dõi trong [`m6-open-decisions.md`](../../docs/proposals/m6-open-decisions.md).
> Mục §7.3/§7.4 và §8 ghi nhận walking skeleton simulator đã có code và focused
> test. Consumer-side dedupe D09 đã được triển khai ở nhánh local
> `codex/integrate-m6-m3-20261019` và được kiểm tra ở §8; thay đổi đó chưa có
> trên nhánh feature M6. Incident workflow chưa được triển khai; persistence và
> atomic after-commit cho M6 vẫn chờ TV4 duyệt addendum latest-state và TV2 chốt
> severity/mapping/active-duplicate policy cho Incident.

> **Đồng bộ liên module ngày 02/10/2026:** Bản mới của M2 xác nhận nhận
> `VehicleFailed`; M4 và M5 xác nhận nhận `ChargerFailed`. M8 vẫn là consumer
> `ResourceStateUpdated` theo Phụ lục A, nhưng chỉ dùng event làm tín hiệu dữ
> liệu đã đổi; mỗi simulation run phải lấy snapshot nhất quán qua API chỉ đọc.
> TV3 chọn phương án C cho M6-D06 (ghi nhận 20/10/2026): M6 sở hữu device
> health, M5 sở hữu charging allocation/session. Transition contract chi tiết
> vẫn cần phối hợp với TV5/M5.

# 1. Functional requirements (§2.2)

> Mỗi FR một câu "Hệ thống phải…". Mã FR-M6-01, 02… MoSCoW: Must/Should/Could/Won't. Module này bắt buộc có FR non-interactive — đã điền sẵn gợi ý ở dòng cuối.

| **Mã** | **Tên FR** | **Mô tả chi tiết** | **MoSCoW** | **Loại** | **Tác nhân kích hoạt** |
|----|----|----|----|----|----|
| FR-M6-01 | Báo cáo sự cố | Hệ thống phải cho phép Operator đã đăng nhập báo cáo xe hoặc cổng sạc bị hỏng bằng cách cung cấp Hub, loại tài nguyên, mã tài nguyên, mức độ, lý do và mô tả. | Must | Interactive | Operator |
| FR-M6-02 | Xử lý vòng đời sự cố | Hệ thống phải cho phép Operator xem, gán người xử lý và chuyển Incident qua các trạng thái hợp lệ `Reported`, `Assigned`, `InProgress`, `Resolved`, `Closed`, đồng thời từ chối chuyển trạng thái sai. | Must | Interactive | Operator |
| FR-M6-03 | Tiếp nhận dữ liệu thiết bị | Hệ thống phải tiếp nhận readings do adapter Sensor/Simulator sinh ra cho xe (bao gồm thông tin pin), chỗ đỗ và cổng sạc; kiểm tra payload/định danh, chống xử lý trùng theo inbound `DeviceReading.eventId`, rồi chỉ lưu latest state thuộc quyền sở hữu M6. | Must | **Non-interactive** | Sensor/Simulator |
| FR-M6-04 | Cập nhật trạng thái định kỳ | Hệ thống phải kích hoạt simulation tick theo chu kỳ cấu hình 5 giây đã được TV2 chốt cho demo, cho readings về xe/pin, chỗ đỗ và cổng sạc; phát `ResourceStateUpdated` với `hubId`, `resourceType`, `resourceId`, `newState` sau khi thay đổi được lưu theo ownership đã chốt. M6 không ghi state hay bảng thuộc module khác. *(D02 đã chốt; D05/D06 đã được TV3 chốt; D10 được TV4 chấp thuận, migration chưa triển khai)* | Must | **Non-interactive** | Timer/Scheduler |
| FR-M6-05 | Cô lập tài nguyên gặp sự cố | Khi xác nhận lỗi thiết bị, hệ thống phải tạo Incident ở trạng thái `Reported`, khóa health state do M6 sở hữu và phát đúng `VehicleFailed` hoặc `ChargerFailed`; M6 không sửa reservation, vehicle lifecycle, parking hoặc charging session của module khác. | Must | **Non-interactive** | Sự kiện thiết bị |

# 2. Business rules (§2.4)

> Luật phải có con số hoặc điều kiện cụ thể, và phải khớp với code thật. Ví dụ: "Thời gian giữ chỗ tối đa 15 phút".

| **Mã**   | **Luật nghiệp vụ** | **Áp dụng khi** | **Liên quan FR** |
|----------|--------------------|-----------------|------------------|
| BR-M6-01 | Chỉ người dùng role `OPERATOR` được gọi API Incident; Sensor/Simulator tạo Incident qua adapter nội bộ. Student không tạo hoặc xử lý Incident; hiện chưa có endpoint chỉ-đọc M6 được yêu cầu cho Student. TV2 chốt actor/policy và đồng ý không thêm kỹ thuật viên. *(M6-D01, D08; TV2/TV3, 2026-10-21)* | Mọi thao tác Incident | FR-M6-01, FR-M6-02 |
| BR-M6-02 | Scheduler dùng `app.simulator.interval-ms=5000` cho chu kỳ demo theo quyết định TV2. Mọi kịch bản ngẫu nhiên phải nhận seed; unit test gọi tick trực tiếp và không chờ đồng hồ thật. Interval đã cấu hình; scheduler vẫn tắt mặc định. *(M6-D02, D03; TV2, 2026-10-21)* | Chạy simulator | FR-M6-03, FR-M6-04 |
| BR-M6-03 | Pin thấp không phải `VehicleFailed`: TV3 xác nhận pin thấp dùng `VehicleLowBattery`, còn `VehicleFailed` làm M3 chuyển xe sang `MAINTENANCE`. M6 kiểm tra đúng allowlist uppercase theo resource: Vehicle `MECHANICAL`, `SENSOR_OFFLINE`, `MANUAL_REPORT`; Charger `OFFLINE`, `OVERHEAT`, `POWER_FAULT`, `MANUAL_REPORT`. M6 không suy diễn ngưỡng; TV5/M5 vẫn cần xác nhận charger trigger/reaction. *(D04/D12 baseline TV6 2026-10-21; validation code có trên hai nhánh local)* | Phân loại dữ liệu lỗi | FR-M6-03, FR-M6-05 |
| BR-M6-04 | Incident chỉ chuyển `Reported → Assigned → InProgress → Resolved → Closed`; cho phép `Resolved → InProgress` để reopen. `Closed` là terminal. TV2 đã chốt lifecycle; Incident state machine/API chưa được implement. *(M6-D07; TV2, 2026-10-21)* | Chuyển trạng thái Incident | FR-M6-02 |
| BR-M6-05 | TV3 chốt tách device health do M6 sở hữu khỏi charging allocation/session do M5 sở hữu (phương án C). Các transition và quy tắc cấp phát cụ thể trong bảng state vẫn là proposal, cần TV5/M5 xác nhận. *(M6-D06)* | Cập nhật trạng thái cổng sạc | FR-M6-03, FR-M6-05 |
| BR-M6-06 | Mỗi tài nguyên chỉ có tối đa một Incident đang hoạt động (`Reported`, `Assigned`, `InProgress`) tại một thời điểm; báo cáo trùng bị từ chối với `ACTIVE_INCIDENT_EXISTS`. *(baseline bổ sung cần TV2 duyệt)* | Tạo Incident | FR-M6-01, FR-M6-05 |
| BR-M6-07 | `DeviceReading.eventId` là inbound idempotency key. D11 baseline TV6 chỉ lưu latest state và Incident, không lưu raw telemetry theo từng tick; durable persistence chưa implement. Consumer-side dedupe dùng `(consumerKey, eventId)`; lỗi consumer được catch/log, không rethrow làm rollback publisher đã commit. Mỗi outbound `DomainEvent` có ID riêng. *(D09/D10 đã chốt; D11 baseline TV6, owner confirmation còn mở)* | Xử lý device update/event | FR-M6-03, FR-M6-04, FR-M6-05 |
| BR-M6-08 | M6 không gọi UPDATE hoặc ghi dữ liệu thuộc module khác; M6 phát event để consumer tự xử lý. TV3 chốt `VehicleFailed` để M3 chuyển xe sang `MAINTENANCE` và M2 hủy Reservation tương ứng. Phản ứng ChargerFailed của M4/M5 cần xác nhận riêng. *(M6-D05 đã chốt; D12 còn chờ)* | Tích hợp liên module | FR-M6-03, FR-M6-05 |
| BR-M6-09 | TV3 chốt quy ước HTTP 400/404/409 áp dụng toàn hệ thống. API service thực hiện validate → đổi state → save → publish trong transaction hoặc sau commit theo semantics của `EventPublisher`; consumer có transaction riêng, lỗi được catch/log và không rollback publisher đã commit. Atomic rollback của M6 reading/Incident chưa implement. *(M6-D09, D10)* | Lỗi API và transaction | FR-M6-01, FR-M6-02, FR-M6-03, FR-M6-04, FR-M6-05 |

# 3. Use-case (§2.5)

## 3.1 Use-case diagram: "Sự cố & sự kiện thiết bị"

File nguồn: [`m6_use_case.drawio`](../../docs/architecture/m6_use_case.drawio)

![Use-case diagram M6](../../docs/figures/m6_use_case.drawio.png)

Sơ đồ đặt Operator, Sensor/Simulator và Timer/Scheduler ngoài system boundary
M6. Operator tham gia UC-M6-01 và UC-M6-02; Sensor/Simulator tham gia UC-M6-03;
Timer/Scheduler kích hoạt UC-M6-04. Association giữa actor và use case là đường
liền không có mũi tên. UC-M6-01 và UC-M6-03 «include» use case kiểm tra payload
và tài nguyên; UC-M6-04 «include» UC-M6-03 vì tick dùng cùng luồng xử lý dữ liệu
thiết bị. UC-M6-05 «extend» UC-M6-03 khi dữ liệu xác nhận lỗi; mũi tên «extend»
trỏ về use case gốc UC-M6-03. Ghi chú UML nêu các event được phát sau commit;
M2–M5 tự xử lý event ngoài boundary M6. Actor, cadence, Incident lifecycle và
permission đã được TV2 chốt; các phần chưa có quyết định tiếp tục được ghi rõ
trong bảng open decisions. Đây là thiết kế, không phải xác nhận đã implement.

## 3.2 Đặc tả use case (tối thiểu 3)

> Các use case cần đặc tả: Báo cáo sự cố; Xử lý sự cố; Nhận sự kiện thiết bị (actor IoT/Simulator). Ngoại lệ tập trung vào: Sự cố xảy ra khi tài nguyên đang được đặt hoặc đang sạc.

### UC-M6-01 — Báo cáo sự cố thiết bị

| **Trường** | **Đặc tả** |
|---|---|
| **Actor chính** | Operator |
| **Stakeholder & mối quan tâm** | Operator cần ghi nhận đúng thiết bị và theo dõi xử lý; M2–M5 cần nhận đúng failure fact nhưng không bị M6 sửa dữ liệu; TV7 cần kết quả kiểm chứng được. |
| **Tiền điều kiện** | Actor đã đăng nhập với role `OPERATOR`; resource catalog có tài nguyên đích. Incident active được kiểm tra trong luồng, không phải tiền điều kiện. |
| **Hậu điều kiện thành công** | Incident mới ở `Reported` được lưu; health state thuộc M6 được cập nhật khi áp dụng; event tương ứng chỉ phát sau khi lưu thành công; response chứa ID và trạng thái Incident. |
| **Hậu điều kiện thất bại** | Không tạo dữ liệu một phần và không phát event. Incident/state trước yêu cầu giữ nguyên. |
| **Trigger** | Operator chọn “Báo cáo sự cố”. |
| **Luồng chính** | 1. Operator chọn loại tài nguyên và Hub. 2. Hệ thống hiển thị tài nguyên phù hợp. 3. Operator nhập resource ID, severity, reason code và mô tả. 4. Hệ thống xác thực payload, resource và quyền. 5. Hệ thống kiểm tra Incident đang hoạt động. 6. `IncidentService.reportIncident(...)` tạo Incident `Reported`. 7. Hệ thống lưu Incident và health state thuộc M6 nếu áp dụng. 8. Chỉ sau commit thành công, hệ thống phát `VehicleFailed` hoặc `ChargerFailed` và `ResourceStateUpdated` nếu state thay đổi. 9. Hệ thống trả chi tiết Incident. |
| **Luồng thay thế** | 3a. Operator chọn `MANUAL_REPORT` và nhập mô tả bắt buộc. 6a. Nếu resource đang có reservation hoặc charging session, M6 vẫn tạo Incident và phát failure fact nhưng không tự hủy/đổi phiên; consumer M2–M5 quyết định phản ứng. 8a. Nếu resource là loại không có failure event chuyên biệt, hệ thống chỉ phát `ResourceStateUpdated` sau khi TV3 duyệt contract. |
| **Luồng ngoại lệ** | 4a. Payload sai → API trả 400. 4b. Resource không tồn tại → API trả 404. 5a. Đã có Incident hoạt động → API trả 409 `ACTIVE_INCIDENT_EXISTS`. 6b. State transition không hợp lệ → API trả 409. 7a. Write/commit thất bại → rollback, không phát event, trả lỗi hệ thống. |
| **FR/BR liên quan** | FR-M6-01, FR-M6-05; BR-M6-01, BR-M6-03, BR-M6-06–09. |

### UC-M6-02 — Xử lý vòng đời Incident

| **Trường** | **Đặc tả** |
|---|---|
| **Actor chính** | Operator |
| **Stakeholder & mối quan tâm** | Operator cần biết sự cố đang ở bước nào; module tài nguyên chỉ nhận fact cần thiết; nhóm cần lịch sử timestamp nhất quán. |
| **Tiền điều kiện** | Actor có role `OPERATOR`; Incident tồn tại và chưa ở `Closed`; request mang version hiện tại để chống ghi đè đồng thời. |
| **Hậu điều kiện thành công** | Incident chuyển đúng trạng thái; assignee và timestamp tương ứng được lưu; ChargingPoint health được cập nhật nếu sự cố cổng sạc bắt đầu/kết thúc sửa chữa. |
| **Hậu điều kiện thất bại** | Incident và ChargingPoint giữ nguyên; không phát state update mới. |
| **Trigger** | Operator mở chi tiết Incident và chọn hành động workflow. |
| **Luồng chính** | 1. Hệ thống tải Incident và các hành động hợp lệ. 2. Operator chọn Assign và assignee; hệ thống chuyển `Reported → Assigned`. 3. Operator chọn Start repair; hệ thống chuyển `Assigned → InProgress`, ChargingPoint `Faulty → UnderRepair` nếu áp dụng. 4. Operator chọn Resolve; hệ thống chuyển `InProgress → Resolved`, lưu `resolvedAt`; cổng sạc sửa thành công chuyển `UnderRepair → Available`. 5. Operator xác nhận Close; hệ thống chuyển `Resolved → Closed` và lưu `closedAt`. |
| **Luồng thay thế** | 4a. Lỗi tái diễn trước khi close: Operator chọn Reopen, hệ thống chuyển `Resolved → InProgress`. 3a. Sửa cổng thất bại: ChargingPoint chuyển `UnderRepair → Faulty`, Incident vẫn `InProgress` và ghi lý do. |
| **Luồng ngoại lệ** | 1a. Incident không tồn tại → 404. 2a–5a. Chuyển trạng thái không có trong BR-M6-04 → 409. 2b. Version đã thay đổi bởi Operator khác → 409 concurrency conflict và yêu cầu tải lại. 4b. Lưu state tài nguyên thất bại → rollback toàn transaction. |
| **FR/BR liên quan** | FR-M6-02, FR-M6-05; BR-M6-01, BR-M6-04, BR-M6-05, BR-M6-09. |

### UC-M6-03 — Nhận dữ liệu thiết bị và phát sự kiện

| **Trường** | **Đặc tả** |
|---|---|
| **Actor chính** | Sensor/Simulator; Timer/Scheduler kích hoạt định kỳ |
| **Stakeholder & mối quan tâm** | M1 cần cập nhật dashboard; M8 cần snapshot mới; M2–M5 cần failure fact; TV6 cần dữ liệu tái hiện được và không xử lý trùng. |
| **Tiền điều kiện** | Adapter nội bộ đang bật; payload có `eventId`, `hubId`, `resourceType`, `resourceId`, `newState`; `reason` có mặt khi reading báo lỗi. Resource có thể không tồn tại; trường hợp đó được xử lý trong luồng ngoại lệ. |
| **Hậu điều kiện thành công** | Latest state thuộc M6 được lưu một lần; `ResourceStateUpdated` được phát; nếu là failure thì Incident `Reported` và đúng failure event được phát. |
| **Hậu điều kiện thất bại** | Payload bị từ chối hoặc transaction rollback; không phát event dựa trên dữ liệu chưa lưu. |
| **Trigger** | Khi được cấu hình rõ, Timer/Scheduler gọi `scheduledTick()` và delegate sang `runScenario(NORMAL_TICK, seed)`; Operator gọi deterministic scenario qua `SimulatorController`. Scheduler hiện mặc định tắt. |
| **Luồng chính** | 1. `DeviceSimulatorService` tạo batch readings, mỗi reading có inbound `eventId`. 2. Với từng reading, `DeviceEventService.processReading(...)` gọi `validate(reading)`. 3. M6 tìm resource theo `resourceId`. 4. Walking skeleton gọi producer-side `markIfNew(eventId)` trong store process-memory riêng. 5. Nếu mới, M6 kiểm tra và cập nhật latest state trong memory. 6. M6 phát `ResourceStateUpdated`; các failure scenario phát thêm event hiện có. 7. Hệ thống ghi structured log với inbound eventId và kết quả; outbound `DomainEvent.eventId` được sinh riêng. |
| **Luồng thay thế** | 5a. Reading bình thường lưu state và phát `ResourceStateUpdated` sau commit; bỏ qua tạo Incident và failure event. 6a. Vehicle pin thấp không phải failure; M6 chỉ phát state update, còn `VehicleLowBattery` thuộc M3. 6b. Nếu lỗi xảy ra khi xe đang được đặt hoặc cổng đang phục vụ phiên sạc, M6 cô lập health state thuộc mình; M2/M3 hoặc M4/M5 tự xử lý reservation/session. 1a. Operator chạy deterministic scenario với seed cố định qua cùng pipeline. |
| **Luồng ngoại lệ** | 2a. Payload sai → trả `INVALID_DEVICE_PAYLOAD`, không ghi/publish. 3a. Resource không tồn tại → trả `RESOURCE_NOT_FOUND`, không tự tạo resource. 4a. `eventId` đã xử lý → `ProcessingResult.duplicate()` và structured log; kết thúc trước transition. 5b. Transition sai → rollback inbound dedupe mark nếu đã ghi, trả `INVALID_STATE_TRANSITION`, không publish. 6c. Incident active → kết quả theo BR-M6-06 còn chờ quyết định riêng; không tự chọn policy. 7a. Write/commit thất bại → rollback state, Incident và dedupe mark; trả `PERSISTENCE_ERROR`, không publish. |
| **FR/BR liên quan** | FR-M6-03–05; BR-M6-02–09. |

# 4. Thiết kế hành vi (§3.3)

## 4.1 State machine: ChargingPoint, Incident

File nguồn: [`m6_state_machines.drawio`](../../docs/architecture/m6_state_machines.drawio)

![State machine Incident và ChargingPoint](../../docs/figures/m6_state_machines.drawio.png)

> **Trạng thái thiết kế:** Incident lifecycle đã được TV2 chốt theo M6-D07;
> Incident active trùng ở BR-M6-06 vẫn cần quyết định riêng. TV3 đã chốt owner
> split M6-D06 (M6 sở hữu health, M5 sở hữu usage/session); transition details
> của ChargingPoint vẫn cần TV5/M5.
> ChargingPoint diagram là transition
> fragment: nguồn chưa quy định initial state, nên không giả định cổng mới tạo
> luôn ở `Available`. Hình hiện tại chưa vẽ riêng usage state và health state;
> cần được cập nhật theo phương án C sau khi TV5/M5 xác nhận transition contract.

Nhãn trên mũi tên là trigger; guard và action/effect được ghi đầy đủ trong các
bảng. Initial pseudostate của Incident dùng chấm đen đặc. Theo UML, transition
từ initial pseudostate không có trigger hoặc guard; vì vậy sự kiện tạo Incident
được tách thành dòng khởi tạo bên dưới, không gắn lên mũi tên initial.

**Bảng trạng thái và chuyển trạng thái** (baseline dùng để review/test sau khi
được duyệt):

**Incident**

| **Trạng thái hiện tại** | **Sự kiện** | **Điều kiện** | **Trạng thái mới** | **Hành động** |
|----|----|----|----|----|
| Khởi tạo (không phải transition UML) | `incidentReported` | Resource hợp lệ; chưa có Incident active | Reported | Tạo và lưu Incident cùng reporter/reportedAt |
| Reported | `assign` | Đã có assignee | Assigned | Lưu assignee/assignedAt |
| Assigned | `startProgress` | Operator có quyền | InProgress | Lưu startedAt |
| InProgress | `resolve` | Có resolution note | Resolved | Lưu resolvedAt |
| Resolved | `close` | Operator xác nhận | Closed | Lưu closedAt |
| Resolved | `reopen` | Lỗi tái diễn trước khi close | InProgress | Xóa resolvedAt, ghi lý do reopen |

**ChargingPoint**

| **Trạng thái hiện tại** | **Sự kiện** | **Điều kiện** | **Trạng thái mới** | **Hành động** |
|----|----|----|----|----|
| Available | `usageDetected` | Sensor xác nhận đang dùng | InUse | Lưu state; phát ResourceStateUpdated sau commit |
| InUse | `released` | Sensor xác nhận kết thúc | Available | Lưu state; phát ResourceStateUpdated sau commit |
| Available | `deviceFailed` | Reason hợp lệ; không có Incident active | Faulty | Lưu state và tạo Incident Reported; sau commit phát ResourceStateUpdated và ChargerFailed |
| InUse | `deviceFailed` | Reason hợp lệ; không có Incident active | Faulty | Lưu state và tạo Incident Reported; sau commit phát ResourceStateUpdated và ChargerFailed |
| Faulty | `repairStarted` | Incident Assigned hoặc InProgress | UnderRepair | Lưu state; nếu Incident Assigned thì chuyển sang InProgress trong cùng thao tác; phát ResourceStateUpdated sau commit |
| UnderRepair | `repairSucceeded` | Sửa thành công; Incident được resolve | Available | Lưu state; phát ResourceStateUpdated sau commit |
| UnderRepair | `repairFailed` | Có failure reason mới | Faulty | Giữ Incident InProgress, ghi lý do; phát ResourceStateUpdated sau commit |

**Chuyển trạng thái không hợp lệ cần chặn:** mọi transition không có trong các
bảng; đặc biệt `Reported → Resolved`, `Assigned → Closed`, mọi transition đi ra
khỏi `Closed`, `Faulty → Available` trực tiếp. Theo baseline BR-M6-05 đang chờ
duyệt, ChargingPoint ở `Faulty` hoặc `UnderRepair` không được cấp phát; transition
sai trả 409 theo BR-M6-09. Bảng ghi riêng hai nhánh `Available → Faulty` và
`InUse → Faulty` để khớp với hai transition có cùng trigger trên diagram.

## 4.2 Activity diagram: Quy trình xử lý sự cố

File nguồn có thể chỉnh sửa trong draw.io: [`m6_incident_activity.drawio`](../../docs/architecture/m6_incident_activity.drawio)

![Activity diagram xử lý sự cố](../../docs/figures/m6_incident_activity.drawio.png)

Sơ đồ tách hai đầu vào. Nhánh Operator xác thực role, payload và resource; request
không hợp lệ bị từ chối, Incident active dẫn tới `409 ACTIVE_INCIDENT_EXISTS`
theo baseline BR-M6-06 đang chờ TV2 duyệt, còn state transition sai trả 409. Nhánh
Sensor/Simulator kiểm tra adapter, schema và resource; `eventId` trùng là no-op có
structured log. Với event mới, M6 kiểm tra transition riêng rồi mới xét Incident
active. Đặc tả chưa chốt kết quả cho một reading mới khi Incident đã active, nên
nhánh đó vẫn chờ quyết định riêng theo BR-M6-06.

Hai nhánh hợp lệ cùng ghi Incident `Reported`, cập nhật health/latest state M6 nếu
áp dụng, và chỉ ghi nhận `eventId` ở nhánh reading trong transaction. Chỉ sau
commit thành công M6 mới phát `ResourceStateUpdated` khi state đổi cùng failure
event phù hợp; commit lỗi rollback và không publish. Consumer tự xử lý ngoài
boundary M6. Mapping consumer trong ghi chú hình dùng baseline M6-D12 do TV6 chọn;
TV3 review và consumer xác nhận payload vẫn còn cần để coi đây là contract toàn
hệ thống. Theo D08, toàn bộ `/api/m6/**` yêu cầu `OPERATOR`; chỉ mở ngoại lệ
read-only nếu có nhu cầu Student cụ thể, hiện chưa xác định endpoint nào. Quy ước
401/403 theo D09. Đây là mô hình đặc tả, không phải xác nhận chức năng đã được
implement.

## 4.3 Sequence diagram: Sự kiện thiết bị → cập nhật trạng thái → phát sự kiện

> Lifeline phải trùng tên thành phần trong sơ đồ kiến trúc của TV3 và class ở mục 6.

File nguồn: [`m6_device_event_sequence.drawio`](../../docs/architecture/m6_device_event_sequence.drawio)

![Sequence diagram device event](../../docs/figures/m6_device_event_sequence.drawio.png)

Sơ đồ mô tả một vertical slice: Timer kích hoạt simulator; với từng reading,
`DeviceEventService` kiểm tra payload rồi tra cứu resource trước khi kiểm tra
trùng `DeviceReading.eventId`. Duplicate kết thúc trước transition. Với reading
mới, service kiểm tra và áp dụng transition, lưu `ChargingPoint` thành `Faulty`,
rồi tạo Incident `Reported`. Nhánh thành công giả định Simulator bật,
`resourceType = CHARGING_POINT`, resource tồn tại, reading báo lỗi và chưa có
Incident active. Nhánh resource không tồn tại, payload sai, duplicate, transition
sai và write/commit thất bại vẫn được thể hiện như ngoại lệ.

Combined fragment đánh dấu vùng atomic được đề xuất cho M6-D09/D10: dedupe mark,
state write và Incident write. Khi commit thành công, `DeviceEventService` mới gọi
`buildEvents(reading)` và `EventPublisher` chuyển `ResourceStateUpdatedEvent` tới
M1/M8; với failure, chuyển thêm `ChargerFailedEvent` tới M4/M5. Khi commit thất
bại, state, Incident và dedupe mark được rollback, trả
`ProcessingResult.failed(PERSISTENCE_ERROR)` và không publish. Inbound
`DeviceReading.eventId` khác ID được sinh riêng cho từng outbound `DomainEvent`.
Publisher dùng Spring in-process event bus; M6 không gọi service của consumer.
`VehicleFailed` là biến thể tương tự, nằm ngoài scenario. Consumer mapping là đề
xuất M6-D12. Đây là target design, không phải mô tả implementation hiện tại.
Walking skeleton hiện dùng store trong bộ nhớ và publish đồng bộ; listener có
thể lỗi sau khi latest state trong memory đã đổi. Incident write, transaction
rollback và after-commit coordination chưa được implement.

# 5. Đặc tả màn hình gửi TV5 (§3.4)

**Bạn không vẽ mockup.** Bạn chỉ điền bảng dưới đây; TV5 dùng nó để viết prompt cho công cụ AI (Figma AI, Google Stitch…), tạo mockup rồi chỉnh theo design system, sau đó gửi lại cho bạn duyệt.

> Mô tả càng cụ thể thì càng ít phải sửa: nêu rõ trường dữ liệu nào hiện, thứ tự ưu tiên thông tin, thông báo lỗi cần hiện gì, có trạng thái đang xử lý và trạng thái rỗng (chưa có dữ liệu) hay không.

| **Màn hình** | **Actor** | **Thông tin hiển thị** | **Hành động người dùng** | **Phản hồi hệ thống (thành công / lỗi / đang xử lý)** |
|----|----|----|----|----|
| Danh sách Incident | Operator | ID, resource, Hub, severity, status, assignee, reportedAt; bộ lọc status/severity/resource | Lọc, mở chi tiết, tạo báo cáo | Loading skeleton; empty state; tải thành công; lỗi API kèm code/message |
| Chi tiết và workflow Incident | Operator | Timeline trạng thái, reason/description, resource, assignee, timestamps, version, hành động hợp lệ | Assign, Start, Resolve, Reopen, Close | Disable action đang gửi; toast thành công; 409 yêu cầu tải lại; transition sai hiển thị lý do |
| Điều khiển Simulator | Operator | Scheduler enabled/disabled và configured interval (read-only), seed, kịch bản, tick gần nhất, event gần nhất | Chạy deterministic tick/scenario; xem trạng thái | Xác nhận inbound eventId; validation error; trạng thái đang chạy; không cho chạy trùng. D02 chốt chu kỳ demo 5.000 ms; scheduler vẫn được cấu hình tắt mặc định. |

*\[Dán ảnh mockup TV5 gửi lại hoặc link Figma\]*

# 6. Class diagram và method (§3.2)

> Mọi method public ghi signature đầy đủ trên sơ đồ. Mô tả bằng lời chỉ cho 2–4 method nghiệp vụ. Không mô tả getter/setter, CRUD thuần, mapper.

File nguồn: [`m6_class_design.drawio`](../../docs/architecture/m6_class_design.drawio)

![Class diagram đề xuất M6](../../docs/figures/m6_class_design.drawio.png)

Trong sơ đồ, mỗi class có các ngăn tên / thuộc tính / operations; mũi tên nét
đứt là dependency (dùng/gọi), không phải quan hệ sở hữu dữ liệu. Vì Incident
tham chiếu tài nguyên qua cặp `resourceType` + `resourceId`, schema proposal D10
đã được TV4 chấp thuận dùng logical reference, không tạo polymorphic FK cho
resource. M6 sở hữu health state; usage/session thuộc M5.
Operations được ghi đủ kiểu tham số và kiểu trả về; DTO/enum được dùng làm type
nhưng không khai triển thành class riêng.

> Sơ đồ tách rõ walking skeleton đã triển khai khỏi vùng proposal. `SimulatorController`,
> `SimulatorScheduler`, `DeviceSimulatorService`, `SyntheticReadingFactory`,
> `DeviceEventService`, `DemoDeviceCatalog`, hai in-memory store và `EventPublisher`
> khớp code hiện tại. Incident workflow/repositories/entities và ChargingPoint
> persistence vẫn là target design theo D10 đã được TV4 chấp thuận; chưa có M6
> migration. Owner split
> ChargingPoint đã được TV3 chốt theo phương án C.

## 6.1 Entity gửi TV4 (để làm ERD)

| **Entity** | **Thuộc tính chính (kèm kiểu)** | **Khóa / quan hệ** |
|------------|---------------------------------|--------------------|
| Incident | `id: Long` (PK, generated); `resourceType: ResourceType` (NOT NULL); `resourceId: Long` (NOT NULL, logical reference); `hubId: Long` (NOT NULL); `reasonCode: String` (NOT NULL); `description: String?`; `severity: IncidentSeverity` (NOT NULL); `status: IncidentStatus` (NOT NULL); `reporterType: ReporterType` (NOT NULL); `reporterUserId: Long?`; `assigneeUserId: Long?`; `reportedAt: OffsetDateTime` (NOT NULL); `assignedAt/startedAt/resolvedAt/closedAt: OffsetDateTime?`; `version: Long` (NOT NULL, optimistic lock) | TV4 đã chấp thuận proposal D10 (2026-10-21): FK `hubId → hub.id`; `reporterUserId`/`assigneeUserId → app_user.id` nếu user entity hiện hữu hỗ trợ; không tạo polymorphic FK cho `resourceId`; các index `(resource_type, resource_id, status)`, `(hub_id, status)`, `(assignee_user_id, status)`. Migration chưa có. |
| ChargingPoint | `id: Long` (PK); `code: String` (NOT NULL, UNIQUE); `hubId: Long` (NOT NULL); `healthState` thuộc M6; usage/allocation state thuộc mô hình M5; `updatedAt: OffsetDateTime` (NOT NULL); `version: Long` (NOT NULL, optimistic lock) | TV3 đã chốt tách health khỏi usage/session theo M6-D06; TV4 đã chấp thuận proposal D10 (2026-10-21). Chưa có entity hay migration trong walking skeleton; transition contract vẫn cần TV5/M5. |
| DeviceLatestState (D11 addendum) | Một dòng hiện trạng: `resourceType`, `resourceId`, `hubId`, `state`, `batteryLevel: Integer?`, `failureReason: String?`, `observedAt: OffsetDateTime` | TV6 đề xuất unique `(resourceType, resourceId)` và logical resource reference, không polymorphic FK. Addendum chưa được TV4 duyệt; chưa có entity hoặc migration. Không lưu raw reading/tick history. |
| ProcessedDeviceEvent | `eventId: UUID` (đề xuất PK/inbound idempotency key); `processedAt: OffsetDateTime` (NOT NULL); `result: String` (NOT NULL) | Riêng producer-side inbound `DeviceReading` dedupe, khác consumer-side `shared.events.ProcessedEventStore`. Consumer namespaced dedupe đã được TV3 chốt theo D09; TV4 đã chấp thuận model proposal D10. Retention/coupling còn là quyết định triển khai; walking skeleton hiện dùng store trong bộ nhớ. |

> **D10 đã được TV4 chấp thuận (2026-10-21):** TV4 sở hữu Flyway migration theo
> schema proposal; migration chưa được tạo. Cần đồng bộ nếu khi triển khai phát
> sinh khác biệt vật lý đáng kể so với proposal đã chấp thuận.
> được tạo và schema chưa được TV4 duyệt.

`DeviceReading`, `DeviceStateSnapshot`, `ProcessingResult`, `RunScenarioRequest`,
`SimulationRunView`, `SimulatorStatusView`, `ResourceKey` và các enum là runtime
DTO/value objects; chúng không phải JPA entity và không tạo bảng persistence.

## 6.2 Mô tả 2–4 method nghiệp vụ

Hai method đầu bảng Incident là thiết kế proposal, chưa được cài đặt trong
walking skeleton. `processReading` và `runScenario` là các method đã có code.

| **Signature**      | `reportIncident(command: ReportIncidentCommand): Incident` |
|--------------------|---------------------------------------|
| **Class**          | `IncidentService` |
| **Mục đích**       | Xác thực báo cáo, ngăn Incident active trùng và tạo Incident `Reported`. Luồng publication tuân theo transaction order đã được TV3 chốt ở D09; Incident lifecycle D07 và schema D10 đã được chốt nhưng chưa implement. BR-M6-06 về Incident active trùng vẫn cần quyết định riêng. |
| **Tiền điều kiện** | Operator hợp lệ hoặc trusted internal adapter; resource tồn tại; reason code hợp lệ. |
| **Hậu điều kiện**  | Incident được lưu đúng một lần; trả Incident đã tạo cho caller. Device-reading flow không publish từ method này; publication theo thứ tự validate → state → save → publish đã được TV3 chốt, nhưng chưa có code Incident để xác minh. |
| **Ngoại lệ**       | `RESOURCE_NOT_FOUND`, `ACTIVE_INCIDENT_EXISTS`, `INVALID_FAILURE_REASON`. |

| **Signature**      | `transitionIncident(id: Long, command: TransitionIncidentCommand): Incident` |
|--------------------|---------------------------------------|
| **Class**          | `IncidentService` |
| **Mục đích**       | Áp dụng state machine Incident, cập nhật assignee/note/timestamps và health state M6 liên quan theo action. |
| **Tiền điều kiện** | Incident tồn tại; action hợp lệ; `expectedVersion` khớp; `assigneeUserId` có giá trị khi `action = ASSIGN`; `note` bắt buộc cho `RESOLVE` và `REOPEN` theo state table đề xuất. |
| **Hậu điều kiện**  | Incident và health state M6 liên quan được lưu cùng transaction; trả Incident mới. Event publication tuân theo transaction order D09; lifecycle D07/schema D10 đã được chốt nhưng chưa có code để xác minh. |
| **Ngoại lệ**       | `INCIDENT_NOT_FOUND`, `INVALID_STATE_TRANSITION`, `CONCURRENT_UPDATE`. |

| **Signature**      | `processReading(reading: DeviceReading): ProcessingResult` |
|--------------------|---------------------------------------|
| **Class**          | `DeviceEventService` |
| **Mục đích**       | Kiểm tra payload/resource trong catalog demo, loại inbound ID đã xử lý, cập nhật synthetic latest state trong bộ nhớ và phát event qua `EventPublisher` đồng bộ. Failure vehicle phát `VehicleFailed`; failure cổng sạc phát state `FAULTY` cùng `ChargerFailed`. |
| **Tiền điều kiện** | Reading có eventId, hub/resource identity, observed state và thời điểm quan sát hợp lệ; battery nếu có nằm trong `0..100`. |
| **Hậu điều kiện**  | Reading mới được xử lý qua store trong bộ nhớ; duplicate trả `DUPLICATE` và không publish lần nữa; outbound event IDs được trả riêng với inbound ID. Đây không phải DB transaction, rollback hoặc after-commit guarantee. |
| **Ngoại lệ**       | Payload/resource không hợp lệ được trả bằng `ProcessingResult.REJECTED` với reason code; lỗi listener đồng bộ có thể ném ra sau khi in-memory state đã đổi. |

| **Signature**      | `runScenario(scenario: SimulatorScenario, seed: long): SimulationRunView` |
|--------------------|---------------------------------------|
| **Class**          | `DeviceSimulatorService` |
| **Mục đích**       | Chặn hai run đồng thời, sinh readings cho scenario đã chọn, chuyển từng reading qua `DeviceEventService` và lưu summary của run gần nhất. |
| **Tiền điều kiện** | Scenario hợp lệ; seed được cung cấp. Scheduler chỉ gọi `NORMAL_TICK` khi được bật tường minh bằng cấu hình. |
| **Hậu điều kiện**  | Trả run ID, số lượng kết quả và outbound event IDs. Cùng seed cho cùng business payload; mỗi reading được cấp inbound UUID mới. `observedAt` được ghi tại lúc tạo nhưng không bảo đảm khác giữa các lần gọi sát nhau và không dùng làm duplicate key. |
| **Ngoại lệ**       | `SIMULATOR_ALREADY_RUNNING`; lỗi publisher làm run thành `FAILED`. |

> **Type note — proposal:** `TransitionIncidentCommand` gồm `action: IncidentAction`, `assigneeUserId: Long [0..1]`, `note: String [0..1]` và `expectedVersion: Long`. `assigneeUserId` bắt buộc với `ASSIGN`; `note` bắt buộc với `RESOLVE` và `REOPEN` theo state table đề xuất. DTO/query/view types còn lại được dùng trong signature nhưng không khai triển thành class riêng.

Các method đã implement có signature đúng code: `DeviceEventService` cung cấp
cả `processReading(reading: DeviceReading): ProcessingResult` và overload
`processReading(reading: DeviceReading, runId: UUID): ProcessingResult`;
`DeviceSimulatorService` cung cấp `runScenario(scenario: SimulatorScenario,
seed: long): SimulationRunView` và `status(): SimulatorStatusView`.

# 7. Cài đặt (§4.2, §4.3, §4.4)

## 7.1 Phạm vi cài đặt

> Bộ sinh dữ liệu giả lập cho xe/pin, chỗ đỗ và cổng sạc; Spring in-process event bus qua `EventPublisher`/`ApplicationEventPublisher`; service sự cố và màn hình quản lý sự cố. Không thêm message broker ngoài tiến trình.

Ghi chú của người phụ trách: ..............................................................

## 7.2 Sự kiện của module (theo Phụ lục A)

<table>
<colgroup>
<col style="width: 24%" />
<col style="width: 75%" />
</colgroup>
<thead>
<tr>
<th><strong>Chiều</strong></th>
<th><strong>Sự kiện và việc phải làm</strong></th>
</tr>
<tr>
<th><strong>Module này PHÁT</strong></th>
<th><p>ResourceStateUpdated → M1 dashboard, M8 snapshot</p>
<p>VehicleFailed → M3 chuyển Maintenance, M2 xử lý đặt chỗ</p>
<p>ChargerFailed → M5 phân lại phiên sạc, M4 xử lý chỗ đỗ</p></th>
</tr>
<tr>
<th><strong>Module này NHẬN và tự xử lý</strong></th>
<th>(không nhận sự kiện từ module khác — M6 là nguồn phát chính)</th>
</tr>
</thead>
<tbody>
</tbody>
</table>

> Chỉ phát và nhận đúng các sự kiện trên. **Không viết logic nằm trong module của người khác.** Cần thêm sự kiện mới thì báo TV3 cập nhật Phụ lục A.

**Đối chiếu với các template module mới nhất:**

| Event M6 phát | Payload hiện có | Phản ứng do consumer sở hữu | Trạng thái đồng bộ |
|---|---|---|---|
| `VehicleFailed` | `vehicleId`, `hubId`, `reason` cùng `eventId`, `occurredAt` kế thừa | M2 chuyển/hủy Reservation liên quan; M3 chuyển xe sang Maintenance | Khớp M2 và hợp đồng `shared/events`; M6 không sửa Reservation hoặc vehicle lifecycle |
| `ChargerFailed` | `chargingPointId`, `hubId`, `reason` cùng `eventId`, `occurredAt` kế thừa | M4/M5 tự xử lý trong module mình; M3 không xử lý event này | TV3 đã chốt owner split M6-D06 (health M6, usage/session M5); M4/M5 đã xác nhận nhận event ngày 02/10, nhưng reaction runtime chưa được chứng minh |
| `ResourceStateUpdated` | `hubId`, `resourceType`, `resourceId`, `newState` cùng metadata kế thừa | M1 cập nhật dashboard; M8 đánh dấu projection/snapshot source đã thay đổi rồi lấy snapshot nhất quán qua API chỉ đọc khi chạy mô phỏng | Khớp Phụ lục A và không biến chuỗi event thành snapshot nhất quán |

Các consumer phải tự dedupe theo outbound `DomainEvent.eventId`. Inbound
`DeviceReading.eventId` chỉ thuộc pipeline M6 và không được dùng thay ID event
liên module. Spring `ApplicationEventPublisher` là bus trong tiến trình; không
được mô tả như message broker bền vững, hàng đợi hoặc cơ chế retry tự động.

**Baseline reason code/payload do TV6 chọn ngày 2026-10-21 (D04/D12):** reason
codes là uppercase `String` để khớp event class hiện có; giữ nguyên payload
`VehicleFailed(vehicleId, hubId, reason)`,
`ChargerFailed(chargingPointId, hubId, reason)` và
`ResourceStateUpdated(hubId, resourceType, resourceId, newState)`, cùng metadata
`eventId`/`occurredAt`. Vehicle reasons: `MECHANICAL`, `SENSOR_OFFLINE`,
`MANUAL_REPORT`; charger reasons: `OFFLINE`, `OVERHEAT`, `POWER_FAULT`,
`MANUAL_REPORT`. TV3 review và consumer payload acknowledgements vẫn cần được
ghi nhận trước khi coi đây là contract tích hợp đã được mọi module duyệt.

## 7.3 Quyết định cài đặt quan trọng (§4.3)

> Viết 1–2 đoạn: vấn đề kỹ thuật là gì, mình chọn cách nào, vì sao, đánh đổi ra sao. Đây là phần thầy chấm trong mục Implementation.

M6 walking skeleton giữ producer logic trong `m6simulator`: `SyntheticReadingFactory`
tạo fixture theo seed; `DeviceEventService` xác thực payload, kiểm tra catalog,
dedupe inbound ID, cập nhật latest state trong bộ nhớ rồi phát event qua
`shared.events.EventPublisher`; `DeviceSimulatorService` quản lý run/scenario.
`NORMAL_TICK` sinh vehicle có battery, parking space và charging point;
`VEHICLE_FAILURE` phát `VehicleFailedEvent`; `CHARGER_FAILURE` phát
`ResourceStateUpdatedEvent(FAULTY)` và `ChargerFailedEvent`. Theo baseline D04/D12
TV6 chọn ngày 2026-10-21, vehicle reasons là `MECHANICAL`, `SENSOR_OFFLINE`,
`MANUAL_REPORT`; charger reasons là `OFFLINE`, `OVERHEAT`, `POWER_FAULT`,
`MANUAL_REPORT`. `FailureReasonCodes` kiểm tra exact uppercase allowlist theo
resource; các scenario hiện có vẫn dùng `MECHANICAL` và `OFFLINE`. TV5/M5 vẫn
cần xác nhận điều kiện/reaction ChargerFailed, còn TV3 và consumers cần xác nhận
contract liên module.

HTTP điều khiển scenario/status hiện yêu cầu role `OPERATOR`; catch-all
`/api/m6/**` cũng đã được thêm vào `shared/config/SecurityConfig` trên nhánh
tích hợp để khóa các route M6 khác. Thay đổi security này chưa có trên nhánh
feature. Scheduler có adapter nhưng mặc định tắt; D02 đã chốt và code cấu hình
interval demo 5.000 ms. State và inbound
dedupe mất khi process dừng; event publish đồng bộ, không có persistence,
transaction rollback, outbox, after-commit, retry hoặc durability. Ngày
02/10/2026, focused Maven tests đạt 21 test và `mvn -B verify` đạt 30 test,
không failure/error/skip; `ModuleBoundaryTest` cũng đạt. Frontend test command
không tìm thấy test file và thoát 0; frontend build thành công. Compose config
hợp lệ. Smoke trên Compose project riêng `m6-hardening-smoke` chạy PostgreSQL
16: unauthenticated 401, Student 403, Operator 200; ba scenario trả 200; M3
chuyển xe ID 1 sang `MAINTENANCE`; malformed/unknown/missing/null inputs trả
400 `VALIDATION_FAILED`; frontend trả 200. Project, volume và image riêng đã
được xóa sau smoke.

## 7.4 Sai lệch và giới hạn (§4.4) — gửi TV3

| **Chức năng** | **Thiết kế ban đầu** | **Thực tế đã làm** | **Lý do** |
|---------------|----------------------|--------------------|-----------|
| Event producer | Spring event bus và ba contract outbound M6 | Focused tests và Compose/PostgreSQL smoke ngày 02/10/2026 xác nhận ba scenario phát `ResourceStateUpdatedEvent`, `VehicleFailedEvent`, `ChargerFailedEvent` theo fixture | Bus synchronous, process-local, không durable |
| Bộ giả lập IoT | Scenario định trước cho vehicle/battery, parking space và charging point | `NORMAL_TICK`, `VEHICLE_FAILURE`, `CHARGER_FAILURE`; `NORMAL_TICK` phát Vehicle `AVAILABLE`, ParkingSpace `FREE`, ChargingPoint `AVAILABLE`; latest state và dedupe trong bộ nhớ; reason codes được kiểm tra theo resource; seed tái tạo business payload, inbound IDs mới | Walking skeleton, chưa persistence; timestamp không được bảo đảm khác nhau; D11 schema addendum đang chờ TV4 |
| Operator simulator API | POST scenario và GET status, role `OPERATOR` | `/api/m6/**` yêu cầu Operator trên nhánh tích hợp; 200 hợp lệ, 400 validation, 401 unauthenticated, 403 Student, 409 run đang chạy | D08 đã được triển khai trên nhánh tích hợp; chưa có ngoại lệ Student chỉ-đọc được yêu cầu; feature sync còn chờ |
| Scheduler | Một service dùng chung với Manual Adapter và Auto Adapter theo M6-D03 | `@ConditionalOnProperty`; chỉ chạy `NORMAL_TICK` khi bật với interval/seed cấu hình | TV2 chốt interval demo 5.000 ms theo D02; interval đã cấu hình, scheduler mặc định tắt |
| Incident / schema | TV2 chốt actor và lifecycle ở D01/D07; TV6 chọn model D10, TV4 chấp thuận 2026-10-21 | Incident API/JPA chưa triển khai; TV2 severity/mapping/BR-M6-06 và TV4 migration là gate; latest-state schema addendum cũng chờ TV4 | Không tự đặt severity/duplicate policy hoặc tạo migration thuộc TV4 |
| Consumer dedupe | Dedupe theo cặp consumer key + outbound event ID | Nhánh tích hợp local đổi `ProcessedEventStore` sang `markIfNew(consumerKey, eventId)`, M3 dùng stable keys, processor chạy `REQUIRES_NEW`, listener bắt/log lỗi; §8 ghi focused proof. Thay đổi chưa có trên nhánh feature M6 | Shared store/M3 handler chỉ ở nhánh tích hợp; memory-only và mất khi restart |
| Runtime integration | M2/M3/M4/M5/M8 nhận event của M6 | H2 integration test và Compose/PostgreSQL smoke xác nhận M3 nhận `VehicleFailed` và xe ID 1 thành `MAINTENANCE`; M4/M5/M8 runtime reaction chưa được chứng minh | Các module tự triển khai reaction; không có gọi chéo service |

# 8. Test case (§5.3)

> Tối thiểu 8 test case, dùng ít nhất 2 kỹ thuật. Gợi ý cho module này: Hỏng thiết bị; state-transition Incident; kiểm tra sự kiện phát đúng bên nhận và đúng dữ liệu; chống xử lý trùng theo eventId.

| **Mã** | **Tên / mục tiêu** | **FR / UC** | **Kỹ thuật** | **Dữ liệu vào** | **Kết quả mong đợi** | **Kết quả thực tế** | **P/F** |
|----|----|----|----|----|----|----|----|
| TC-M6-01 | Cùng seed giữ nguyên business payload; UUID mới, timestamp không null | FR-M6-03/04 | So sánh theo trường nghiệp vụ | `NORMAL_TICK`, seed 42 hai lần | Business payload giống nhau; inbound IDs khác; cả hai `observedAt` không null, không yêu cầu khác nhau | Pass, focused Maven run 21 test; UUID khác nhau, observedAt không null | P |
| TC-M6-02 | `NORMAL_TICK` có vehicle+battery, parking space, charging point | FR-M6-03/04 | Equivalence / structural | seed 42 | Đúng ba resource type, chỉ vehicle có battery trong `0..100` | Pass, focused Maven run 21 test | P |
| TC-M6-03 | Reading thường phát `ResourceStateUpdatedEvent` đúng payload | FR-M6-03 | Event contract | parking space 1, state `OCCUPIED` | Hub/type/ID/state khớp reading | Pass, focused Maven run 21 test | P |
| TC-M6-04 | Vehicle failure đến M3 và chuyển fixture sang `MAINTENANCE` | FR-M6-05 | Spring event + H2 integration + Compose/PostgreSQL smoke | vehicle ID 1, `MECHANICAL` | M3 listener nhận event và state thành `MAINTENANCE` | Pass, `mvn -B verify` và isolated Compose/PostgreSQL smoke; M3 trả vehicle ID 1 `MAINTENANCE` | P |
| TC-M6-05 | Charger failure phát state `FAULTY` và `ChargerFailedEvent` | FR-M6-05 | Event contract | charging point 1, `OFFLINE` | Có đủ hai event với payload tương ứng | Pass, focused Maven run 21 test và smoke `CHARGER_FAILURE` có đủ hai event | P |
| TC-M6-06 | Gửi lại đúng cùng inbound `eventId` | FR-M6-03 | Idempotency / negative | Cùng một `DeviceReading` hai lần | Lần hai `DUPLICATE`, không phát event thêm | Pass, focused Maven run 21 test | P |
| TC-M6-07 | Battery sai hoặc resource vắng trong catalog | FR-M6-03 | Equivalence partitions | battery 101; parking ID 9 | `REJECTED`, không đổi state và không publish | Pass, focused Maven run 21 test | P |
| TC-M6-08 | Gọi status chưa đăng nhập | API simulator | Security boundary | GET `/api/m6/simulator/status` | HTTP 401 | Pass, `mvn -B verify` (MockMvc) và isolated HTTP smoke: 401 | P |
| TC-M6-09 | Student gọi status simulator | API simulator | Security boundary | role `STUDENT` | HTTP 403 | Pass, `mvn -B verify` (MockMvc) và isolated HTTP smoke: 403 | P |
| TC-M6-10 | Operator đọc status và chạy scenario | API simulator | Security boundary / happy path | role `OPERATOR`, seed 42 | HTTP 200 cho GET/POST | Pass, `mvn -B verify` (MockMvc) và isolated HTTP smoke: 200 | P |
| TC-M6-11 | Scheduler property tắt | FR-M6-04 | Configuration boundary | `scheduler-enabled=false` | Scheduler bean không được tạo | Pass, `mvn -B verify` (Spring context test) | P |
| TC-M6-12 | Scheduler bật với cấu hình tường minh | FR-M6-04 | Adapter delegation | interval/seed khai báo trong test | Scheduler gọi pipeline dùng chung với `NORMAL_TICK` | Pass, `mvn -B verify` (Spring context test) | P |
| TC-M6-13 | `NORMAL_TICK` phát đúng contract state của ba tài nguyên | FR-M6-03/04 | Pipeline integration + structural | seed 42 | Vehicle `AVAILABLE`, ParkingSpace `FREE`, ChargingPoint `AVAILABLE`; phát ba state event tương ứng | Pass, focused Maven run 21 test xác nhận đúng ba state/event, gồm ParkingSpace `FREE` | P |
| TC-M6-14 | Factory ném exception rồi simulator phục hồi | FR-M6-04 | Failure injection / recovery | Factory ném ở lần đầu, thành công lần sau | Lần đầu run `FAILED`, completedAt có, readingCount=0; lần sau chạy thành công | Pass, focused Maven run 21 test; factory exception ghi FAILED và run sau thành công | P |
| TC-M6-15 | Hai scenario không chạy chồng nhau | API simulator | Concurrency / negative | Run thứ nhất giữ bằng latch; request thứ hai trong lúc chạy | Service từ chối; API trả 409 `SIMULATOR_ALREADY_RUNNING`; sau run đầu guard được giải phóng | Pass, focused Maven run 21 test; latch concurrency và MockMvc 409 `SIMULATOR_ALREADY_RUNNING` | P |
| TC-M6-16 | Scenario/body sai hoặc thiếu seed | API simulator | API validation / negative | Unknown scenario, malformed/empty JSON, `{}`, seed `null` | HTTP 400 `VALIDATION_FAILED`; details chỉ rõ scenario hợp lệ hoặc request/seed | Pass, `mvn -B verify` (MockMvc) và isolated HTTP smoke: unknown/malformed/empty/missing/null đều 400 | P |
| TC-M6-17 | Lỗi ở giữa batch vẫn giữ counters của reading đã xử lý | FR-M6-04 | Failure injection | Reading đầu thành công, reading tiếp theo ném lỗi | Run `FAILED`, readingCount giữ batch size và processedCount giữ giá trị đã xong | Pass, focused Maven run 21 test; failure giữa batch giữ reading/processed counters | P |
| TC-M6-18 | Consumer dedupe theo stable key | M6-D09 | Dedupe pair / negative | Cùng outbound `eventId`, cùng consumer key rồi key khác | Cùng key no-op; key khác vẫn được xử lý | Pass, `ProcessedEventStoreTest` trong focused 14-test run và `mvn -B verify` integration | P |
| TC-M6-19 | Lặp lại `VehicleFailed` không đổi M3 lần hai | M6-D05/D09 | H2 event integration / idempotency | Cùng `VehicleFailedEvent` được publish hai lần | Xe vào `MAINTENANCE`; version không tăng ở lần nhận trùng | Pass, `DeviceEventM3IntegrationTest` trong focused 14-test run và `mvn -B verify` integration | P |
| TC-M6-20 | M3 consumer lỗi không thoát ra publisher | M6-D09 | Failure injection | Processor ném exception trong handler | Listener bắt/log lỗi và lời gọi handler không ném exception | Pass, `VehicleEventHandlerTest` trong focused 14-test run và `mvn -B verify` integration | P |
| TC-M6-21 | Threshold `VehicleLowBattery` do M3 sở hữu | M6-D04 | Boundary values | Trả xe ở 19% và đúng 20%; threshold cấu hình 20% | 19% phát `VehicleLowBattery`; 20% chỉ phát `VehicleReturned` | Pass, `VehicleServiceTest` trong focused 14-test run và `mvn -B verify` integration | P |
| TC-M6-22 | Reason code uppercase hợp lệ theo resource; code sai loại hoặc lowercase bị từ chối | M6-D04/D12 | Equivalence partitions / negative | Vehicle `MECHANICAL`, `SENSOR_OFFLINE`, `MANUAL_REPORT`; Charger `OFFLINE`, `OVERHEAT`, `POWER_FAULT`, `MANUAL_REPORT`; lowercase và code sai loại | Code hợp lệ được xử lý; code sai bị `REJECTED`, không lưu state/publish | Pass, `DeviceEventServiceTest`; `mvn -B verify` ngày 21/10/2026: 41 tests, 0 failures/errors | P |
| TC-M6-23 | Cập nhật latest snapshot cùng resource không giữ history trong walking skeleton | M6-D11 | State upsert | Hai reading cùng `VEHICLE/1`, state/pin khác nhau | Một snapshot trong memory store với state/pin của reading mới nhất | Pass, `DeviceEventServiceTest`; `mvn -B verify` ngày 21/10/2026: 41 tests, 0 failures/errors. Chưa chứng minh persistence DB | P |
| TC-M6-24 | Toàn namespace M6 áp dụng D08 OPERATOR matcher | M6-D08 | Security boundary / negative | Route chưa map `/api/m6/unmapped-access-check`; unauthenticated, STUDENT, OPERATOR | 401, 403; Operator đi qua authorization và nhận 404 do route chưa tồn tại | Pass, `SimulatorControllerSecurityTest`; `mvn -B verify` ngày 21/10/2026: 41 tests, 0 failures/errors | P |

TC-M6-18–21 là proof consumer/M3 D09 trên working tree
`codex/integrate-m6-m3-20261019`; shared/M3 changes chưa có trên feature branch.
TC-M6-22/23 (M6-owned D04/D11 in-memory behavior) đã chạy trên cả hai nhánh
local. TC-M6-24 kiểm tra matcher trong shared `SecurityConfig`, chỉ có trên
nhánh tích hợp. Không gộp các proof consumer với inbound `DeviceReading`
dedupe: đó là hai store và hai boundary khác nhau.

Verification evidence ngày 02/10/2026: focused command
`mvn -B -Dtest='*Simulator*,*DeviceEvent*,*EventStore*' test` đạt 21 test;
`mvn -B verify` đạt 30 test, không failure/error/skip, bao gồm
`ModuleBoundaryTest`. Frontend `npm ci` đạt; `npm run test -- --run
--passWithNoTests` thoát 0 và báo không có test file; `npm run build` thành
công. Compose config hợp lệ. Isolated Compose project `m6-hardening-smoke`
build và chạy backend, frontend cùng PostgreSQL 16; Operator login và status
trả 200, unauthenticated trả 401, Student trả 403. Ba scenario trả 200;
`NORMAL_TICK` có ba reading/state event, `VEHICLE_FAILURE` làm M3 chuyển xe
ID 1 sang `MAINTENANCE`, và `CHARGER_FAILURE` có hai event mong đợi. Unknown
scenario, JSON malformed/rỗng, thiếu seed và seed null đều trả 400
`VALIDATION_FAILED`. Frontend trả 200. Log đối chiếu được `runId`, inbound ID
và outbound ID. Chỉ project/network/volume/image smoke riêng được dừng và xóa.

Verification evidence ngày 20/10/2026 trên working tree local (không commit):
focused Maven command chạy 14 test, không failure/error/skip; `mvn -B verify`
đạt 37 test trên `codex/integrate-m6-m3-20261019` và 31 test trên
`feature/m6-iot-simulator-event-bus`. Trên cả hai branch, frontend `npm ci`,
`npm run test -- --run --passWithNoTests`, `npm run build` và Compose config đều
thoát 0 theo CI/demo runbook; frontend hiện chưa có test file. `npm ci` báo 16
vulnerabilities trong lockfile/dependency tree (7 moderate, 7 high, 2 critical);
dependency remediation chưa nằm trong phạm vi công việc này.

# 9. Checklist hoàn thành module

Người review chéo tick vào các ô sau (tiêu chí ở mục 12 tài liệu Phân công):

- [ ] FR, BR, use case có mã đúng quy ước, đã vào ma trận truy vết của TV7.

- [ ] Có ít nhất một FR non-interactive với actor hệ thống trên use-case diagram.

- [ ] Đủ use-case diagram, activity, sequence, state machine như mục 10 tài liệu Phân công.

- [ ] Tên class, trạng thái, sự kiện khớp giữa sơ đồ, code và report.

- [ ] Class diagram có signature đầy đủ; đã mô tả 2–4 method nghiệp vụ.

- [ ] Entity đã gửi TV4; khớp ERD và code.

- [ ] Đã gửi bảng đặc tả màn hình cho TV5 và duyệt lại mockup TV5 tạo.

- [ ] Code đã merge qua pull request, chạy được trên bản tích hợp, CI xanh.

- [ ] Sự kiện phát/nhận đúng Phụ lục A; không viết logic trong module người khác.

- [ ] Có ≥ 8 test case dùng ≥ 2 kỹ thuật, đã chạy và ghi kết quả.

- [ ] Đã ghi sai lệch §4.4 và gửi TV3.

- [ ] Đã viết trang tóm tắt ôn tập (C-05).

# 10. Template report của module

Phần này là **bản nháp report** của module. Mọi thứ đã điền ở mục 1–8 chỉ là dữ liệu thô; ở đây viết thành văn để TV1 ghép vào report chung, nên bắt đầu viết càng sớm càng tốt.

> Quy tắc chung: mỗi hình đều phải có ít nhất một đoạn giải thích đi kèm, không dán hình trơ. Viết bằng câu hoàn chỉnh, không gạch đầu dòng cụt. Tên class, trạng thái, sự kiện phải trùng với code.

| **Mục report** | **Lấy dữ liệu từ** | **Viết bao nhiêu** |
|----|----|----|
| §2.2 Functional requirements | Mục 1 | Bảng FR + 1 đoạn dẫn |
| §2.4 Business rules | Mục 2 | Bảng BR + 1 đoạn dẫn |
| §2.5 Use-case modeling | Mục 3 | 1 hình + bảng đặc tả + 1–2 đoạn |
| §3.2 Structural design | Mục 6 | 1 hình class + bảng method + 1 đoạn |
| §3.3 Behavioral design | Mục 4 | 3 hình + mỗi hình 1 đoạn |
| §3.4 UI design | Mục 5 | Ảnh mockup + 1 đoạn (gửi TV5) |
| §4.3 Quyết định cài đặt | Mục 7.3 | 1–2 đoạn |
| §4.4 Sai lệch, giới hạn | Mục 7.4 | Bảng + 1 đoạn (gửi TV3) |
| §5.3 Test cases | Mục 8 | Bảng test + 1 đoạn về kỹ thuật đã dùng |

## 10.1 §2.2 — Yêu cầu chức năng của module

> Mở đầu 2–3 câu: module này phục vụ ai, giải quyết phần nào của bài toán. Sau đó dẫn vào bảng FR (chép từ mục 1). Nêu rõ FR nào là non-interactive và do tác nhân nào kích hoạt.

M6 phục vụ hai nhóm tác nhân. Operator dùng module để báo cáo, phân công và theo
dõi sự cố; Sensor/Simulator và Timer/Scheduler cung cấp luồng cập nhật thiết bị
không cần thao tác người dùng. Phạm vi của M6 kết thúc ở việc ghi nhận
Incident/health state do M6 sở hữu và phát fact liên module, không bao gồm logic
đặt chỗ, vòng đời xe, chỗ đỗ hoặc phiên sạc.

Bảng FR tại mục 1 xác định năm yêu cầu chức năng bắt buộc. FR-M6-01 và
FR-M6-02 là luồng tương tác của Operator. FR-M6-03 đến FR-M6-05 là các luồng
non-interactive, lần lượt nhận readings về xe/pin, chỗ đỗ, cổng sạc; được kích
hoạt theo chu kỳ; và cô lập lỗi thiết bị. TV3 đã chốt ranh giới event ở D05 và
health/usage ownership ở D06. TV6 đã chọn model proposal làm baseline D10; physical
schema đã được TV4 chấp thuận; Flyway migration vẫn chờ được triển khai.

## 10.2 §2.4 — Luật nghiệp vụ

> Dẫn bảng BR (mục 2). Với mỗi luật quan trọng, giải thích ngắn vì sao có luật đó — thường gắn với tài nguyên dùng chung hoặc tình huống tranh chấp.

Các luật tại mục 2 gồm quyết định đã được TV2/TV3 chốt và các proposal còn mở.
TV2 đã chấp thuận chu kỳ demo 5.000 ms ở BR-M6-02; interval đã đặt,
scheduler tắt mặc định và khi bật cần seed tường minh. Walking skeleton
chỉ kiểm tra adapter, chưa chứng minh đã đáp ứng NFR thời gian thực. BR-M6-04
(Incident) đã được chốt nhưng chưa implement; BR-M6-05 còn cần TV5/M5 cho
transition ChargingPoint.

BR-M6-06 vẫn là proposal chưa được TV2 duyệt: giới hạn một Incident active cho
mỗi resource. BR-M6-07 ghi rõ
inbound `DeviceReading.eventId`, thứ tự dedupe trước transition và atomic write
proposal; publish chỉ sau commit. D09 đã chốt quy ước lỗi, consumer dedupe và
transaction/error handling của consumer; atomic write cho state/Incident M6 vẫn
chưa được implement vì latest-state schema addendum chờ TV4 và Incident policy
chờ TV2. D01, D02, D07 và D08
đã được TV2 chốt; D04/D11/D12 có baseline implement phía M6 do TV6 chọn, nhưng
xác nhận liên module tương ứng vẫn còn mở. BR-M6-06 (Incident active trùng) và
ChargingPoint transition details cũng cần quyết định/phối hợp riêng.

## 10.3 §2.5 — Mô hình use case

> Chèn use-case diagram của module, đánh số hình. Một đoạn giải thích: có những actor nào, vì sao có actor hệ thống, quan hệ «include»/«extend» dùng ở đâu. Sau đó là các bảng đặc tả use case (mục 3.2), tối thiểu 3 bảng. Nhấn mạnh luồng ngoại lệ về tranh chấp tài nguyên.

Hình use-case ở mục 3.1 đặt ba actor bên ngoài boundary M6. Association actor–use
case dùng đường liền không mũi tên. UC-M6-01 và UC-M6-03 «include» kiểm tra
payload/tài nguyên; UC-M6-04 «include» UC-M6-03 vì simulation tick đi qua cùng
luồng xử lý reading. UC-M6-05 «extend» UC-M6-03 với điều kiện reading xác nhận
lỗi; vì vậy mũi tên nét đứt hướng từ use case mở rộng về use case gốc. Event
publication được ghi theo transaction order D09 trong UML note, không vẽ tên
method hay chữ ký hàm thành use case. M2–M5 chỉ nhận event và tự thực hiện phản
ứng bên ngoài boundary M6. Các quyết định D01/D02/D07/D08 đã có phê duyệt; các
quyết định còn mở và phần proposal tương ứng được liệt kê trong decision log.

Ba bảng tại mục 3.2 mô tả đầy đủ tiền điều kiện, hậu điều kiện, trigger, luồng
chính, luồng thay thế và ngoại lệ. Tình huống tài nguyên đang được đặt hoặc đang
sạc không làm M6 sửa reservation/session: M6 chỉ cô lập health state, tạo
Incident và phát VehicleFailed hoặc ChargerFailed; M2–M5 quyết định phản ứng
trong transaction của module mình. Payload sai, resource không tồn tại, ghi
trùng hoặc transition sai đều kết thúc mà không publish event từ dữ liệu chưa
được lưu.

## 10.4 §3.2 — Thiết kế cấu trúc (class và method)

> Chèn class diagram của module. Một đoạn: các lớp chính đảm nhiệm gì, lớp nào chứa business logic, quan hệ với lớp của module khác. Sau đó là bảng mô tả 2–4 method nghiệp vụ (mục 6.2). Ghi rõ entity nào đã đưa vào ERD tổng của TV4.

Class diagram tại mục 6 có hai vùng riêng. Vùng xanh mô tả code đã triển khai:
`SimulatorController`, `SimulatorScheduler`, `DeviceSimulatorService`,
`SyntheticReadingFactory`, `DeviceEventService`, `DemoDeviceCatalog`, hai store
trong bộ nhớ và `EventPublisher` dạng concrete Spring component. Nét đứt thể hiện
dependency giữa các lớp này. Vùng vàng giữ Incident controller/service/repository,
Incident, ChargingPoint và ProcessedDeviceEvent dưới nhãn proposal; các dependency
ở vùng này không phải implementation hiện hữu.

M6 đã có producer code và event integration; Incident service/API/JPA, ChargingPoint
persistence và migration chưa được cài đặt. TV6 đã chọn Incident,
ChargingPoint health state và `ProcessedDeviceEvent` trong proposal làm baseline
M6-D10, TV4 đã chấp thuận schema proposal ngày 2026-10-21; `ProcessedDeviceEvent`
vẫn khác consumer-side `shared.events.ProcessedEventStore`. Không có JPA entity
hoặc migration M6; TV4 cần triển khai migration. Ranh giới event D05, owner split
D06 và consumer dedupe D09 đã được TV3 chốt.

## 10.5 §3.3 — Thiết kế hành vi

> Ba tiểu mục: state machine, activity, sequence. Mỗi hình kèm một đoạn giải thích. Với state machine, nêu các chuyển trạng thái không hợp lệ bị chặn và vì sao. Với sequence, chỉ rõ chỗ nào phát hoặc nhận sự kiện của module khác.

State machine tại mục 4.1 trình bày Incident và các transition fragment của
ChargingPoint. Mũi tên Incident bắt đầu bằng initial pseudostate không mang
trigger; `incidentReported` được mô tả là thao tác tạo Incident ở Reported bên
ngoài state machine. Incident đi theo whitelist transition, có nhánh
Resolved → InProgress để reopen và không có transition đi ra khỏi Closed.
ChargingPoint có đủ hai nhánh Available/InUse → Faulty; transition repair và
guard/action của từng cạnh khớp với bảng. Các chuyển không nằm trong bảng bị
từ chối theo baseline. TV3 đã chốt ownership/separation theo M6-D06, nhưng hình
hiện tại chưa tách riêng health và usage; transition details cần TV5/M5. TV2 đã
chốt Incident lifecycle theo D07; state table/diagram vẫn chưa được implement.

Activity diagram tại mục 4.2 tách luồng Operator khỏi readings của
Sensor/Simulator. Nhánh manual kiểm tra quyền, payload/resource, Incident active
rồi transition; nhánh device kiểm tra schema/resource, nhận diện duplicate
`eventId` trước transition, sau đó xét Incident active. Duplicate là no-op có
structured log; payload/resource/transition lỗi không ghi và không publish.
Write state/Incident và đánh dấu inbound event nằm trong atomic boundary đề xuất;
chỉ nhánh commit thành công mới publish. Kết quả khi Incident active còn chờ quyết
định riêng theo BR-M6-06. Quyền D08 đã được TV2/TV3 chốt; catch-all `/api/m6/**`
đã được thêm vào SecurityConfig trên nhánh tích hợp.

Sequence diagram tại mục 4.3 là target design cho một `ChargingPoint` failure,
không phải execution trace của walking skeleton. TV3 đã chốt consumer dedupe theo
`(consumerKey, eventId)` cùng nguyên tắc transaction/error handling cho consumer;
M3 implementation có trên nhánh tích hợp local, còn nhánh feature giữ trạng thái
trước đồng bộ. Atomic producer state/Incident write, commit/rollback và publication
boundary còn phụ thuộc việc implement persistence theo D10 đã được chấp thuận và
semantics thực tế của `EventPublisher`.
Code hiện tại xác thực reading, kiểm tra duplicate, ghi latest state vào process
memory rồi phát event đồng bộ; listener failure có thể xảy ra sau memory write.
Inbound `DeviceReading.eventId` khác outbound `DomainEvent.eventId`. Chỉ M3
`VehicleFailed` reaction đã có integration proof; M4/M5/M8 chưa được chứng minh.

## 10.6 §3.4 — Giao diện (gửi TV5 ghép)

> Với mỗi màn hình chính: ảnh mockup do TV5 tạo, thông tin hiển thị, hành động người dùng làm được, hệ thống phản hồi ra sao khi thành công và khi lỗi. Một câu giải thích lựa chọn thiết kế, ví dụ vì sao hiển thị mức pin bằng thanh màu thay vì số. TV5 ghép các phần này thành §3.4, nhưng bạn vẫn là người trả lời khi thầy hỏi về màn hình của module mình.

*\[Viết vào đây\]*

## 10.7 §4.3 — Quyết định cài đặt quan trọng

> Đây là phần ăn điểm mục Implementation. Viết theo mạch: vấn đề kỹ thuật là gì → các cách giải quyết có thể → nhóm chọn cách nào → vì sao → đánh đổi gì. Nhắc tên method đã mô tả ở mục 6.2 để report nhất quán.

M6 chọn Spring event bus trong tiến trình thay vì thêm message broker. API dùng
chung `EventPublisher.publish(DomainEvent)` giữ các module phụ thuộc vào
`shared/events`, không gọi service nội bộ của nhau. Focused tests và Compose
smoke với PostgreSQL xác nhận pipeline scenario → `processReading(...)` → publish
chạy được; M3 nhận `VehicleFailed` và chuyển fixture vehicle ID 1 sang
`MAINTENANCE`. Đánh đổi là không có durability, dead-letter queue hay retry
tự động như broker ngoài tiến trình, và M4/M5/M8 runtime reaction chưa được
chứng minh. Khi phát event, inbound UUID được dùng riêng cho dedupe của producer;
mỗi outbound event có UUID riêng. Timestamp ghi thời điểm tạo, không phải unique key.

## 10.8 §4.4 — Sai lệch và giới hạn (gửi TV3)

> Chép bảng ở mục 7.4 và viết một đoạn ngắn thừa nhận thẳng thắn phần nào chưa làm được, vì sao. Guideline chấm điểm sự trung thực ở đây, giấu đi thì mất điểm.

Sai lệch hiện tại được ghi ở mục 7.4: walking skeleton M6 đã phát event trong
tiến trình, có focused tests, và đã qua isolated Compose/PostgreSQL smoke.
State/dedupe chỉ ở memory, publish không durable, Incident workflow/JPA chưa có;
consumer namespaced dedupe mới có trên nhánh tích hợp local. Smoke chứng minh M3
handler xử lý `VehicleFailed`; M4/M5/M8 runtime reaction chưa được chứng minh.
D04/D11/D12 đã có baseline TV6 để implement phía M6, nhưng owner review/consumer
acknowledgements chưa hoàn tất; ChargingPoint transition details và BR-M6-06
cũng còn mở. D04 reason-code allowlist đã được thêm vào code tích hợp; D11
latest-state schema addendum vẫn cần TV4 duyệt, còn D10 Flyway migration chưa
tồn tại. D08 catch-all security matcher mới có trên nhánh tích hợp.

## 10.9 §5.3 — Kiểm thử module

> Một đoạn: đã dùng kỹ thuật thiết kế test nào và vì sao phù hợp với module này (ví dụ state-transition vì có vòng đời trạng thái). Sau đó là bảng test case (mục 8) kèm kết quả chạy. Nêu lỗi đáng chú ý đã phát hiện và cách xử lý.

*\[Viết vào đây\]*
