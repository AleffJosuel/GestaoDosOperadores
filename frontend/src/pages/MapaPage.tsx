import { useState } from "react";
import { api } from "../api/client";
import { useRequisicao } from "../hooks/useRequisicao";
import { Carregando, Clf, ErroBox, Vazio } from "../components/estados";

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
  const { dados, carregando, erro, recarregar } = useRequisicao(() => api.mapa());
  const [busca, setBusca] = useState("");

  if (carregando) return <Carregando />;
  if (erro) return <ErroBox mensagem={erro} onTentar={recarregar} />;
  if (!dados) return null;

  const semDados = dados.colaboradores.length === 0 || dados.competencias.length === 0;
  const termo = busca.trim().toLowerCase();
  const linhas = termo
    ? dados.colaboradores.filter((l) =>
        [l.nome, l.cargo, l.setor].some((v) => v.toLowerCase().includes(termo))
      )
    : dados.colaboradores;

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
            <input
              className="busca"
              type="search"
              placeholder="Buscar colaborador por nome, cargo ou setor…"
              value={busca}
              onChange={(e) => setBusca(e.target.value)}
            />
          </div>
          {linhas.length === 0 ? (
            <Vazio texto="Nenhum colaborador corresponde à busca." />
          ) : (
          <>
          <div className="mapa-wrap cartao">
            <table className="mapa">
              <thead>
                <tr>
                  <th className="col-nome">Colaborador</th>
                  {dados.competencias.map((c) => (
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
                      return (
                        <td
                          key={cel.competenciaId}
                          className={classe}
                          title={cel.classificacao?.rotulo ?? "Sem avaliação"}
                        >
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
