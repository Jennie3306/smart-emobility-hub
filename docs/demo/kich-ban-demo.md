# Kịch bản demo một luồng (cập nhật 13/11/2026, hợp đồng sự kiện v2)

## Nguyên tắc dữ liệu

Mỗi tài nguyên có **một module chủ sở hữu**; module khác chỉ đọc lại trạng thái do chủ sở hữu phát:

| Tài nguyên | Chủ sở hữu | Ai phản chiếu |
|---|---|---|
| Đặt chỗ (reservation) | M2 | M4, M5 |
| Xe thuê | M3 | M1, M2 |
| Chỗ đỗ (quỹ chung của Hub) | M4 | M1, M8; M3 và M7 giữ / nhả chỗ qua `ParkingDock` |
| Trụ sạc | M5 | M1, M8, M4 (khóa chỗ gắn trụ) |
| Số đọc thiết bị | M6 | chỉ là tín hiệu vào cho M3, M4, M5 |

- M6 phát số đọc thiết bị bằng `DeviceReadingReported`; chỉ chủ sở hữu (M4, M5) phát `ResourceStateUpdated`. M1 và M8 chỉ nghe `ResourceStateUpdated`, nên dashboard luôn khớp M4/M5.
- **Quỹ chỗ đỗ chung (BR-M4-08):** xe thuê đang đứng ở Hub cũng chiếm một ParkingSpace. Trả xe → M3 gọi M4 giữ một chỗ (hết chỗ → `HUB_FULL`, kèm Hub còn chỗ); nhận xe → chỗ được nhả. M7 nhả chỗ ở Hub nguồn khi bắt đầu điều chuyển và giữ chỗ ở Hub đích khi tới nơi.
- Khi khởi động: M4 giữ chỗ cho xe thuê đang đứng ở Hub mà chưa có chỗ, rồi M4 và M5 phát lại trạng thái mọi chỗ đỗ / trụ sạc.
- Dữ liệu demo tạo bằng **luồng thật**: 29 sinh viên demo (`demo01`..`demo29`, mật khẩu `123456`) có lượt đặt đã check-in ở Hub Ga Metro (P-09..P-34 và P-37..P-39), cộng 2 xe thuê EB-001, EB-002 đứng ở P-36, P-35. Hub bắt đầu ở 31/40 = 77%, ngay dưới ngưỡng cảnh báo 80%. Các chỗ có trụ sạc P-01..P-08 và P-40 để trống. Tắt bằng `app.demo.seed-parking=false`.
- **Đội xe thuê (V30):** 26 xe. Hub Ga Metro cố ý thiếu xe (EB-001 sẵn sàng, EB-002 pin 18%) nên M1 giữ một cảnh báo `LOW_VEHICLE` ở đó; KTX Khu A 10 xe, KTX Khu B 5, BK 5, KHTN 4. Đây là câu chuyện mở đầu: sinh viên đổ về ga Metro mà xe lại dồn ở ký túc xá → M7 nhận cảnh báo, M8 đề xuất điều chuyển.
- **M6 khi quay demo:** `scheduler-enabled: false` (nếu bật, `NORMAL_TICK` chạy 5 giây/lần và trụ hỏng tự hoạt động lại trước khi người xem kịp thấy). Chạy tay `NORMAL_TICK` với **seed 100** (mặc định mới): số đọc pin EB-001 là 92%, đúng bằng dữ liệu mẫu, nên không đổi gì ngoài việc đưa trụ 1 về hoạt động.
- Lượt đặt đã check-in mà quá giờ rời bãi 15 phút vẫn chưa check-out thì Timer của M4 tự check-out. Lượt demo dài 12 giờ; mỗi lần khởi động, sinh viên demo nào hết lượt được đặt và check-in lại, nên Hub luôn bắt đầu ở 77%.

## Luồng demo (khoảng 10 phút)

