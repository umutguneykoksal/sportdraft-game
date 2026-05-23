/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ["./src/**/*.{js,jsx,ts,tsx}"],
  theme: {
    extend: {
      colors: {
        pitch: {
          dark: "#1a472a",
          light: "#2d8a4e",
        },
        card: {
          gold: "#c9a227",
          silver: "#8a8d91",
          bronze: "#cd7f32",
        },
        surface: {
          DEFAULT: "#0f1520",
          card: "#1a2232",
          elevated: "#243044",
        },
        accent: {
          DEFAULT: "#3b82f6",
          green: "#22c55e",
          red: "#ef4444",
          amber: "#f59e0b",
        },
      },
      animation: {
        "fade-in": "fadeIn 0.4s ease-out",
        "fade-in-up": "fadeInUp 0.5s ease-out",
        "bid-pop": "bidPop 0.35s ease-out",
      },
      keyframes: {
        fadeIn: {
          "0%": { opacity: "0", transform: "translateY(8px)" },
          "100%": { opacity: "1", transform: "translateY(0)" },
        },
        fadeInUp: {
          "0%": { opacity: "0", transform: "translateY(16px)" },
          "100%": { opacity: "1", transform: "translateY(0)" },
        },
        bidPop: {
          "0%": { transform: "scale(1)", opacity: "0.7" },
          "50%": { transform: "scale(1.15)" },
          "100%": { transform: "scale(1)", opacity: "1" },
        },
      },
    },
  },
  plugins: [],
}

