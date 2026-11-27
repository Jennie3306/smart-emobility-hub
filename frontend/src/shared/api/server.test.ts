// @vitest-environment jsdom
import { afterEach, describe, expect, it } from "vitest";
import { apiUrl, isHtmlPage, isNativeApp, resolveApiBase, serverUrl, setServerUrl } from "./server";

const win = window as unknown as { Capacitor?: object; androidBridge?: object };

afterEach(() => { delete win.Capacitor; delete win.androidBridge; localStorage.clear(); });

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

  it("recognises the app from the bare Android bridge (no @capacitor/core in the bundle)", () => {
    win.androidBridge = {};
    setServerUrl("10.0.0.5:8080");
    expect(apiUrl("/api")).toBe("http://10.0.0.5:8080/api");
  });

  it("keeps an explicit https address", () => {
    win.Capacitor = { isNativePlatform: () => true };
    setServerUrl("https://hub.example.edu");
    expect(apiUrl("/api")).toBe("https://hub.example.edu/api");
  });

  it("refuses to call the API from the app before an address is set", () => {
    win.androidBridge = {};
    expect(() => resolveApiBase("/api")).toThrow();
    setServerUrl("10.0.0.5:8080");
    expect(resolveApiBase("/api")).toBe("http://10.0.0.5:8080/api");
  });

  it("recognises an HTML page returned instead of JSON", () => {
    expect(isHtmlPage("<!doctype html><html>")).toBe(true);
    expect(isHtmlPage("  <html lang=en>")).toBe(true);
    expect(isHtmlPage([{ id: 1 }])).toBe(false);
    expect(isHtmlPage("plain text")).toBe(false);
  });
});