1. **Operator — Dashboard (M1):** Hub Ga Metro 77% chỗ đỗ (29 xe cá nhân + 2 xe thuê), 7/8 trụ sạc hoạt động (trụ 8 hỏng sẵn), cảnh báo `LOW_VEHICLE` vì chỉ còn 1 xe sẵn sàng; các Hub ký túc xá dư xe.
2. **student1 — Find a vehicle (M2) → My reservation:** đặt EB-001 ở Hub Ga Metro (*Book vehicle*), rồi *Pick up vehicle* → chỗ P-36 được nhả, Hub còn 30/40 = 75%.
3. **student1 — Parking (M4):** *My vehicles* → thêm xe; *Book a space* → Hub Ga Metro, giờ đến = bây giờ, tick *Charge while parked*, pin 30% → hệ thống chọn P-01 (có trụ 1). Hub 31/40.
   - M5: phiên sạc vào hàng đợi, được gán **trụ 1** (trụ của chỗ P-01), trụ chuyển RESERVED trên dashboard.
4. **student1 — My reservation (M3 trả xe):** *Return vehicle* về Hub Ga Metro, pin 70% → xe chiếm một chỗ trong quỹ chung, Hub lên 32/40 = 80%.
   - M1: cảnh báo CAPACITY (chậm nhất 30 giây); M7 nhận `HubNearCapacityEvent`.
   - Ghi chú khi thuyết trình: nếu Hub đã hết chỗ, trả xe bị từ chối `HUB_FULL` và hệ thống gợi ý Hub còn chỗ (tình huống §3.3 "trả xe tại Hub đầy").
5. **student1 — My tickets (M4):** *Check in* → chỗ OCCUPIED. **Smart charging (M5):** *Start* → trụ 1 IN_USE.
6. **Operator — Incidents & IoT (M6):** chạy `CHARGER_FAILURE` (trụ 1 hỏng).
   - M5: trụ 1 FAULTY; phiên đang sạc bị ngắt và chuyển sang trụ khác còn trống (không còn trụ nào thì nằm lại hàng đợi với +20 điểm bù).
   - M4: P-01 bị khóa (xe vẫn đang đỗ, vé vẫn hiệu lực, check-out vẫn được).
   - M1: trụ 1 hiện FAULTY, P-01 hiện BLOCKED.
7. **Operator — M6:** chạy `NORMAL_TICK` với **seed 100** → M5 nhận số đọc, đưa trụ 1 về hoạt động và phát trạng thái mới (M1 thấy ngay). P-01 vẫn khóa vì xe còn đỗ ở đó.
8. **student1 — My tickets:** *Check out* → reservation COMPLETED, vé COMPLETED, P-01 mở lại ngay (M4 thấy trụ 1 đã hoạt động). Phiên sạc tự kết thúc khi đủ 90% pin, sau 90 phút hoặc đến giờ rời bãi.
9. **Operator — What-if Studio (M8):** giữ kịch bản mặc định (đóng Hub Ga Metro, 100 yêu cầu) → *Save & run simulation*; tick *Allow requests to use other hubs* rồi chạy lại để so sánh. Xem *Recommended plans* (điều chuyển xe từ Hub ký túc xá sang). Chưa bấm *Approve & execute* vì M8 chưa nối sang M7.

## Bước thêm (cuối buổi): FR tự chuyển xe cần sạc của M3, kiểm thử biên ở ngưỡng 20%

Làm sau khi xong các bước dùng EB-001. Operator → M6 → `NORMAL_TICK`, chạy lần lượt theo thứ tự dưới đây (xe đã LOW_BATTERY thì không tự về AVAILABLE):

| Seed | Pin EB-001 do M6 báo | Kết quả mong đợi (Find a vehicle / Hub detail) |
|---|---|---|
| 51 | 20% | Vẫn AVAILABLE |
| 101 | 21% | Vẫn AVAILABLE |
| 127 | 19% | LOW_BATTERY; M5 tự tạo yêu cầu sạc cho xe (Smart charging → Hub charging queue) |

## Lưu ý

- Muốn demo lại từ đầu: xóa database (`docker compose down -v`, rồi `docker compose up -d`) và chạy lại app. Nên reset trước mỗi lần quay để số liệu đúng như trên.
- Không còn dùng cách cũ "M6 chuyển PARKING_SPACE 1 sang OCCUPIED để lên 80%": số đọc đó giờ không đổi dashboard vì M4 mới là chủ sở hữu chỗ đỗ.
