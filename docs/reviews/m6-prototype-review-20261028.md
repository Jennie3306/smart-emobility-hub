# Review prototype M6 — 28/10/2026

Kết luận: bố cục hai tab Incident/Simulator dùng được làm cơ sở giao diện M6.
Cần sửa các điểm dưới đây trước khi nối API. Đây là review prototype HTML,
không phải kết quả kiểm thử frontend production hoặc bản ghép các module.
Không sửa source TV5 và không chốt thay các policy còn mở.

## Phiên bản và cách kiểm tra

- Prototype TV5: branch `feature/m5-design-system`, revision
  `5a05df1bfb3a59dcd750d4ed77481e0426ecc4f1`,
  [file HTML gốc](https://github.com/Jennie3306/smart-emobility-hub/blob/5a05df1bfb3a59dcd750d4ed77481e0426ecc4f1/docs/design/Smart_E_Mobility_Hub_UI_Mockups_DesignSystem.html).
  SHA-256 của bản HTML đã mở:
  `e1c1787707c569b072728cc0ea86d2b719f95c9d3e661dd7d5c71b1155c1ae09`.
- Code đối chiếu: `feature/m6-iot-simulator-event-bus`, HEAD `5583c1c`,
  kèm phần D07 domain chưa commit trong lượt này. Không dùng code của nhánh
  tích hợp để khẳng định capability đã có trên feature.
- Authority: [FR/BR, UC và D07](../../DESCRIPTION/Template_report/Template_M6.md),
  [các quyết định còn mở](../proposals/m6-open-decisions.md),
  [SimulatorController](../../backend/src/main/java/com/hcmut/emobility/m6simulator/controller/SimulatorController.java),
  [SimulatorScenario](../../backend/src/main/java/com/hcmut/emobility/m6simulator/domain/SimulatorScenario.java),
  [request](../../backend/src/main/java/com/hcmut/emobility/m6simulator/dto/RunScenarioRequest.java),
  [status](../../backend/src/main/java/com/hcmut/emobility/m6simulator/dto/SimulatorStatusView.java),
  [run response](../../backend/src/main/java/com/hcmut/emobility/m6simulator/dto/SimulationRunView.java).
- Playwright/Chromium mở bản HTML nguyên trạng qua local static server,
  viewport 1440×1000. Các thao tác và ảnh chụp được thực hiện tối 27/10/2026
  (UTC+7); biên bản hoàn thiện ngày 28/10. Chỉ thay đổi dữ liệu demo trong trang.
  [Network capture](../../DESCRIPTION/Template_report/Evidence/M6_IoT/records/verification-excerpts-20261028.txt)
  chỉ có HTML/font, không có request `/api/` trong các thao tác đã kiểm tra.
  Vì thế thông báo thành công demo không chứng minh API/DB/event đã chạy.

## Đối chiếu và việc cần sửa

P1: cần xử lý trước khi triển khai luồng tương ứng. P2: cần xử lý trước khi
nghiệm thu giao diện. Đây là mức ưu tiên review, không phải severity Incident.

| ID | Mức | Quan sát và căn cứ | Đề xuất cụ thể |
|---|---|---|---|
| UI-M6-01 | P1 | D07 cho phép `Resolved → InProgress`, nhưng `viewM6()` (dòng 210) chỉ dựng nút Close cho Resolved. Đã bấm Resolve ở INC-203 và thấy Resolved chỉ có Close. | Hiện cả Reopen và Close ở Resolved; Closed không có transition. Khi API có, render trạng thái từ response; không suy rằng reopen khôi phục health hoặc ghi timestamp theo một policy chưa duyệt. |
| UI-M6-02 | P1 | Simulator có bốn lựa chọn Normal campus demand / Charger offline / Low battery vehicle / Parking sensor drift; API chỉ nhận `NORMAL_TICK`, `VEHICLE_FAILURE`, `CHARGER_FAILURE`. Thiếu lựa chọn vehicle failure, còn low-battery/drift không có scenario API tương ứng. Seed demo `261-05` không phải JSON số `Long`; tick count 1/5/10 không có trong request. | Dùng ba scenario hiện có, label có thể dịch nhưng value phải map đúng enum. Nhập seed số nguyên theo `Long`, gửi `{ "seed": 42 }`. Bỏ/khóa tick count và scenario chưa hỗ trợ; không tự lặp POST hoặc mở rộng backend contract trong lượt làm UI. |
| UI-M6-03 | P1 | Scheduler enabled, interval 5.000 ms, last tick và thông báo run thành công đều là dữ liệu cố định. Code mặc định tắt scheduler; status có `schedulerEnabled`, `intervalMs` (có thể null), `state`, `latestRun`. Run handler (dòng 249) chỉ thay HTML/toast. | Đọc GET status và POST scenario thật. Render IDLE/RUNNING/SUCCEEDED/FAILED, latestRun, counts, times, outbound events và error; chỉ báo thành công từ kết quả API. Khi chưa có run hoặc interval null phải hiển thị được. Không thêm nút bật scheduler vì API hiện không có thao tác đó. |
| UI-M6-04 | P1 | Assign hiện tự gán Current operator; chưa có chọn người, detail/timeline, version hoặc đường xử lý conflict. D07 domain đã có nhưng Incident controller/service/DB chưa có. FR-02 và target command trong report yêu cầu gán người, xem dữ liệu và transition với `expectedVersion`. | Chuẩn bị UI chọn assignee, hiển thị detail/version và gửi expectedVersion sau khi DTO/API được triển khai. Bổ sung pending/error, 400/404 và 409 rồi reload dữ liệu mới; không đổi local row như thể đã lưu. Quy tắc timestamp khi reopen, assignee hợp lệ và health coupling cần đồng bộ trước khi khóa assertion. Đây là thiếu sót của luồng thiết kế, không phải lỗi endpoint đang chạy. |
| UI-M6-05 | P2 | Chọn filter Closed nhưng cả ba row Reported/InProgress/Assigned vẫn hiện. Có control filter nhưng handler chưa lọc danh sách. | Thực hiện lọc status/severity/resource, cả tổ hợp và tập rỗng; khi Incident API có thì đối chiếu TC-M6-32. Sửa contract trước khi ghi case API pass. |
| UI-M6-06 | P2 | Form report có Hub, resource type/ID, severity và ô gộp Reason & description; không có reasonCode riêng. Có Parking sensor trong khi FR-01 chỉ cho báo xe/cổng sạc. Gửi ID `NOT-A-RESOURCE` với mô tả vẫn tạo row demo thành công. Severity Low/Medium/High và default Medium chưa có quyết định TV2. | Giới hạn form theo FR-01; tách reasonCode khỏi description, map display code sang ID thật của catalog và validate ở server. Có phản hồi 400 payload/404 resource absent. Giữ severity/default/mapping và xử lý active Incident ở trạng thái chờ TV2, không dùng ba giá trị demo làm vocabulary đã duyệt. Không coi kiểm tra ID trên demo là proof resource validation. |
| UI-M6-07 | P2 | Recent stream đặt `VehicleLowBattery` cạnh events M6 và ghi emitted to M3/M5; producer event này thuộc M3. Trong M6, pin thấp là reading/state update, không phải vehicle failure. Các ID EVT-993x, reason `offline`, state `Available` chỉ là mẫu hiển thị. `SimulationRunView.outboundEvents` hiện chỉ có ID/type/resourceType/resourceId, không có reason/newState/hub/occurredAt cho từng event. | Nếu hiển thị stream toàn hệ thống phải ghi nguồn M3 và có nguồn dữ liệu riêng; không trình bày như output của M6. UI nối API hiện có chỉ hiển thị các field response cung cấp. UUID, numeric ID và reason uppercase thuộc wire contract; label thân thiện được phép nhưng phải có mapping, không gửi nguyên chuỗi demo. Không bịa endpoint stream hay field response. |

## Bằng chứng thao tác

| Bằng chứng | Thao tác / kết quả quan sát | Artifact |
|---|---|---|
| E01 | M6 → filter Status = Closed; bảng vẫn giữ Reported/InProgress/Assigned. | [Ảnh](../../DESCRIPTION/Template_report/Evidence/M6_IoT/screenshots/prototype/01-closed-filter.png) |
| E02 | Resolve INC-203; row thành Resolved, chỉ có Close, thiếu Reopen. | [Ảnh](../../DESCRIPTION/Template_report/Evidence/M6_IoT/screenshots/prototype/02-resolved-no-reopen.png) |
| E03 | Report incident; kiểm tra fields/options/default. Submit ID không tồn tại `NOT-A-RESOURCE` và mô tả không rỗng tạo row demo Reported. Ảnh/snapshot form ghi trước submit; snapshot sau submit ghi ID INC-208 và resource không hợp lệ. | [Ảnh form](../../DESCRIPTION/Template_report/Evidence/M6_IoT/screenshots/prototype/03-report-form.png), [sau submit](../../DESCRIPTION/Template_report/Evidence/M6_IoT/records/verification-excerpts-20261028.txt) |
| E04 | Simulator events: kiểm tra badge, seed, scenario, tick count và event mẫu. | [Ảnh](../../DESCRIPTION/Template_report/Evidence/M6_IoT/screenshots/prototype/04-simulator-contract.png) |
| E05 | Chọn Charger offline, 10 ticks → Run; trang báo thành công ngay, network không có API. | [Ảnh](../../DESCRIPTION/Template_report/Evidence/M6_IoT/screenshots/prototype/05-demo-run.png), [network](../../DESCRIPTION/Template_report/Evidence/M6_IoT/records/verification-excerpts-20261028.txt) |

## Phần có thể giữ và các gate còn mở

Giữ bố cục Operator, hai tab tách workflow khỏi simulator, resource/Hub context,
badge trạng thái, read-only scheduler và cảnh báo pin thấp không phải lỗi phần
cứng. Chu kỳ 5.000 ms khớp D02. Role switch trong prototype là công cụ demo;
không xem nó như cơ chế authorization production hoặc kết luận lỗ hổng bảo mật.

TV6 có thể triển khai trước phần simulator UI theo hai API hiện có và D07 state
presentation. Tuy nhiên lượt này chỉ review, chưa làm frontend. Trước khi hoàn
thiện Incident UI cần: TV2 chốt severity/mapping/active-duplicate; TV4 giao D10
migration và duyệt D11 addendum; TV3 + TV5/M5 chốt health/usage/recovery contract.
Không tự cho Resolve/Close/Reopen đổi health, hủy reservation hoặc phân phiên sạc.

Kết quả review không chuyển TC-M6-25–63 sang Pass. Những case này có oracle
API/DB/concurrency/publication rộng hơn các thao tác trên HTML demo. Sau khi
sửa và triển khai UI cần kiểm lại desktop/responsive, loading/empty/error,
authorization thật và bản ghép backend; chưa có bằng chứng các phần đó ở đây.

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
