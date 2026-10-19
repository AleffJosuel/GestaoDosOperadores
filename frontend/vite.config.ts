import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// O front roda em http://localhost:5173 (origem liberada no CORS do backend).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // Mantém SEMPRE a 5173 (origem liberada no CORS do backend); se estiver ocupada, falha avisando.
    strictPort: true,
  },
});
