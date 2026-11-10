# Evidence M6 — IoT simulator, Incident domain và UI

Bộ này được tuyển chọn ngày **29/10/2026** từ bằng chứng lịch sử **27–28/10**
và bổ sung execution/ảnh mới **29/10** sau khi sửa hai gap. Log/ảnh cũ giữ nguyên. Nhánh được kiểm tra là
`feature/m6-iot-simulator-event-bus`, HEAD `5583c1c` + working tree ở các lượt cũ; code mới đã commit `7edb551` (backend)
và `b156344` (frontend). Source manifests ở `records/` tách từng revision/ngày chạy.

M6 cần evidence có liên kết requirement → thao tác → expected/actual, tương tự
bảng B18 của M2. Không cần số lượng ảnh giống M2. Ảnh UI không thay thế log test,
API response hoặc truy vấn DB; ảnh fixture không chứng minh Incident đã persist.

## Mục lục ảnh và phạm vi chứng minh

| ID | Ảnh / record | Liên quan | Đã chứng minh / giới hạn |
|---|---|---|---|
| EV-M6-01 | [Simulator thật](screenshots/ui/05-simulator-live.png) + [receipts/network](records/verification-excerpts-20261028.txt) | FR-03/04/05; TC-02/04/05/10/13 | Ba POST scenario 200, counts và event IDs. NORMAL_TICK với Long max có receipt text; ảnh chụp CHARGER_FAILURE. M6 state vẫn memory, không Incident DB. |
| EV-M6-02 | [Student 403](screenshots/ui/02-student-forbidden.png) + [401/login record](records/verification-excerpts-20261028.txt) | TC-08/09 | Simulator status yêu cầu Operator. Không dùng làm proof TC-30/31 Incident API hoặc toàn namespace TC-24. |
| EV-M6-03 | [M3 SQL trước/sau + live API 400](records/verification-excerpts-20261028.txt) | TC-04/16; D09 | Xe ID 1 AVAILABLE/version 0 → MAINTENANCE/version 1; seed null trả 400 và latestRun không đổi. Chưa có ảnh terminal SQL/API; không suy thành M6 latest-state table. |
| EV-M6-04 | [Incident fixture](screenshots/ui/08-incident-fixture.png) + [fixture receipts](records/verification-excerpts-20261028.txt) | FE-M6-03; graph D07 | Filter và Reopen đổi local view; nhãn fixture hiển thị rõ. Không chứng minh TC-25–63/API/DB/version/timestamps. |
| EV-M6-05 | [Simulator mobile](screenshots/ui/09-mobile-simulator.png), [Incident mobile](screenshots/ui/10-mobile-fixture.png) | FE-M6-06 | Bố cục 390×844, bảng cuộn trong vùng riêng; không chứng minh mobile device/browser khác Chromium. Ảnh simulator cuối lượt còn phản hồi injected 409. |
| EV-M6-06 | [Injected 409](screenshots/ui/12-injected-409.png) + [400/409 injection records](records/verification-excerpts-20261028.txt) | FE-M6-02/06 | Chỉ xử lý lỗi UI và GET refresh; **không** phải race/409 backend thật hay optimistic lock TC-43/44. |
| EV-M6-07 | [D07 focused](records/d07-focused.log), [backend 27/10](records/backend-verify-20261027.log), [backend 28/10](records/backend-verify-20261028.log) | D07, ModuleBoundaryTest | 35 domain tests và backend 69 tests. Không thay oracle API/DB của TC-34–40. |
| EV-M6-08 | [Frontend 28 tests](records/frontend-tests-20261028.log), [build](records/frontend-build-20261028.log) | FE-M6-01–04 | Component/API adapter + production build. Lỗi 400/401/403/409 trong component tests dùng mock. |
| EV-M6-09 | [Filter prototype](screenshots/prototype/01-closed-filter.png), [thiếu Reopen](screenshots/prototype/02-resolved-no-reopen.png), [form](screenshots/prototype/03-report-form.png), [simulator contract](screenshots/prototype/04-simulator-contract.png), [demo run](screenshots/prototype/05-demo-run.png) | Review TV5 tại `5a05df1` | Bằng chứng lỗi/gap của HTML prototype đã pin; không phải frontend hiện tại bị cùng lỗi hoặc chạy API thành công. |

