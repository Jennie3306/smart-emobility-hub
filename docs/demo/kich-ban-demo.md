# Kịch bản demo một luồng (chốt 12/11/2026)

## Nguyên tắc dữ liệu

Mỗi tài nguyên có **một module chủ sở hữu**; module khác chỉ đọc lại trạng thái do chủ sở hữu phát:

| Tài nguyên | Chủ sở hữu | Ai phản chiếu |
|---|---|---|
| Đặt chỗ (reservation) | M2 | M4, M5 |
| Xe thuê | M3 | M1, M2 |
| Chỗ đỗ | M4 | M1, M8 |
| Trụ sạc | M5 | M1, M8, M4 (khóa chỗ gắn trụ) |
| Số đọc thiết bị | M6 | chỉ là tín hiệu vào cho M3, M4, M5 |

- Số đọc của M6 mang `source = "M6"`. M1 **bỏ qua** số đọc M6 cho chỗ đỗ và trụ sạc; nó cập nhật khi M4/M5 phát trạng thái sau khi xử lý.
- Khi khởi động, M4 và M5 phát lại trạng thái mọi chỗ đỗ / trụ sạc, nên M1 khớp với M4/M5 kể cả trên database cũ.
- Dữ liệu demo được tạo bằng **luồng thật**: 31 sinh viên demo (`demo01`..`demo31`, mật khẩu `123456`) có lượt đặt đã check-in ở Hub Ga Metro, chỗ P-09..P-39. Hub bắt đầu ở 31/40 = 77%, ngay dưới ngưỡng cảnh báo 80%. Các chỗ có trụ sạc P-01..P-08 và P-40 để trống. Tắt bằng `app.demo.seed-parking=false`.
- Lượt đặt nào đã check-in mà quá giờ rời bãi 15 phút vẫn chưa check-out thì Timer của M4 tự check-out (bảng chuyển trạng thái 4.1: Occupied → Free khi kết thúc khung giờ). Lượt demo dài 12 giờ; mỗi lần khởi động app, sinh viên demo nào đã hết lượt sẽ được đặt và check-in lại, nên lúc nào mở app Hub cũng bắt đầu ở 77%.

## Luồng demo (khoảng 10 phút)

1. **Operator — Dashboard (M1):** Hub Ga Metro 77% chỗ đỗ, 7/8 trụ sạc hoạt động (trụ 8 hỏng sẵn).
2. **student1 — Find a vehicle (M2) → Rental vehicles (M3):** đặt và nhận một xe thuê (tùy chọn, minh họa M2/M3).
3. **student1 — Parking (M4):** *My vehicles* → thêm xe; *Book a space* → Hub Ga Metro, giờ đến = bây giờ, tick *Charge while parked*, pin 30% → hệ thống chọn P-01 (có trụ 1).
   - M1: Hub lên 32/40 = 80% → cảnh báo CAPACITY (chậm nhất 30 giây); M7 nhận `HubNearCapacityEvent`.
   - M5: phiên sạc vào hàng đợi, được gán **trụ 1** (trụ của chỗ P-01), trụ chuyển RESERVED trên dashboard.
4. **student1 — My tickets (M4):** *Check in* → chỗ OCCUPIED. **Smart charging (M5):** *Start* → trụ 1 IN_USE.
5. **Operator — Incidents & IoT (M6):** chạy `CHARGER_FAILURE` (trụ 1 hỏng).
   - M5: trụ 1 FAULTY; phiên đang sạc bị ngắt và chuyển sang trụ khác còn trống (nếu không còn trụ nào thì nằm lại hàng đợi với +20 điểm bù).
   - M4: P-01 bị khóa (xe vẫn đang đỗ, vé vẫn hiệu lực, check-out vẫn được).
   - M1: trụ 1 hiện FAULTY, P-01 hiện BLOCKED.
6. **Operator — M6:** chạy `NORMAL_TICK` → trụ 1 hoạt động lại (M5 và M1 thấy ngay). P-01 vẫn khóa vì xe còn đỗ ở đó.
7. **student1 — My tickets:** *Check out* → reservation COMPLETED, vé COMPLETED. Chạy `NORMAL_TICK` thêm một lần → P-01 mở lại (FREE). Phiên sạc tự kết thúc khi đủ 90% pin, sau 90 phút hoặc đến giờ rời bãi.
8. **Operator — What-if Studio (M8):** chạy mô phỏng trên snapshot hiện tại (cùng số liệu với M1).

## Lưu ý

- Muốn demo lại từ đầu: xóa database (`docker compose down -v`, rồi `docker compose up -d`) và chạy lại app.
- Không còn dùng cách cũ "M6 chuyển PARKING_SPACE 1 sang OCCUPIED để lên 80%": số đọc đó giờ không đổi dashboard vì M4 mới là chủ sở hữu chỗ đỗ.
