import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

 
// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    allowedHosts: 'all',   // allows any tunnel URL (cloudflare, ngrok, localtonet etc.)
    host: true,            // listen on all network interfaces
  }
})
 
