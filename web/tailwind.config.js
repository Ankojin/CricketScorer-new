/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        brand: {
          50: '#ecfdf5',
          100: '#d1fae5',
          500: '#10b981',
          600: '#059669',
          700: '#047857',
          800: '#065f46',
          900: '#064e3b',
        },
        cricNavy: {
          500: '#071B33',
          600: '#0E325A',
          700: '#0D2A4A',
          800: '#0A2544',
          900: '#041326',
        },
        cricGreen: {
          500: '#13A968',
          600: '#087A4A',
        },
        cricElectric: {
          500: '#49E878',
          600: '#39F57A',
        },
        cricBorder: '#29415B',
        cricRed: {
          500: '#D92D20',
        },
        cricAmber: {
          500: '#F59E0B',
        }
      },
      fontFamily: {
        sans: ['Inter', 'sans-serif'],
        mono: ['JetBrains Mono', 'monospace'],
      }
    },
  },
  plugins: [],
}
