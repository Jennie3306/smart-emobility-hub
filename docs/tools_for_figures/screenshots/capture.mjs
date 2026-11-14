// Screenshots of the running app, saved into screenshots/<module>/<kind>/<name>.png
//
//   node capture.mjs mockup            all mockup screens, automatic (read-only: opens pages, clicks tabs)
//   node capture.mjs mockup m5         only module M5
//   node capture.mjs real              real interface, step by step: you act in the browser, then press Enter
//   node capture.mjs real m2           only module M2
//   node capture.mjs list              print what will be captured
//
// Before running: backend and frontend are up (npm run dev on port 5173), demo data seeded.
// Options (environment variables): BASE=http://localhost:5173  PASSWORD=123456  WIDTH=1440  HEIGHT=900
import { chromium } from "playwright";
import { mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import readline from "node:readline";

const HERE = dirname(fileURLToPath(import.meta.url));
const BASE = process.env.BASE ?? "http://localhost:5173";
const PASSWORD = process.env.PASSWORD ?? "123456";
const VIEW = { width: Number(process.env.WIDTH ?? 1440), height: Number(process.env.HEIGHT ?? 900) };
const USERS = { student: "student1", operator: "operator1" };

// ---------------------------------------------------------------- what to capture
// mockup: clean screens for the UI-design section. Each step only opens a page or clicks a tab.
const MOCKUP = [
  { m: "shared", name: "login", role: null, path: "/login" },
  { m: "m1", name: "m1_dashboard", role: "operator", path: "/m1/dashboard" },
  { m: "m1", name: "m1_hub_list", role: "student", path: "/m1/hubs" },
  { m: "m1", name: "m1_hub_detail", role: "student", path: "/m1/hubs", click: { link: "/m1/hubs/" } },
  { m: "m1", name: "m1_alert_history", role: "operator", path: "/m1/alerts" },
  { m: "m2", name: "m2_search", role: "student", path: "/m2/search", click: { button: /^\s*Search\s*$/ } },
  { m: "m3", name: "m3_fleet", role: "operator", path: "/m3/vehicles" },
  { m: "m4", name: "m4_parking", role: "student", path: "/m4/parking" },
  { m: "m4", name: "m4_parking_operator", role: "operator", path: "/m4/parking" },
  { m: "m5", name: "m5_schedule", role: "student", path: "/m5/charging" },
  { m: "m5", name: "m5_operator", role: "operator", path: "/m5/charging" },
  { m: "m6", name: "m6_incidents", role: "operator", path: "/m6/operations" },
  { m: "m6", name: "m6_simulator", role: "operator", path: "/m6/operations", click: { button: /Simulator events/ } },
  { m: "m7", name: "m7_redistribution", role: "operator", path: "/m7/redistribution" },
  { m: "m8", name: "m8_whatif", role: "operator", path: "/m8/simulation" },
  { m: "m8", name: "m8_history", role: "operator", path: "/m8/simulation", click: { button: /History & comparison/ } },
  { m: "m8", name: "m8_network", role: "operator", path: "/m8/simulation", click: { button: /Network snapshot/ } },
];

// real: the system at work. The script opens the page; you do the action, then press Enter.
// Keep this order: M3 picks up the vehicle that M2 reserved.
const REAL = [
  { m: "m1", name: "m1_alert_open", role: "operator", path: "/m1/dashboard",
    todo: "Khung Open alerts có ít nhất một cảnh báo OPEN. Nếu chưa có, đợi 30 giây rồi bấm ↻ Refresh data." },
  { m: "m1", name: "m1_alert_acknowledged", role: "operator", path: "/m1/dashboard",
    todo: "Bấm Acknowledge ở một cảnh báo, đợi trạng thái chuyển sang ACKNOWLEDGED." },
  { m: "m2", name: "m2_search_results", role: "student", path: "/m2/search",
    todo: "Chọn hub (và loại xe nếu muốn), bấm Search để hiện danh sách xe." },
  { m: "m2", name: "m2_vehicle_detail", role: "student", path: "/m2/search",
    todo: "Bấm Search, rồi bấm Details ở một xe để mở chi tiết xe và pin." },
  { m: "m2", name: "m2_my_reservation", role: "student", path: "/m2/search",
    todo: "Bấm Search, rồi Book vehicle ở một xe. Đợi trang My reservation hiện CONFIRMED và đồng hồ đếm ngược." },
  { m: "m2", name: "m2_limit_error", role: "student", path: "/m2/search",
    todo: "Trong lúc đang giữ chỗ, thử Book vehicle một xe khác để hiện thông báo lỗi (đã có đặt chỗ hiệu lực)." },
  { m: "m3", name: "m3_current_trip", role: "student", path: "/m2/my-reservation",
    todo: "Bấm Pick up vehicle. Đợi thẻ chuyến đi hiện ra (xe IN_USE, thời gian chuyến đi)." },
  { m: "m3", name: "m3_returned", role: "student", path: "/m2/my-reservation",
    todo: "Chọn hub trả xe, nhập mức pin, bấm Return vehicle. Chụp khi hiện thông báo trả xe thành công." },
  { m: "m4", name: "m4_booked_ticket", role: "student", path: "/m4/parking",
    todo: "Tab Book a space: chọn hub, xe và khung giờ bắt đầu ngay, bấm Book space. Mở tab My tickets, thấy vé và đếm ngược check-in." },
  { m: "m4", name: "m4_checked_in", role: "student", path: "/m4/parking",
    todo: "Tab My tickets: bấm Check in. Chụp khi vé ghi 'Checked in at …'." },
  { m: "m4", name: "m4_operator_spaces", role: "operator", path: "/m4/parking",
    todo: "Chọn hub vừa đặt chỗ, thấy chỗ đó OCCUPIED trong bảng chỗ đỗ." },
  { m: "m5", name: "m5_request_submitted", role: "student", path: "/m5/charging",
    todo: "Cần một xe cá nhân đã đăng ký (Parking → My vehicles; nếu chưa có thì Add a vehicle). Lấy ID của xe: mở tab mới http://localhost:5173/api/m4/vehicles/mine, đọc số \"id\". Về form Request charging: Hub = hub có trụ sạc, Vehicle kind = Personal, Vehicle ID = số id đó, Battery level < 90 (vd 35), Parking space ID để trống → Submit charging request. Chụp khi bảng Your charging sessions có phiên mới QUEUED hoặc SCHEDULED." },
  { m: "m5", name: "m5_operator_queue", role: "operator", path: "/m5/charging",
    todo: "Chọn đúng hub vừa gửi yêu cầu ở ô Hub (góc trên phải), thấy phiên PERSONAL #<id> trong hàng đợi hoặc danh sách đang hoạt động." },
  { m: "m5", name: "m5_charging_started", role: "student", path: "/m5/charging",
    todo: "Ở bảng Your charging sessions, khi phiên đã SCHEDULED (có cổng CP-…) thì bấm Start. Nếu còn QUEUED, bấm ↻ Refresh tới khi SCHEDULED. Chụp khi hiện 'Charging session started.' và trạng thái CHARGING." },
  { m: "m6", name: "m6_incident_reported", role: "operator", path: "/m6/operations",
    todo: "Bấm Report an incident, điền form, bấm Submit report. Chụp khi sự cố mới hiện REPORTED trong bảng." },
  { m: "m6", name: "m6_incident_assigned", role: "operator", path: "/m6/operations",
    todo: "Bấm vào sự cố vừa tạo, nhập operator1 ở ô Assignee, bấm Assign. Chụp khi trạng thái là ASSIGNED." },
  { m: "m6", name: "m6_simulator_run", role: "operator", path: "/m6/operations",
    todo: "Mở tab Simulator events, bấm Run scenario, đợi lần chạy Completed." },
  { m: "m7", name: "m7_pending", role: "operator", path: "/m7/redistribution",
    todo: "Thấy một đề xuất PENDING (sinh từ cảnh báo thiếu xe của M1). Nếu chưa có, bấm ↻ Refresh." },
  { m: "m7", name: "m7_tasks_created", role: "operator", path: "/m7/redistribution",
    todo: "Bấm Approve ở đề xuất. Chụp khi các task mới hiện trong danh sách." },
  { m: "m7", name: "m7_task_done", role: "operator", path: "/m7/redistribution",
    todo: "Bấm Start rồi Mark done ở một task. Chụp khi task chuyển sang DONE." },
  { m: "m8", name: "m8_extracted", role: "operator", path: "/m8/simulation",
    todo: "Ô 'Fill parameters from a description' gõ đúng: Close Metro Hub for 120 minutes, 40 students need a ride → bấm ✧ Extract parameters. Chụp khi hiện 'Local rules filled the form…', Affected hub = Metro Hub, ô Close the hub đã tick." },
  { m: "m8", name: "m8_run_result", role: "operator", path: "/m8/simulation",
    todo: "Trang được mở lại: gõ lại mô tả trên, Extract, đổi Scenario name thành 'Close Metro Hub - peak hour', bấm ▶ Save & run simulation. Chụp khi hiện 4 ô KPI, biểu đồ và Recommended plans (gõ f để chụp cả trang)." },
  { m: "m8", name: "m8_compare", role: "operator", path: "/m8/simulation",
    todo: "Mở History & comparison → View & edit ở run vừa chạy. Ở form: đổi tên 'Close Metro Hub - reroute', tick Allow requests to use other hubs, giữ Snapshot source là run cũ → Save & run. Mở lại History & comparison, tick ô Select của hai run này. Chụp khi hiện KPI comparison." },
];

// ---------------------------------------------------------------- helpers
const [mode = "list", only] = process.argv.slice(2);
const pick = list => list.filter(s => !only || s.m === only);
const out = (s, kind) => {
  const dir = s.m === "shared" ? join(HERE, "shared", "mockup") : join(HERE, s.m, kind);
  mkdirSync(dir, { recursive: true });
  return join(dir, `${s.name}.png`);
};

async function settle(page) {
  await page.waitForLoadState("load");
  await page.waitForSelector('[aria-busy="true"]', { state: "detached", timeout: 15000 }).catch(() => {});
  await page.waitForTimeout(1200); // charts and fonts
}

const sessions = {};
async function pageFor(browser, role) {
  if (sessions[role ?? "anon"]) return sessions[role ?? "anon"];
  const context = await browser.newContext({ viewport: VIEW, deviceScaleFactor: 2 });
  const page = await context.newPage();
  if (role) {
    await page.goto(`${BASE}/login`);
    await page.fill('input[autocomplete="username"]', USERS[role]);
    await page.fill('input[autocomplete="current-password"]', PASSWORD);
    await page.click('button[type="submit"]');
    await page.waitForURL(u => !u.pathname.startsWith("/login"), { timeout: 15000 })
      .catch(() => { throw new Error(`Login as ${USERS[role]} failed: check BASE (${BASE}), the backend and CORS (port 5173).`); });
  }
  return (sessions[role ?? "anon"] = page);
}

async function click(page, c) {
  if (c.button) await page.getByRole("button", { name: c.button }).first().click();
  if (c.link) await page.locator(`a[href^="${c.link}"]`).first().click();
  await settle(page);
}

// Hide scrollbars; for a full page, grow the viewport to the page height instead of Playwright's fullPage,
// which repeats the sticky sidebar and header in the middle of the image.
async function snap(page, path, full = false) {
  await page.addStyleTag({ content: "html,body,*{scrollbar-width:none!important}::-webkit-scrollbar{display:none!important}" });
  if (!full) { await page.screenshot({ path }); return; }
  await page.evaluate(() => window.scrollTo(0, 0));
  const height = await page.evaluate(() => Math.max(document.documentElement.scrollHeight, document.body.scrollHeight));
  await page.setViewportSize({ width: VIEW.width, height: Math.min(height, 6000) });
  await page.waitForTimeout(800); // charts resize
  await page.screenshot({ path });
  await page.setViewportSize(VIEW);
}

// ---------------------------------------------------------------- run
if (mode === "list") {
  for (const [kind, list] of [["mockup", MOCKUP], ["real", REAL]])
    for (const s of pick(list)) console.log(`${kind.padEnd(6)} ${s.m.padEnd(6)} ${s.name.padEnd(24)} ${s.role ?? "-"}  ${s.path}`);
  process.exit(0);
}
if (!["mockup", "real"].includes(mode)) { console.log("Usage: node capture.mjs mockup|real|list [m1..m8|shared]"); process.exit(1); }

const browser = await chromium.launch({ headless: mode === "mockup" || process.env.HEADLESS === "1" });
try {
  if (mode === "mockup") {
    for (const s of pick(MOCKUP)) {
      const page = await pageFor(browser, s.role);
      await page.goto(BASE + s.path); await settle(page);
      if (s.click) await click(page, s.click);
      await snap(page, out(s, "mockup"));
      console.log("✓", out(s, "mockup"));
    }
  } else {
    const rl = readline.createInterface({ input: process.stdin });
    const queued = []; let waiting = null; let closed = false;
    rl.on("line", l => { if (waiting) { const w = waiting; waiting = null; w(l); } else queued.push(l); });
    rl.on("close", () => { closed = true; if (waiting) waiting("s"); });
    const ask = q => { process.stdout.write(q); return queued.length ? Promise.resolve(queued.shift()) : closed ? Promise.resolve("s") : new Promise(r => (waiting = r)); };
    for (const s of pick(REAL)) {
      const page = await pageFor(browser, s.role);
      await page.bringToFront();
      await page.goto(BASE + s.path); await settle(page);
      console.log(`\n[${s.m}] ${s.name}  (${USERS[s.role]})\n  → ${s.todo}`);
      const a = (await ask("  Enter = chụp, f + Enter = chụp cả trang, s + Enter = bỏ qua: ")).trim().toLowerCase();
      if (a === "s") continue;
      await page.waitForTimeout(300);
      await snap(page, out(s, "real"), a === "f");
      console.log("  ✓", out(s, "real"));
    }
    rl.close();
  }
} finally {
  await browser.close();
}