`records/verification-excerpts-20261028.txt` giữ nội dung receipts,
network, SQL, startup/run logs, schema V1/V2, cleanup và provenance SHA-256 cho
từng file gốc. Log bootstrap Java/Vite, Compose config đã resolve và snapshot
lặp chỉ giữ local trong `output/`. `manifest.json` ghi nguồn/hash/size của các
file được chọn; các ảnh sao chép nguyên byte, không vẽ lại kết quả.

## Quyết định mới và phạm vi lịch sử

- [TV2 ngày 28/10](decisions/tv2-20261028.md): severity và active-Incident policy
  đã chốt; phần gợi ý cơ chế/DDL còn cần TV3/TV4 duyệt.
- [TV3 nhận ngày 29/10](decisions/tv3-received-20261029.md): nguồn không ghi ngày
  trả lời, nên tên file chỉ ghi ngày nhận. Chấp nhận D11 về kiến trúc, uppercase
  reason, payload M3 và boundary/compatibility; không phải phê duyệt đề xuất mới
  của TV2 về thứ tự active/transition hoặc cột unique.

Hai bản trên được copy nguyên văn từ `Template_report/responses/`, có hash trong
manifest. Ảnh và test log trước phản hồi vẫn giữ làm lịch sử. Ở snapshot trước sửa gap, code reading cho MANUAL_REPORT và UI ghi Chờ TV2.
Hai điểm này đã sửa trong code 29/10; xem mục proof mới bên dưới. Không đổi caption ảnh cũ thành behavior mới. TC-M6-22 chỉ là proof
allowlist **cũ**; không dùng để khẳng định tuân thủ quy tắc adapter mới.

## Ảnh còn thiếu và cách tự chụp

Không tạo ảnh giả của terminal/DB hoặc một feature chưa có. Những record dạng
text ở trên đã đủ để review kỹ thuật; nếu report cần ảnh như M2, có thể bổ sung:

1. **Test results:** mở terminal tại feature worktree. Chạy backend với Java 21
   `mvn -B -Dtest=IncidentStatusTest,IncidentLifecycleTest test`, rồi `mvn -B verify`.
   Frontend chạy `npm ci`, `npm run test -- --run`, `npm run build`. Chụp command,
   branch/revision và phần Tests/BUILD SUCCESS. Ghi ngày chạy mới và số thực tế;
   không mặc định vẫn là 35/69/28 khi code đã đổi. Có thể chụp log cũ nếu ghi rõ
   đó là log lịch sử 27/28, không phải chạy lại ngày chụp.
2. **Backend startup / DB / real 400:** trên môi trường PostgreSQL riêng theo
   runbook, chụp startup có Flyway versions và app Started; chụp request status,
   NORMAL_TICK/VEHICLE_FAILURE/CHARGER_FAILURE và run IDs. Trước/sau lỗi xe dùng
   `SELECT id, state, version FROM rental_vehicle WHERE id = 1;`. Chụp API seed
   null với HTTP 400/VALIDATION_FAILED, không chụp Swagger Example Value làm actual.
   Không quay/chụp Set-Cookie, JSESSIONID hoặc cookie file contents.
3. **Incident và latest-state sau migration:** hiện **chưa lấy được** vì chưa có
   service/API/migration M6. Chờ TV4 giao revision, chạy DB mới/ddl-auto validate,
   dùng tên bảng/cột và endpoint thực tế từ migration/Swagger. Chụp create → row
   REPORTED; lifecycle và reopen/Closed rejection; hai cập nhật cùng version;
   latest-state trước/sau; rollback không có event; listener sau commit đọc được
   dữ liệu. Gắn mỗi ảnh với TC-25–63 và request/run/event/Incident ID thực tế.
4. **Backend 409 thật:** ảnh injected 409 hiện tại phải giữ nhãn. Chỉ thêm ảnh
   “live 409” sau khi tái hiện contention hoặc stale version thật và đối chiếu
   server log/DB. Không dùng route interception làm bằng chứng backend.

Trên macOS dùng `⌘⇧4` chụp vùng có command/request và kết quả, đặt tên theo case
và ngày chạy. Không ghi Pass chỉ vì đã thêm ảnh. Với các mục chưa chạy vẫn ghi
**Not run**, expected mới theo TV2 được chốt riêng khỏi actual.

