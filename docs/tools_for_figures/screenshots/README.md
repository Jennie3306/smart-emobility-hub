# Ảnh chụp giao diện

```
screenshots/
├── capture.mjs        script chụp
├── package.json
├── shared/mockup/     login.png
└── m1 … m8/
    ├── mockup/        màn hình chính, dùng cho mục UI Design (tự chụp)
    └── real/          giao diện thật khi đang thao tác (bạn thao tác, script chụp)
```

| Loại | Là gì | Dùng ở đâu trong report |
|---|---|---|
| **mockup** | Màn hình chính của module ngay sau khi mở, dữ liệu seed, không popup hay lỗi | Mục *UI design* của từng module |
| **real** | Hệ thống đang chạy thật: kết quả sau khi bấm nút, như thông báo thành công, thông báo lỗi, đồng hồ đếm ngược, trạng thái vừa đổi | Ảnh minh hoạ luồng chính, phần demo / kiểm thử |

Mọi ảnh đều có khổ 1440×900, độ phân giải ×2 (ảnh 2880×1800), nên các module đều đồng đều.

## Cài đặt (một lần, trong thư mục này)
```
cd tools_for_figures/screenshots
npm install
npx playwright install chromium
```

## Trước khi chụp
1. Chạy backend với dữ liệu seed. Nếu muốn ảnh "sạch" thì reset DB trước.
2. Chạy frontend `npm run dev`, phải ở **cổng 5173**; nếu cổng khác, đăng nhập sẽ bị lỗi CORS 403.
3. Vào http://localhost:5173 và đăng nhập thử `student1` / `123456`.

## Chụp mockup (tự động, khoảng 1 phút)
```
npm run mockup                 # tất cả module
node capture.mjs mockup m5     # chỉ một module
```
Script tự đăng nhập `student1` hoặc `operator1`, mở từng trang, bấm tab nếu cần, rồi lưu ảnh vào `mX/mockup/`. Chế độ này chỉ mở trang và bấm tab, không làm thay đổi dữ liệu.

## Chụp giao diện thật (bạn thao tác, khoảng 15 phút)
```
npm run real                   # tất cả, theo thứ tự M1 → M8
node capture.mjs real m2       # chỉ một module
```
Script mở cửa sổ Chrome, đã đăng nhập sẵn và vào đúng trang. Terminal in ra việc cần làm, ví dụ:
```
[m2] m2_my_reservation  (student1)
  → Bấm Search, rồi Book vehicle ở một xe. Đợi trang My reservation hiện CONFIRMED…
  Enter = chụp, f + Enter = chụp cả trang, s + Enter = bỏ qua:
```
Làm theo hướng dẫn trong cửa sổ Chrome. Khi màn hình đúng như mô tả, quay lại terminal:
- bấm **Enter** để chụp phần đang thấy (có thể cuộn trang trước khi chụp);
- gõ **f** rồi Enter để chụp cả trang;
- gõ **s** rồi Enter để bỏ qua bước đó.

Nên chạy đủ M2 rồi tới M3, vì M3 nhận chính chiếc xe vừa đặt ở M2. Muốn chụp lại một ảnh thì chạy lại module đó; ảnh cũ sẽ bị ghi đè.

Xem danh sách mọi ảnh sẽ chụp: `npm run list`.

## Danh sách ảnh

| Module | mockup (tự động) | real (thao tác) |
|---|---|---|
| — | `login` | — |
| M1 | `m1_dashboard`, `m1_hub_list`, `m1_hub_detail`, `m1_alert_history` | `m1_alert_open`, `m1_alert_acknowledged` |
| M2 | `m2_search` | `m2_search_results`, `m2_vehicle_detail`, `m2_my_reservation`, `m2_limit_error` |
| M3 | `m3_fleet` | `m3_current_trip`, `m3_returned` |
| M4 | `m4_parking`, `m4_parking_operator` | `m4_booked_ticket`, `m4_checked_in`, `m4_operator_spaces` |
| M5 | `m5_schedule`, `m5_operator` | `m5_request_submitted`, `m5_operator_queue`, `m5_charging_started` (student) |
| M6 | `m6_incidents`, `m6_simulator` | `m6_incident_reported`, `m6_incident_assigned`, `m6_simulator_run` |
| M7 | `m7_redistribution` | `m7_pending`, `m7_tasks_created`, `m7_task_done` |
| M8 | `m8_whatif`, `m8_history`, `m8_network` | `m8_extracted`, `m8_run_result`, `m8_compare` |

## Ảnh nào vào report

Chép các file sau vào `images/ui/` của report, đổi sang tên ở cột phải. Mọi ảnh khác cứ để trong thư mục này, khi cần thì lấy thêm.

| Lấy từ | Đặt tên trong report |
|---|---|
| `m1/mockup/m1_dashboard.png` | `m1_dashboard.png` |
| `m2/mockup/m2_search.png` | `m2_search.png` |
| `m2/real/m2_my_reservation.png` | `m2_my_reservation.png` |
| `m3/real/m3_current_trip.png` | `m3_current_trip.png` |
| `m4/mockup/m4_parking.png` | `m4_parking.png` |
| `m5/mockup/m5_operator.png` | `m5_operator_mockup.png` |
| `m5/mockup/m5_schedule.png` | `m5_schedule_mockup.png` |
| `m6/mockup/m6_incidents.png` | `m6_incidents.png` |
| `m7/real/m7_pending.png` | `m7_redistribution.png` |
| `m8/real/m8_run_result.png` | `m8_whatif.png` |

## Lưu ý
- **Đăng nhập lỗi:** thường do sai cổng (phải là 5173) hoặc backend chưa chạy. Cổng khác thì dùng `BASE=http://localhost:5174 node capture.mjs mockup`, nhưng phải thêm cổng đó vào CORS của backend.
- **Khổ ảnh khác:** dùng `WIDTH=1280 HEIGHT=800 npm run mockup`.
- **Chụp tay không dùng script:** mở Chrome DevTools, bật Device toolbar, đặt khổ 1440×900, rồi Ctrl+Shift+P → *Capture screenshot*. Đặt tên và để vào đúng thư mục như bảng trên.
- **Màn M8:** chụp lại sau khi TV8 merge phần nối M7, để nút *Approve & execute* không còn bị khoá.
