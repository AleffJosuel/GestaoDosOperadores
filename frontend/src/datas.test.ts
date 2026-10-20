import { afterEach, describe, expect, it, vi } from "vitest";
import { emIso, hoje } from "./datas";

/**
 * Estes testes rodam com TZ=America/Sao_Paulo (fixado em vite.config.ts), porque o
 * defeito que eles cobrem só aparece em fuso negativo: `toISOString()` é UTC, então a
 * partir das 21h ele já devolve o dia seguinte. A data de calendário tem que ser a local.
 */
describe("emIso", () => {
  it("usa a data local, não a de UTC, no fim do dia", () => {
    // 21h no Brasil já é 00h do dia seguinte em UTC — é aqui que o bug aparecia.
    expect(emIso(new Date("2026-10-20T21:00:00-03:00"))).toBe("2026-10-20");
    expect(emIso(new Date("2026-10-20T23:59:00-03:00"))).toBe("2026-10-20");
  });

  it("vira o dia só depois da meia-noite local", () => {
    expect(emIso(new Date("2026-10-20T20:59:00-03:00"))).toBe("2026-10-20");
    expect(emIso(new Date("2026-10-21T00:01:00-03:00"))).toBe("2026-10-21");
  });

  it("preenche mês e dia com zero à esquerda", () => {
    expect(emIso(new Date("2026-01-05T10:00:00-03:00"))).toBe("2026-01-05");
  });

  it("devolve sempre 10 caracteres no formato AAAA-MM-DD", () => {
    const d = emIso(new Date("2026-12-31T22:30:00-03:00"));
    expect(d).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(d).toBe("2026-12-31");
  });
});

describe("hoje", () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it("não adianta para o dia seguinte às 21h", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-10-20T21:30:00-03:00"));
    expect(hoje()).toBe("2026-10-20");
  });

  it("acompanha a virada do dia local", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-10-21T00:05:00-03:00"));
    expect(hoje()).toBe("2026-10-21");
  });

  it("nunca é uma data futura para o backend (@PastOrPresent)", () => {
    // O backend compara com LocalDate.now() no fuso do servidor; se hoje() adiantasse,
    // a criação de avaliação e de registro de acompanhamento seria rejeitada à noite.
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-10-20T22:00:00-03:00"));
    const dataLocalDoServidor = "2026-10-20";
    expect(hoje() <= dataLocalDoServidor).toBe(true);
  });
});
