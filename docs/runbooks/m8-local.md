# M8 — chạy và kiểm tra local

Branch: `feature/m8-UI_whatif`. Đã tích hợp M3 `6ed46b4`, M6 `2e74eb8`.

## Chạy

Từ thư mục gốc, PowerShell:

```powershell
./scripts/start-m8.ps1
```

Mở http://localhost:5173/m8/simulation. Đăng nhập `operator1 / 123456`.
Tài khoản STUDENT không có quyền M8. Backend: http://localhost:8080/swagger-ui.html.
Dừng backend/frontend bằng `./scripts/stop-m8.ps1`; CSDL được giữ nguyên.

## Hiện đã có

- Text → form chỉnh sửa được; adapter LLM tùy chọn và bộ quy tắc local có nhãn riêng.
- Đọc Hub/xe từ PostgreSQL bằng transaction REPEATABLE_READ, sao chép sang records bất biến.
- Engine bước 1 phút, lịch khách đến tái lập bằng seed; không gọi LLM hoặc sửa xe thật.
- KPI, baseline cùng snapshot khi Hub mở, biểu đồ, lưu lịch sử, chỉnh sửa và so sánh.
- M6 `ResourceStateUpdatedEvent` → bảng quan sát IoT. Các quan sát RAM có timestamp riêng;
  không tuyên bố nhất quán cùng transaction PostgreSQL và không ghi đè state xe M3.
- Đề xuất điều chuyển khả thi hoặc chuyển khách; kiểm chứng từng phương án độc lập bằng engine.
- Discard được lưu. Apply trả 409 `DISPATCH_NOT_CONNECTED`, không phát sự kiện giả thực thi.
- Scheduler chụp trạng thái mỗi 30 phút, cảnh báo nếu số xe gán Hub vượt sức chứa.
  Chưa có demand feed M2 nên không dự báo quá tải từ request thực.

## Giả định mô hình

- Request tổng được rải ngẫu nhiên theo seed trong `arrivalWindowMins`.
- Chỉ xe AVAILABLE có pin ≥20% phục vụ. Không tái hiện toàn bộ logic reservation M2.
- Một chuyến giữ xe `tripMins`, sau đó xe quay về Hub xuất phát; không tính hao pin/sạc.
- Đóng Hub trong toàn bộ horizon. Nếu bật reroute, chọn Hub còn xe theo ID, không tính khoảng cách.
- Khách còn chờ cuối horizon là thất bại; mean wait chỉ tính khách được phục vụ.
- Không mô phỏng M4 parking reservation, lịch M5, thời gian điều chuyển hoặc arrivals từ chuyến đang chạy.
- Baseline giữ cùng demand/seed/reroute, chỉ đổi Hub từ đóng sang mở.
- Đề xuất dispatch giả định xe đã chuyển trước mô phỏng; KPI của từng đề xuất không cộng gộp.

Lịch sử lưu atomically ở `backend/.local/m8/runs.json`, giữ 200 lần chạy mới nhất.
File này chỉ dành cho một backend local, không thay thế database đa instance.
Không tạo migration M8: TV4 cần chốt schema Scenario/SimulationRun/KPI/Recommendation.

## LLM tùy chọn

Trước khi chạy script, đặt các biến môi trường của **backend**:

- `M8_LLM_URL`: URL đầy đủ của endpoint chat completions tương thích.
- `M8_LLM_KEY`: API key, không commit hoặc gửi xuống browser.
- `M8_LLM_MODEL`: tên model hỗ trợ JSON object response.

Adapter yêu cầu `response_format: {type: json_object}`; kiểm tra JSON, bounds, Hub,
xe nguồn và sức chứa đích; không thực hiện tool call do LLM trả về.
Request chứa mô tả, snapshot hoặc KPI sẽ gửi đến provider do người vận hành cấu hình.
Chưa cấu hình hoặc provider lỗi: dùng local rules và hiển thị nguồn rõ ràng.
Trong lần kiểm tra local này chưa gọi provider LLM thực.

## Handoff tích hợp

- TV2: demand theo Hub/time window, semantics request và reservation.
- TV3: snapshot contract xe/Hub; kiểm tra sửa identity/response M3 trong branch này.
- TV4: migration M8, Hub operational state/capacity và parking integration.
- TV7: Apply contract phải kiểm tra lại dữ liệu live, dedupe và trả execution status.
  `RecommendationApprovedEvent` hiện chưa đủ payload cho dispatch; không sửa contract shared trong M8.
- M1: tiếp nhận warning và execution status để hiển thị dashboard.

## Kiểm tra

```powershell
cd backend
& 'C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\plugins\maven\lib\maven3\bin\mvn.cmd' -B verify
cd ../frontend
npm.cmd run build
```

Test bao gồm repeatability/conservation, đóng Hub, reroute, pin thấp, zero demand,
restart persistence, validation, quyền M8, feasibility đề xuất và chống spoof userId M3.
