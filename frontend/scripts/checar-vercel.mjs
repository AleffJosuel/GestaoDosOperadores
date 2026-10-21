/**
 * Trava de build: a CSP do `vercel.json` precisa liberar, em `connect-src`, a mesma API
 * que o bundle vai chamar (VITE_API_URL).
 *
 * Por que isto existe: `vercel.json` é JSON estático, lido pela Vercel ANTES do build —
 * não dá para interpolar variável de ambiente nele. Sem esta checagem, publicar com o
 * endereço de exemplo ainda ali gera um site que carrega normalmente e falha só na
 * primeira chamada à API, com um erro de CSP no console que não aparece na tela.
 *
 * Só vale para build de produção: com API em localhost, não faz nada.
 */
import { existsSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const raiz = join(dirname(fileURLToPath(import.meta.url)), "..");
const LOCAIS = ["localhost", "127.0.0.1", "[::1]", "::1"];

function urlDaApi() {
  if (process.env.VITE_API_URL) return process.env.VITE_API_URL.trim();
  for (const arquivo of [".env.production", ".env"]) {
    const caminho = join(raiz, arquivo);
    if (!existsSync(caminho)) continue;
    const linha = readFileSync(caminho, "utf8")
      .split(/\r?\n/)
      .find((l) => l.trim().startsWith("VITE_API_URL="));
    if (linha) return linha.slice(linha.indexOf("=") + 1).trim();
  }
  return null;
}

function falhar(mensagem) {
  console.error(`\n[checar-vercel] ${mensagem}\n`);
  process.exit(1);
}

const bruta = urlDaApi();
if (!bruta) {
  console.log("[checar-vercel] VITE_API_URL não definida; nada a conferir (build local).");
  process.exit(0);
}

let origem;
try {
  origem = new URL(bruta).origin;
} catch {
  falhar(`VITE_API_URL não é uma URL válida: ${bruta}`);
}

const host = new URL(bruta).hostname;
if (LOCAIS.includes(host) || host.endsWith(".localhost")) {
  console.log(`[checar-vercel] API local (${origem}); conferência pulada.`);
  process.exit(0);
}

const caminhoVercel = join(raiz, "vercel.json");
if (!existsSync(caminhoVercel)) {
  falhar("vercel.json não encontrado: sem ele o site vai ao ar sem CSP e sem fallback de SPA.");
}

const config = JSON.parse(readFileSync(caminhoVercel, "utf8"));
const csp = (config.headers ?? [])
  .flatMap((h) => h.headers ?? [])
  .find((h) => h.key.toLowerCase() === "content-security-policy")?.value;

if (!csp) {
  falhar("vercel.json não declara Content-Security-Policy.");
}
if (csp.includes("SUA-API.exemplo.com")) {
  falhar(
    "vercel.json ainda tem o endereço de exemplo na CSP.\n" +
      `  Troque 'https://SUA-API.exemplo.com' por '${origem}' em connect-src.`
  );
}
if (!csp.includes(origem)) {
  falhar(
    `a CSP do vercel.json não libera a API que o bundle vai chamar.\n` +
      `  VITE_API_URL = ${origem}\n` +
      `  connect-src  = ${csp.split(";").find((d) => d.includes("connect-src"))?.trim() ?? "(ausente)"}\n` +
      `  Inclua '${origem}' em connect-src.`
  );
}

console.log(`[checar-vercel] ok: CSP libera ${origem}.`);
