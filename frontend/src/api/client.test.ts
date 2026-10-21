import { afterEach, describe, expect, it, vi } from "vitest";
import { api, ApiError } from "./client";

function responder(status: number, corpo: string, tipo = "application/json") {
  vi.stubGlobal("fetch", vi.fn(async () => new Response(corpo, { status, headers: { "Content-Type": tipo } })));
}

async function erroDe(p: Promise<unknown>): Promise<ApiError> {
  try {
    await p;
  } catch (e) {
    if (e instanceof ApiError) return e;
    throw e;
  }
  throw new Error("era esperado um ApiError");
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("cliente da API — tratamento de erro", () => {
  it("usa a mensagem e os erros por campo que o backend manda em JSON", async () => {
    responder(400, JSON.stringify({ erro: "Dados inválidos", campos: { nome: "obrigatório" } }));
    const e = await erroDe(api.criarCompetencia({ nome: "", tipo: "TECNICA" }));
    expect(e.status).toBe(400);
    expect(e.message).toBe("Dados inválidos");
    expect(e.campos).toEqual({ nome: "obrigatório" });
  });

  it("erro com corpo HTML (proxy) vira mensagem legível em vez de estourar no JSON.parse", async () => {
    responder(502, "<html><body>Bad Gateway</body></html>", "text/html");
    const e = await erroDe(api.dashboard());
    expect(e.status).toBe(502);
    expect(e.message).toMatch(/indisponível/);
  });

  it("erro sem corpo usa a mensagem padrão do status", async () => {
    responder(404, "");
    const e = await erroDe(api.buscarColaborador(99));
    expect(e.message).toBe("Registro não encontrado.");
  });

  it("sucesso com corpo que não é JSON é tratado como resposta inválida", async () => {
    responder(200, "<html></html>", "text/html");
    const e = await erroDe(api.dashboard());
    expect(e.message).toBe("Resposta inválida do servidor.");
  });

  it("sucesso com JSON devolve os dados", async () => {
    responder(200, JSON.stringify([{ id: 1 }]));
    await expect(api.listarTodosPdi()).resolves.toEqual([{ id: 1 }]);
  });
});
