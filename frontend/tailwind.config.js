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
    },
  },
  plugins: [],
}

