# Ứng dụng Android — Smart E-Mobility Hub

Thư mục này đóng gói **chính frontend React** (`../frontend`) thành một ứng dụng Android thật bằng
[Capacitor](https://capacitorjs.com/). Không viết lại giao diện: mọi màn hình, quy tắc và API giống
hệt bản web. Ứng dụng có icon riêng, mở toàn màn hình, thanh tab dưới đáy, và gọi thẳng tới backend
đang chạy trên laptop qua Wi-Fi.

```
Điện thoại (app)  ──HTTP──►  backend Spring Boot (laptop, cổng 8080)  ──►  PostgreSQL
   WebView + giao diện React đóng gói sẵn trong APK
   Capacitor HTTP gửi request bằng native → giữ cookie JSESSIONID, không vướng CORS
```

## Cần cài một lần

| Công cụ | Phiên bản |
|---|---|
| Node.js | 20 trở lên |
| Android Studio | Ladybug (2024.2.1) trở lên, kèm Android SDK |
| JDK | 21 (Android Studio có sẵn JDK đi kèm) |

## Build APK (lần đầu)

```bash
cd frontend && npm install && cd ..   # nếu chưa cài
cd mobile
npm install                           # Capacitor
npm run add:android                   # tạo thư mục android/ (chỉ làm 1 lần, rồi commit thư mục này)
npm run icons                         # icon + splash từ assets/
npm run sync                          # build frontend rồi chép vào android/
npm run open                          # mở Android Studio
```

Trong Android Studio: chờ Gradle sync xong → **Build › Build App Bundle(s) / APK(s) › Build APK(s)**.
File nằm ở `mobile/android/app/build/outputs/apk/debug/app-debug.apk` — gửi qua Zalo/Drive, mở trên
điện thoại để cài (cho phép "cài từ nguồn không xác định").

Hoặc cắm điện thoại (bật USB debugging) rồi chạy `npm run device` để cài và mở thẳng.

Không cần Android Studio GUI: `cd android` rồi `./gradlew assembleDebug` (Windows: `gradlew.bat assembleDebug`).

**Mỗi lần sửa frontend:** chạy lại `npm run sync` rồi build lại APK.

## Chạy demo trên điện thoại

1. Chạy backend trên laptop như bình thường (`mvn spring-boot:run` → cổng 8080, hoặc
   `docker compose -f docker-compose.demo.yml up` → cổng 8080 và 80).
2. Laptop và điện thoại vào **cùng một Wi-Fi**. Xem IP của laptop: Windows `ipconfig`
   (IPv4 Address), macOS `ipconfig getifaddr en0`.
3. Mở app → màn hình đăng nhập có ô **Server address**: nhập `http://<IP-laptop>:8080`
   (ví dụ `http://192.168.1.20:8080`). App nhớ địa chỉ này cho lần sau.
4. Đăng nhập `student1` hoặc `operator1` (mật khẩu `123456`).

Có thể build sẵn địa chỉ mặc định: `VITE_SERVER_URL=http://192.168.1.20:8080 npm run sync`
(Windows PowerShell: `$env:VITE_SERVER_URL="http://192.168.1.20:8080"; npm run sync`).

## Gỡ lỗi nhanh

| Hiện tượng | Cách xử lý |
|---|---|
| "Cannot reach the server at …" | Sai IP/cổng, khác Wi-Fi, hoặc tường lửa Windows chặn cổng 8080 (cho phép Java/OpenJDK trên mạng Private). Thử mở `http://<IP>:8080/api/auth/me` bằng trình duyệt điện thoại: thấy lỗi 401 là kết nối được. |
| Đăng nhập được nhưng dashboard báo "Data may be stale" | WebSocket không nối được; trang vẫn tự cập nhật 15 giây/lần. Kiểm tra `app.cors.app-origins` trong `backend/src/main/resources/application.yml` có `https://localhost`. |
| Wi-Fi trường chặn kết nối giữa các máy | Dùng hotspot từ điện thoại, cho laptop bắt hotspot đó. |

## Vì sao làm theo cách này

- **Một mã nguồn cho web và app**: sửa giao diện một lần, cả hai cùng có. Viết lại bằng React Native
  là thêm một bộ giao diện thứ hai phải giữ đồng bộ với 8 module.
- **Backend không đổi API**: app dùng đúng các endpoint, session cookie và quy tắc nghiệp vụ hiện có.
  Thay đổi duy nhất ở backend là cho phép thêm origin của app (`https://localhost`,
  `capacitor://localhost`) trong CORS và WebSocket.
- Giới hạn: bản build là bản debug để demo; phát hành lên Google Play cần ký APK và dùng HTTPS.
  Bản iOS làm tương tự (`cap add ios`) nhưng phải có máy Mac + Xcode.
