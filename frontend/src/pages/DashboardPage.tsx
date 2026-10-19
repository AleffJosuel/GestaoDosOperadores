import { api } from "../api/client";
import { useRequisicao } from "../hooks/useRequisicao";
import { Carregando, Clf, ErroBox } from "../components/estados";
import type { FaixaContagem } from "../api/types";

const corDaFaixa: Record<string, string> = {
  CRITICO: "var(--cor-critico)",
  A_DESENVOLVER: "var(--cor-desenvolver)",
  ADEQUADO: "var(--cor-adequado)",
  BOM: "var(--cor-bom)",
};

function fmt(media: number | null): string {
  return media == null ? "—" : media.toFixed(2).replace(".", ",");
}

export function DashboardPage() {
  const { dados, carregando, erro, recarregar } = useRequisicao(() => api.dashboard());

  if (carregando) return <Carregando />;
  if (erro) return <ErroBox mensagem={erro} onTentar={recarregar} />;
  if (!dados) return null;

  const totalFaixas = dados.distribuicao.reduce((s, f) => s + f.quantidade, 0);

  return (
    <>
      <div className="topo">
        <div>
          <h1>Dashboard</h1>
          <p>Visão geral da equipe a partir das avaliações mais recentes.</p>
        </div>
      </div>

      <div className="grade-cartoes">
        <Metrica valor={dados.colaboradoresAtivos} rotulo="Colaboradores ativos" />
        <Metrica valor={dados.competenciasAtivas} rotulo="Competências ativas" />
        <Metrica valor={dados.colaboradoresAvaliados} rotulo="Colaboradores avaliados" />
        <Metrica valor={dados.totalAvaliacoes} rotulo="Avaliações registradas" />
        <Metrica valor={fmt(dados.mediaGeral)} rotulo="Média geral (1–5)" />
      </div>

      <div className="painel-duplo">
        <section className="cartao bloco">
          <h2>Distribuição por faixa</h2>
          {totalFaixas === 0 ? (
            <p style={{ color: "var(--cor-texto-suave)" }}>Ainda não há notas registradas.</p>
          ) : (
            dados.distribuicao.map((f: FaixaContagem) => {
              const pct = totalFaixas ? (f.quantidade / totalFaixas) * 100 : 0;
              return (
                <div className="barra-faixa" key={f.classificacao.codigo}>
                  <span style={{ width: 120 }}>
                    <Clf c={f.classificacao} />
                  </span>
                  <span className="trilho">
                    <span
                      className="preenche"
                      style={{ width: `${pct}%`, background: corDaFaixa[f.classificacao.codigo] }}
                    />
                  </span>
                  <span className="num">{f.quantidade}</span>
                </div>
              );
            })
          )}
        </section>

        <section className="cartao bloco">
          <h2>Competências mais críticas</h2>
          {dados.competenciasCriticas.length === 0 ? (
            <p style={{ color: "var(--cor-texto-suave)" }}>Sem dados suficientes.</p>
          ) : (
            <table className="tabela">
              <thead>
                <tr>
                  <th>Competência</th>
                  <th>Média</th>
                </tr>
              </thead>
              <tbody>
                {dados.competenciasCriticas.map((c) => (
                  <tr key={c.id}>
                    <td style={{ whiteSpace: "normal" }}>{c.nome}</td>
                    <td>{fmt(c.media)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>
      </div>

      <h2 style={{ marginTop: 24 }}>Acompanhamento (PDI)</h2>
      <div className="grade-cartoes">
        <Metrica valor={dados.pdiAbertas} rotulo="Ações abertas" />
        <Metrica valor={dados.pdiAtrasadas} rotulo="Ações atrasadas" alerta={dados.pdiAtrasadas > 0} />
        <Metrica valor={dados.pdiConcluidas} rotulo="Ações concluídas" />
      </div>
    </>
  );
}

function Metrica({
  valor,
  rotulo,
  alerta,
}: {
  valor: number | string;
  rotulo: string;
  alerta?: boolean;
}) {
  return (
    <div className="cartao cartao-metrica">
      <div className="valor" style={alerta ? { color: "var(--cor-critico)" } : undefined}>
        {valor}
      </div>
      <div className="rotulo">{rotulo}</div>
    </div>
  );
}
