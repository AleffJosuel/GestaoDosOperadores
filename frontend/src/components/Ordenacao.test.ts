import { describe, expect, it } from "vitest";
import { comparar } from "./Ordenacao";

const ordenar = <T extends string | number | null>(xs: T[], dir: "asc" | "desc" = "asc") =>
  [...xs].sort((a, b) => comparar(a, b, dir));

describe("comparar (ordenação das tabelas)", () => {
  it("ordena texto em português ignorando acento e caixa", () => {
    expect(ordenar(["Érica", "ana", "Bruno", "Álvaro"])).toEqual(["Álvaro", "ana", "Bruno", "Érica"]);
  });

  it("ordena matrícula com zeros à esquerda pelo valor numérico", () => {
    expect(ordenar(["00110", "00101", "00009"])).toEqual(["00009", "00101", "00110"]);
  });

  it("ordena números e inverte na direção descendente", () => {
    expect(ordenar([3, 1, 5])).toEqual([1, 3, 5]);
    expect(ordenar([3, 1, 5], "desc")).toEqual([5, 3, 1]);
  });

  it("mantém vazios no fim nas duas direções", () => {
    expect(ordenar(["b", null, "a"])).toEqual(["a", "b", null]);
    expect(ordenar(["b", null, "a"], "desc")).toEqual(["b", "a", null]);
  });
});
