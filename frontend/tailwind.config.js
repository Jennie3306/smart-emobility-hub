/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        // Design system do TV5 chot - moi module dung token nay, khong tu dat mau
        brand:   { DEFAULT: "#1F4E79", light: "#2E75B6" },
        state:   { available: "#16a34a", busy: "#f59e0b", fault: "#dc2626", idle: "#64748b" },
      },
    },
  },
  plugins: [],
};