## Chạy lại môi trường smoke và lưu evidence

Runbook chính: [ci-and-demo.md](../../../../docs/runbooks/ci-and-demo.md).
Override của lần chạy cũ nằm ở [records/compose-override.yml](records/compose-override.yml).
Từ root **feature worktree**, sau khi kiểm tra project/container/cổng không do
phiên khác sử dụng, có thể chạy lại cùng cấu hình:

```sh
docker compose -p m6-ui-proof-20261028 -f docker-compose.yml \
  -f docker-compose.demo.yml \
  -f DESCRIPTION/Template_report/Evidence/M6_IoT/records/compose-override.yml up -d --build
```

Frontend loopback 18088, backend 18089, DB 55438. Tài khoản demo trong runbook.
Vehicle failure tác động fixture M3 nên chỉ dùng DB biệt lập. Kết thúc dùng cùng
project/files với `down --volumes --rmi local`; không dọn project của người khác.

## Quy tắc commit cho bộ này

Nên commit README, decisions, manifest, ảnh đã chọn và các record nhỏ ở đây cùng
report/reviews. Không commit cả `output/`, cookie/session, `target/`, `dist/`,
`node_modules/`, local Harness/plan hoặc bản assignment của mọi TV. Phần mở rộng
`.txt`/`.log` không tự quyết định có nên commit: các file ở `records/` là evidence
đã chọn, còn file tạm/trùng ở `output/` để local. Sau commit, report không cần
`output/` để mở bằng chứng. Chưa xóa các file gốc để vẫn có thể đối chiếu.

`.git/info/exclude` hiện đang ẩn toàn bộ Evidence của DESCRIPTION. Khi thực hiện
kế hoạch commit, chỉ force-add đường dẫn `DESCRIPTION/Template_report/Evidence/M6_IoT`;
không force-add cả DESCRIPTION hoặc bỏ các exclude toàn cục.

## Proof mới sau sửa gap — 29/10/2026

- [47 focused tests](records/policy-20261029/focused-backend.log),
  [71 backend tests](records/policy-20261029/backend-verify.log),
  [32 frontend tests](records/policy-20261029/frontend-tests.log),
  [production build](records/policy-20261029/frontend-build.log).
- [Bắt buộc chọn severity, không default](screenshots/ui/policy-20261029/02-severity-required.png),
  [preview CRITICAL](screenshots/ui/policy-20261029/03-severity-preview.png),
  [form mobile](screenshots/ui/policy-20261029/04-severity-mobile.png).
- [Browser receipts](records/policy-20261029/browser-checks.txt) ghi rõ GET status
  stubbed để cô lập Incident fixture. Không có live API/DB smoke mới hoặc request
  tạo Incident. [Source hashes](records/policy-20261029/source-sha256.txt) khớp code
  trong hai commit 7edb551/b156344. Compose config --quiet đã pass.

Adapter MANUAL_REPORT được reject trước write/publish/mark completed; corrected
reading cùng ID vẫn dùng được. Operator reason validation giữ mã này riêng,
không phải Incident endpoint đã có. Fixture severity chỉ hiển thị/lọc/draft,
không phải automatic severity mapping hoặc duplicate policy đã implement.
39 case API/DB mới vẫn Not run. Ảnh lịch sử có Chờ TV2 không bị sửa nhãn/vẽ lại.

### Định dạng text trước commit

Bản records đưa lên Git chỉ bỏ khoảng trắng cuối dòng và dòng trắng thừa ở EOF
để qua git diff --check; không đổi số liệu, command hoặc kết quả. Manifest ghi
sha256 hiện tại, sha256_before_normalization và local_original khi có chuẩn hóa.
Raw logs/output và bản text trước chuẩn hóa vẫn ở local; ảnh và hai nguồn quyết
định được giữ nguyên byte. Hash source code trong records không bị thay đổi.

Password tạm do Spring tự sinh trong test log đã được che bằng
`[REDACTED_TEST_PASSWORD]`; manifest ghi provenance trước khi che. Không thay
đổi command, số test, kết quả hay source code hashes.
