Hưng (TV3 / M3) reply:1. D03 / D05 / D06 / D09

Mình giữ nguyên các quyết định đã chốt.2. D06 — Contract health (M6) vs usage/session (M5)

Đồng ý mô hình C:
M6 sở hữu device health (OK / Faulty / UnderRepair…) và phát ChargerFailed / recovery event nếu cần.
M5 sở hữu allocation + ChargingSession (cấp phát, bắt đầu/kết thúc phiên).
M6 không ghi bảng/session của M5; M5 tự dừng/phân lại khi nhận failure event.
M3 không đụng ChargingPoint. Với RentalVehicle (đã code):
M6 phát VehicleFailed → M3 handler chuyển MAINTENANCE + clearReservation().
Pin thấp không thành Failed: M3 chỉ phát VehicleLowBattery lúc trả xe.
Nhờ M5 đề xuất bảng state usage + điều kiện cấp phát lại; nếu cần recovery event/API mới thì gửi draft contract mình duyệt trước khi implement.3. D12 — reason + payload
Đồng ý reason String viết HOA (convention chung).
Ba hướng event M3 đang dùng đủ field:
VehicleFailed → M3 chỉ cần vehicleId (+ eventId)
ReservationConfirmed → userId, resourceType, resourceId (+ reservationId, hubId)
ReservationCancelled / Expired → resourceType, resourceId
Payload hiện tại đủ cho handler M3; không yêu cầu thêm field trừ khi M5/M6 có use case mới.

4. D11 — latest state + Incident, không raw history

Phù hợp kiến trúc BTL / event-driven hiện tại. M3 cũng chỉ giữ state mới nhất trên RentalVehicle + phản ứng event, không lưu raw history sự kiện. Chấp nhận D11.5. Dedupe + handler M3
M3 remote/caller hiện dùng ProcessedEventStore.markIfNew(eventId) một tham số — giữ tương thích.
Handler M3 đã có và cần giữ khi merge:
ReservationConfirmed → AVAILABLE → RESERVED + reserve(userId)
ReservationCancelled / ReservationExpired → RESERVED → AVAILABLE + clearReservation
VehicleFailed → MAINTENANCE
DispatchCompleted → cập nhật hub, IN_TRANSIT → AVAILABLE

Hướng markIfNew(consumerKey, eventId) (VD-20 / D09) chấp nhận thiết kế, triển khai shared khi có PR riêng; không phá handler mới của M3.
Nhánh M6 (feature/m6-iot-simulator-event-bus): phần đụng shared nhờ PR riêng hoặc báo mình review trước khi merge.
6. Incident response / error (D09) — quy ước assertion
Tình huống HTTPPayload thiếu/sai 400 Resource không tồn tại 404 Transition / state không hợp lệ409Event trùng (cùng consumer)no-op + log, không 5xxM3 business API cũng theo hướng tương tự (VEHICLE_NOT_FOUND → 404, invalid state → 400/409).7. Branch / owner shared
Logic nghiệp vụ M3 nằm trong package m3vehicle.
Đổi shared/events hoặc ProcessedEventStore → cần TV3 biết / review.
ReservationConfirmedEvent đã thêm trong shared để M2↔M3 hết DEF đặt chỗ mà xe vẫn AVAILABLE.


### 6. Incident response / error (D09) — quy ước assertion
| Tình huống | HTTP |
| --- | --- |
| Payload thiếu/sai | 400 |
| Resource không tồn tại | 404 |
| Transition / state không hợp lệ | 409 |
| Event trùng (cùng consumer) | no-op + log, không 5xx |