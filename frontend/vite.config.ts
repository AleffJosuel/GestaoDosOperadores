/// <reference types="vitest/config" />
import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";

/**
 * Cabeçalhos de segurança do build de produção (servidos pelo `vite preview` e documentados no
 * README para quem hospedar o `dist/`). Não valem no `npm run dev`: o HMR do Vite precisa de
 * script inline e WebSocket, que esta CSP bloquearia.
 */
function cabecalhosSeguranca(apiUrl: string): Record<string, string> {
  const api = new URL(apiUrl).origin;
  return {
    "Content-Security-Policy": [
      "default-src 'self'",
      "script-src 'self'",
      "style-src 'self'",
      "img-src 'self'",
      "font-src 'self'",
      `connect-src 'self' ${api}`,
      "object-src 'none'",
      "base-uri 'self'",
      "form-action 'self'",
      "frame-ancestors 'none'",
    ].join("; "),
    "X-Content-Type-Options": "nosniff",
    "X-Frame-Options": "DENY",
    "Referrer-Policy": "no-referrer",
    "Permissions-Policy": "camera=(), microphone=(), geolocation=()",
  };
}

// O front roda em http://localhost:5173 (origem liberada no CORS do backend).
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "VITE_");
  return {
    plugins: [react()],
    server: {
      port: 5173,
      // Mantém SEMPRE a 5173 (origem liberada no CORS do backend); se estiver ocupada, falha avisando.
      strictPort: true,
    },
    preview: {
      // Mesma porta do dev: é a única origem que o CORS do backend aceita.
      port: 5173,
      strictPort: true,
      headers: cabecalhosSeguranca(env.VITE_API_URL ?? "http://localhost:8080"),
    },
    test: {
      include: ["src/**/*.test.ts", "src/**/*.test.tsx"],
      // Fuso fixo: as regras de data do sistema são em horário local, e o bug que motivou
      // estes testes só aparece em fuso negativo (à noite, UTC já está no dia seguinte).
      // Sem isto, o resultado dependeria do relógio de quem roda.
      env: { TZ: "America/Sao_Paulo" },
    },
  };
});
