# Mã nguồn sơ đồ của report

Gói xếp theo **loại file**. Trong mỗi loại lại chia theo **module** (`m1` … `m8`, cộng `shared` cho sơ đồ toàn hệ thống).

## Mỗi module có đủ 5 sơ đồ UML, đặt tên giống nhau

| Sơ đồ | File nguồn | Ảnh |
|---|---|---|
| Use case | `graphviz_dot/mX/mX_usecase.dot` | `png/mX/mX_usecase.png` |
| Class | `graphviz_dot/mX/mX_class.dot` | `png/mX/mX_class.png` |
| State machine | `graphviz_dot/mX/mX_state.dot` | `png/mX/mX_state.png` |
| Activity | `graphviz_dot/mX/mX_activity.dot` | `png/mX/mX_activity.png` |
| Sequence | `mermaid_mmd/mX/mX_sequence.mmd` | `png/mX/mX_sequence.png` |

Mỗi module có một script `python/mX/mX.py` sinh cả 5 sơ đồ trên. Các file `.dot`, `.mmd` và `.png` đều do script ghi ra.

Nội dung sơ đồ state và activity của từng module:

| Module | State machine | Activity |
|---|---|---|
| M1 | Alert | UC-M1-07: đánh giá ngưỡng, bật và tắt cảnh báo |
| M2 | Reservation | UC-M2-03: đặt xe, kiểm tra xung đột |
| M3 | RentalVehicle | UC-M3-02: trả xe tại hub |
| M4 | ParkingSpace | Một lượt đỗ, từ lúc đặt chỗ tới lúc giải phóng chỗ |
| M5 | ChargingSession | Hàng đợi ưu tiên và xử lý trụ sạc hỏng (bản của TV5) |
| M6 | Incident | Báo sự cố: Operator báo tay hoặc thiết bị báo hỏng |
| M7 | Task + Recommendation | Từ cảnh báo của hub tới các task điều phối |
| M8 | Proposal trong một run | Một lần chạy what-if, từ mô tả tới quyết định |

## Các thư mục

| Thư mục | Nội dung |
|---|---|
| `python/` | `lib.py` (hàm vẽ chung), `render_all.py`, `mX/mX.py`, `shared/*.py`, cùng các file phụ `stick.png`, `mcfg.json`, `pp.json` |
| `graphviz_dot/mX/` | Use case, class, state, activity |
| `mermaid_mmd/mX/` | Sequence |
| `plantuml_puml/m2/`, `m5/` | Bản gốc PlantUML của Danh (tiếng Việt) và use case M5 của TV5 |
| `drawio/m2/`, `m6/` | Bản gốc draw.io của M2 (state) và M6 |
| `html/shared/` | Sơ đồ kiến trúc (`arch.html`) và script chụp (`shot.js`) |
| `png/mX/`, `png/shared/` | Ảnh đã xuất |
| `screenshots/mX/mockup/`, `screenshots/mX/real/` | Ảnh chụp giao diện. Script và cách chụp xem trong `screenshots/README.md` |

Ảnh xuất từ bản gốc của các bạn được thêm đuôi công cụ để không trùng tên với bản chuẩn, ví dụ `m2_usecase.puml.png` và `m6_use_case.drawio.png`. Riêng `m5_class_diagram.png` và `m5_sequence_charger_failed.png` do script Pillow của TV5 vẽ (`python/m5/render_m5_*.py`).

## Cài đặt (một lần)
- Python 3, Graphviz, Node.js
- Mermaid CLI: `npm install -g @mermaid-js/mermaid-cli`. Nếu `mmdc` báo không tìm thấy Chrome, chạy `npx puppeteer browsers install chrome-headless-shell`.
- Hai script `render_m5_*.py` cần `pip install pillow` và font DejaVu.

## Vẽ lại
```
cd python
python render_all.py          # tất cả
python render_all.py m3       # một module
python render_all.py shared   # ERD, interaction, deployment, navigation
```
Hoặc chạy riêng một script: `cd python/m3 && python m3.py`.

Muốn đổi màu, font hay khoảng cách cho mọi sơ đồ thì sửa `lib.py`. Muốn sửa nội dung sơ đồ của một module thì sửa `mX.py` của module đó. Không sửa thẳng file `.dot` hoặc `.mmd`, vì lần chạy script sau sẽ ghi đè.

Nếu vẫn muốn render tay một file nguồn:
```
cd graphviz_dot/m3 && dot -Tpng -Gdpi=150 m3_usecase.dot -o m3_usecase.png
mmdc -i mermaid_mmd/m3/m3_sequence.mmd -o m3_sequence.png -c python/mcfg.json -w 1500 -b white -s 2
```
- Phải đứng trong thư mục của file `.dot`, vì use case tìm `stick.png` qua đường dẫn `../../python`.
- Riêng `module_interaction.dot` phải dùng lệnh `neato -n2`.

Sơ đồ kiến trúc: sửa `html/shared/arch.html`, rồi chạy `npm i playwright` và `node shot.js "$(pwd)/arch.html" architecture.png`.

## Quy ước
- Sơ đồ phải khớp code lúc chốt: dùng đúng tên class, trạng thái và sự kiện.
- Mỗi module tự quản sơ đồ của mình. Sơ đồ kiến trúc tổng chỉ TV3 sửa.
- Sửa xong thì chép `png/mX/mX_*.png` đè vào `images/mX/` của report, giữ nguyên tên file.
