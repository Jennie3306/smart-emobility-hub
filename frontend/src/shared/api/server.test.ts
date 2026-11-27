// @vitest-environment jsdom
import { afterEach, describe, expect, it } from "vitest";
import { apiUrl, isNativeApp, serverUrl, setServerUrl } from "./server";

const win = window as unknown as { Capacitor?: { isNativePlatform: () => boolean } };

afterEach(() => { delete win.Capacitor; localStorage.clear(); });

describe("server address", () => {
  it("keeps relative URLs in the browser, even if an address was stored", () => {
    localStorage.setItem("emh.serverUrl", "http://10.0.0.5:8080");
    expect(isNativeApp()).toBe(false);
    expect(apiUrl("/api/m1")).toBe("/api/m1");
  });

  it("uses the stored address inside the app", () => {
    win.Capacitor = { isNativePlatform: () => true };
    setServerUrl(" 192.168.1.20:8080/ ");
    expect(serverUrl()).toBe("http://192.168.1.20:8080");
    expect(apiUrl("/ws")).toBe("http://192.168.1.20:8080/ws");
  });

  it("keeps an explicit https address", () => {
    win.Capacitor = { isNativePlatform: () => true };
    setServerUrl("https://hub.example.edu");
    expect(apiUrl("/api")).toBe("https://hub.example.edu/api");
  });
});
