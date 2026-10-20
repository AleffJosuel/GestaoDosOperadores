import { useNavigate } from "react-router-dom";
import { api } from "../api/client";
import { useRequisicao } from "../hooks/useRequisicao";
import { Carregando, ErroBox } from "../components/estados";
import { DonutFaixas } from "../components/DonutFaixas";

function fmt(media: number | null): string {
  return media == null ? "—" : media.toFixed(2).replace(".", ",");
}

export function DashboardPage() {
  const { dados, carregando, erro, recarregar } = useRequisicao(() => api.dashboard());
  const navegar = useNavigate();

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
            <DonutFaixas
              distribuicao={dados.distribuicao}
              onSelecionar={(codigo) => navegar(`/mapa?faixa=${codigo}`)}
            />
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
