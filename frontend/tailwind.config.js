/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      fontFamily: {
        sans: ['"Be Vietnam Pro"', "Inter", "ui-sans-serif", "system-ui", '"Segoe UI"', "sans-serif"],
      },
      colors: {
        // Design system (TV5) - every module uses these tokens instead of picking its own colours
        brand:   { DEFAULT: "#1F4E79", light: "#2E75B6", dark: "#163A5C", 50: "#EEF4FA", 100: "#EAF1F8" },
        accent:  { DEFAULT: "#B4690E", light: "#D9822B" },
        canvas:  "#F3F6FA",
        state:   { available: "#16a34a", busy: "#f59e0b", fault: "#dc2626", idle: "#64748b" },
      },
    },
  },
  plugins: [],
};
