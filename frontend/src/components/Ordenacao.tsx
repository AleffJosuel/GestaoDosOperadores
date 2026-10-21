import { useMemo, useState } from "react";
import { Icone } from "./Icone";

export type Direcao = "asc" | "desc";
type Valor = string | number | null | undefined;

const comparadorTexto = new Intl.Collator("pt-BR", { numeric: true, sensitivity: "base" });

/** Compara dois valores de coluna; vazios vão sempre para o fim, em qualquer direção. */
export function comparar(a: Valor, b: Valor, dir: Direcao): number {
  const vazioA = a == null || a === "";
  const vazioB = b == null || b === "";
  if (vazioA || vazioB) return vazioA === vazioB ? 0 : vazioA ? 1 : -1;
  const c =
    typeof a === "number" && typeof b === "number"
      ? a - b
      : comparadorTexto.compare(String(a), String(b));
  return dir === "asc" ? c : -c;
}

/**
 * Ordenação de tabela no cliente. `colunas` deve ser estável (constante de módulo):
 * mapeia a chave da coluna para o valor usado na comparação.
 */
export function useOrdenacao<T, K extends string>(
  itens: T[],
  colunas: Record<K, (item: T) => Valor>,
  inicial: NoInfer<K>
) {
  const [chave, setChave] = useState<K>(inicial);
  const [dir, setDir] = useState<Direcao>("asc");

  const ordenados = useMemo(() => {
    const valor = colunas[chave];
    // sort é estável: empates mantêm a ordem que veio do servidor.
    return [...itens].sort((a, b) => comparar(valor(a), valor(b), dir));
  }, [itens, colunas, chave, dir]);

  function alternar(k: K) {
    if (k === chave) setDir((d) => (d === "asc" ? "desc" : "asc"));
    else {
      setChave(k);
      setDir("asc");
    }
  }

  return { ordenados, chave, dir, alternar };
}

/** Cabeçalho clicável; anuncia a ordem atual via aria-sort. */
export function ThOrdenavel({
  rotulo,
  ativo,
  dir,
  onOrdenar,
  className,
}: {
  rotulo: string;
  ativo: boolean;
  dir: Direcao;
  onOrdenar: () => void;
  className?: string;
}) {
  return (
    <th
      className={className}
      aria-sort={ativo ? (dir === "asc" ? "ascending" : "descending") : "none"}
    >
      <button type="button" className={`th-ordenar${ativo ? " ativo" : ""}`} onClick={onOrdenar}>
        {rotulo}
        <Icone nome={ativo ? (dir === "asc" ? "cima" : "baixo") : "ordenar"} />
      </button>
    </th>
  );
}
