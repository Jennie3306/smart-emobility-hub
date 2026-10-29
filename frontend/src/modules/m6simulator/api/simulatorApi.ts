import axios from "axios";
import { api, type ApiError } from "../../../shared/api/client";

export const scenarios = ["NORMAL_TICK", "VEHICLE_FAILURE", "CHARGER_FAILURE"] as const;
export type Scenario = typeof scenarios[number];
export type RunState = "IDLE" | "RUNNING" | "SUCCEEDED" | "FAILED";

export interface SimulationRun {
  runId: string;
  scenario: Scenario;
  // Preserve Java Long exactly, including seeds beyond Number.MAX_SAFE_INTEGER.
  seed: string;
  startedAt: string;
  completedAt: string | null;
  readingCount: number;
  processedCount: number;
  duplicateCount: number;
  rejectedCount: number;
  outboundEvents: { eventId: string; eventType: string; resourceType: string; resourceId: number }[];
  error: string | null;
}

export interface SimulatorStatus {
  schedulerEnabled: boolean;
  intervalMs: number | null;
  state: RunState;
  latestRun: SimulationRun | null;
}

export function normalizeSeed(input: string): string {
  const value = input.trim();
  if (!/^-?\d+$/.test(value)) throw new Error("Seed phải là số nguyên, không dùng phần thập phân.");
  const seed = BigInt(value);
  if (seed < -9223372036854775808n || seed > 9223372036854775807n) {
    throw new Error("Seed nằm ngoài phạm vi Long: -9223372036854775808 đến 9223372036854775807.");
  }
  return seed.toString();
}

// Only the numeric seed field is converted to a string before JSON.parse.
// A reviver would run after JavaScript had already rounded a large JSON number.
function parseResponse(raw: string) {
  try {
    return JSON.parse(raw.replace(/("seed"\s*:\s*)(-?\d+)(?=\s*[,}])/g, '$1"$2"'));
  } catch {
    return raw;
  }
}
const responseConfig = { transformResponse: [parseResponse] };

export const simulatorApi = {
  async status(signal?: AbortSignal): Promise<SimulatorStatus> {
    return (await api.get<SimulatorStatus>("/m6/simulator/status", { ...responseConfig, signal })).data;
  },
  async run(scenario: Scenario, input: string): Promise<SimulationRun> {
    const seed = normalizeSeed(input);
    // Raw JSON sends an exact numeric Long, not a string or a rounded JS number.
    return (await api.post<SimulationRun>(`/m6/simulator/scenarios/${scenario}`, `{"seed":${seed}}`, responseConfig)).data;
  },
};

export function describeApiError(error: unknown) {
  const response = axios.isAxiosError<ApiError>(error) ? error.response : undefined;
  const status = response?.status;
  const advice: Record<number, string> = {
    400: "Dữ liệu không hợp lệ. Kiểm tra scenario và seed rồi thử lại.",
    401: "Phiên đăng nhập đã hết hạn. Đăng nhập lại để tiếp tục.",
    403: "Chỉ tài khoản Operator được sử dụng simulator.",
    409: "Một run đang chạy. Làm mới trạng thái rồi chạy lại khi run kết thúc.",
  };
  return {
    status,
    message: (status && advice[status]) || "Không thể liên lạc hoặc hoàn tất yêu cầu. Kiểm tra kết nối rồi thử lại.",
    code: response?.data?.code,
    details: response?.data?.details,
  };
}
