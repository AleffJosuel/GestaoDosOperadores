import { useEffect, useRef, useState } from "react";
import type { FaixaContagem } from "../api/types";

const corDaFaixa: Record<string, string> = {
  CRITICO: "var(--cor-critico)",
  A_DESENVOLVER: "var(--cor-desenvolver)",
  ADEQUADO: "var(--cor-adequado)",
  BOM: "var(--cor-bom)",
};

const RAIO = 48;
const CIRC = 2 * Math.PI * RAIO;

interface Fatia extends FaixaContagem {
  pct: number;
  // comprimento do arco e deslocamento acumulado no círculo
  comprimento: number;
  offset: number;
  cor: string;
}

/**
 * Donut (rosca) da distribuição por faixa, feito em SVG puro (sem dependência).
 * Hover realça a fatia e mostra um tooltip; clicar numa fatia/legenda chama
 * `onSelecionar(codigo)`. Entra com uma animação de crescimento ao montar.
 */
export function DonutFaixas({
  distribuicao,
  onSelecionar,
}: {
  distribuicao: FaixaContagem[];
  onSelecionar: (codigo: string) => void;
}) {
  const [montado, setMontado] = useState(false);
  const [ativo, setAtivo] = useState<string | null>(null);
  const [tip, setTip] = useState<{ x: number; y: number } | null>(null);
  const wrapRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    // Dispara a animação de entrada no próximo frame (garante a transição).
    const id = requestAnimationFrame(() => setMontado(true));
    return () => cancelAnimationFrame(id);
  }, []);

  const total = distribuicao.reduce((s, f) => s + f.quantidade, 0);

  // Monta as fatias (só as com quantidade > 0 viram arco), acumulando o offset.
  let acumulado = 0;
  const fatias: Fatia[] = distribuicao
    .filter((f) => f.quantidade > 0)
    .map((f) => {
      const pct = (f.quantidade / total) * 100;
      const comprimento = (f.quantidade / total) * CIRC;
      const fatia: Fatia = {
        ...f,
        pct,
        comprimento,
        offset: -acumulado,
        cor: corDaFaixa[f.classificacao.codigo] ?? "var(--cor-texto-suave)",
      };
      acumulado += comprimento;
      return fatia;
    });

  const faixaAtiva = fatias.find((f) => f.classificacao.codigo === ativo) ?? null;

  function moverTip(e: React.MouseEvent) {
    const r = wrapRef.current?.getBoundingClientRect();
    if (r) setTip({ x: e.clientX - r.left, y: e.clientY - r.top });
  }

  return (
    <div className="donut" ref={wrapRef} onMouseMove={moverTip}>
      <div className="donut-grafico">
        <svg
          className={`donut-svg${montado ? " montado" : ""}`}
          viewBox="0 0 120 120"
          role="img"
          aria-label={
            "Distribuição por faixa: " +
            distribuicao.map((f) => `${f.classificacao.rotulo} ${f.quantidade}`).join(", ")
          }
        >
          {/* trilho de fundo */}
          <circle
            cx="60"
            cy="60"
            r={RAIO}
            fill="none"
            stroke="var(--cor-superficie-2)"
            strokeWidth="16"
          />
          <g className="donut-fatias">
            <g transform="rotate(-90 60 60)">
              {fatias.map((f) => {
                const realce = ativo === f.classificacao.codigo;
                return (
                  <circle
                    key={f.classificacao.codigo}
                    cx="60"
                    cy="60"
                    r={RAIO}
                    fill="none"
                    stroke={f.cor}
                    strokeWidth={realce ? 22 : 16}
                    strokeDasharray={`${f.comprimento} ${CIRC - f.comprimento}`}
                    strokeDashoffset={f.offset}
                    className="donut-fatia"
                    opacity={ativo && !realce ? 0.45 : 1}
                    onMouseEnter={() => setAtivo(f.classificacao.codigo)}
                    onMouseLeave={() => setAtivo(null)}
                    onClick={() => onSelecionar(f.classificacao.codigo)}
                  >
                    <title>{`${f.classificacao.rotulo}: ${f.quantidade} (${Math.round(f.pct)}%)`}</title>
                  </circle>
                );
              })}
            </g>
          </g>
          {/* centro: total ou a fatia em foco */}
          <text x="60" y="55" className="donut-centro-num" textAnchor="middle">
            {faixaAtiva ? faixaAtiva.quantidade : total}
          </text>
          <text x="60" y="72" className="donut-centro-rot" textAnchor="middle">
            {faixaAtiva ? `${Math.round(faixaAtiva.pct)}%` : "avaliados"}
          </text>
        </svg>
      </div>

      {faixaAtiva && tip && (
        <div className="donut-tip" style={{ left: tip.x, top: tip.y }} role="tooltip">
          <strong>{faixaAtiva.classificacao.rotulo}</strong>
          <br />
          {faixaAtiva.quantidade} · {Math.round(faixaAtiva.pct)}%
        </div>
      )}

      <ul className="donut-legenda">
        {distribuicao.map((f) => {
          const pct = total ? Math.round((f.quantidade / total) * 100) : 0;
          return (
            <li key={f.classificacao.codigo}>
              <button
                type="button"
                className={`donut-leg-item${ativo === f.classificacao.codigo ? " ativo" : ""}`}
                onMouseEnter={() => setAtivo(f.classificacao.codigo)}
                onMouseLeave={() => setAtivo(null)}
                onClick={() => onSelecionar(f.classificacao.codigo)}
                title={`Ver colaboradores — ${f.classificacao.rotulo}`}
              >
                <span
                  className="donut-leg-cor"
                  style={{ background: corDaFaixa[f.classificacao.codigo] }}
                  aria-hidden="true"
                />
                <span className="donut-leg-rot">{f.classificacao.rotulo}</span>
                <span className="donut-leg-num">
                  {f.quantidade} · {pct}%
                </span>
              </button>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
