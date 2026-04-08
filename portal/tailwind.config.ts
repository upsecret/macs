import type { Config } from "tailwindcss";

export default {
  content: ["./app/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        "navbar-bg": "#2f3034",
        bg: "#f8f8f9",
        primary: "#4747b3",
        secondary: "#e7e6ff",
        accent: "#ff9a26",
        info: "#2b65d9",
        error: "#e10013",
        header: "#eaeaed",
        "primary-text": "#8f8dc4",
      },
    },
  },
  plugins: [],
} satisfies Config;
