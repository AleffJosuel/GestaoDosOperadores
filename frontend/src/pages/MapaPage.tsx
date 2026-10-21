import { useState } from "react";
import { useSearchParams } from "react-router-dom";
import { api } from "../api/client";
import { useRequisicao } from "../hooks/useRequisicao";
import { Carregando, Clf, ErroBox, Vazio } from "../components/estados";
import { CampoBusca } from "../components/CampoBusca";
import { Icone } from "../components/Icone";

function fmt(media: number | null): string {
  return media == null ? "—" : media.toFixed(2).replace(".", ",");
}

const faixas = [
  { codigo: "CRITICO", rotulo: "Crítico", cor: "var(--cor-critico-suave)" },
  { codigo: "A_DESENVOLVER", rotulo: "A desenvolver", cor: "var(--cor-desenvolver-suave)" },
  { codigo: "ADEQUADO", rotulo: "Adequado", cor: "var(--cor-adequado-suave)" },
  { codigo: "BOM", rotulo: "Bom", cor: "var(--cor-bom-suave)" },
];

export function MapaPage() {
  const { dados, carregando, erro, recarregar } = useRequisicao(async () => {
    const [mapa, config] = await Promise.all([api.mapa(), api.obterConfiguracao()]);
    return { mapa, config };
  });
  const [busca, setBusca] = useState("");
  const [params, setParams] = useSearchParams();
  const faixaFiltro = params.get("faixa");

  if (carregando) return <Carregando />;
  if (erro) return <ErroBox mensagem={erro} onTentar={recarregar} />;
  if (!dados) return null;

  const { mapa, config } = dados;
  const rotuloPorNota = new Map(config.rotulos.map((r) => [r.nota, r.rotulo]));
  const semDados = mapa.colaboradores.length === 0 || mapa.competencias.length === 0;
  const termo = busca.trim().toLowerCase();
  const faixaRotulo = faixas.find((f) => f.codigo === faixaFiltro)?.rotulo ?? faixaFiltro;

  function limparFaixa() {
    const p = new URLSearchParams(params);
    p.delete("faixa");
    setParams(p, { replace: true });
  }

  const linhas = mapa.colaboradores.filter((l) => {
    if (faixaFiltro && l.classificacao?.codigo !== faixaFiltro) return false;
    if (termo && ![l.nome, l.cargo, l.setor].some((v) => v.toLowerCase().includes(termo)))
      return false;
    return true;
  });

  return (
    <>
      <div className="topo">
        <div>
          <h1>Mapa de competências</h1>
          <p>Nota mais recente de cada colaborador em cada competência.</p>
        </div>
      </div>

      {semDados ? (
        <Vazio texto="Cadastre colaboradores e competências e registre avaliações para ver o mapa." />
      ) : (
        <>
          <div className="toolbar">
            <CampoBusca
              valor={busca}
              onMudar={setBusca}
              rotulo="Buscar colaborador no mapa"
              placeholder="Nome, cargo ou setor…"
            />
            {faixaFiltro && (
              <button className="chip-filtro" onClick={limparFaixa} type="button">
                Faixa: {faixaRotulo}
                <Icone nome="fechar" />
                <span className="sr-only">remover filtro</span>
              </button>
            )}
          </div>
          {linhas.length === 0 ? (
            <Vazio
              texto={
                faixaFiltro && !termo
                  ? `Nenhum colaborador na faixa ${faixaRotulo}.`
                  : "Nenhum colaborador corresponde à busca."
              }
            />
          ) : (
          <>
          <div className="mapa-wrap cartao">
            <table className="mapa">
              <thead>
                <tr>
                  <th className="col-nome">Colaborador</th>
                  {mapa.competencias.map((c) => (
                    <th key={c.id} title={`Nível esperado: ${c.nivelEsperado}`}>
                      {c.nome}
                    </th>
                  ))}
                  <th>Média</th>
                </tr>
              </thead>
              <tbody>
                {linhas.map((linha) => (
                  <tr key={linha.colaboradorId}>
                    <td className="col-nome">
                      <div>{linha.nome}</div>
                      <div style={{ fontSize: "0.8rem", color: "var(--cor-texto-suave)" }}>
                        {linha.cargo} · {linha.setor}
                      </div>
                    </td>
                    {linha.celulas.map((cel) => {
                      const classe = cel.classificacao
                        ? `cel-nota cel-${cel.classificacao.codigo}`
                        : "cel-nota cel-vazia";
                      const dica =
                        cel.nota != null
                          ? `Nota ${cel.nota} — ${rotuloPorNota.get(cel.nota) ?? ""}`.trim()
                          : "Sem avaliação";
                      return (
                        <td key={cel.competenciaId} className={classe} title={dica}>
                          {cel.nota ?? "—"}
                        </td>
                      );
                    })}
                    <td>
                      <strong>{fmt(linha.media)}</strong>
                      <div>
                        <Clf c={linha.classificacao} />
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="legenda" aria-hidden="true">
            {faixas.map((f) => (
              <span key={f.codigo}>
                <span className="amostra" style={{ background: f.cor }} />
                {f.rotulo}
              </span>
            ))}
            <span>
              <span className="amostra" style={{ background: "var(--cor-neutra-suave)" }} />
              Sem avaliação
            </span>
          </div>
          </>
          )}
        </>
      )}
    </>
  );
}
