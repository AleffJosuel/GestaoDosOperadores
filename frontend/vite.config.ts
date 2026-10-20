/// <reference types="vitest/config" />
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
  test: {
    include: ["src/**/*.test.ts", "src/**/*.test.tsx"],
    // Fuso fixo: as regras de data do sistema são em horário local, e o bug que motivou
    // estes testes só aparece em fuso negativo (à noite, UTC já está no dia seguinte).
    // Sem isto, o resultado dependeria do relógio de quem roda.
    env: { TZ: "America/Sao_Paulo" },
  },
});
