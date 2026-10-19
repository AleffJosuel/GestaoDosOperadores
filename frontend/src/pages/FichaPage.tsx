import { useMemo } from "react";
import { Link, useParams } from "react-router-dom";
import { api } from "../api/client";
import { useRequisicao } from "../hooks/useRequisicao";
import { Carregando, ErroBox } from "../components/estados";
import type { StatusPdi } from "../api/types";

const STATUS_LABEL: Record<StatusPdi, string> = {
  NAO_INICIADO: "Não iniciado",
  PLANEJADO: "Planejado",
  EM_ANDAMENTO: "Em andamento",
  CONCLUIDO: "Concluído",
};
const fmtData = (d: string | null) => (d ? d.split("-").reverse().join("/") : "—");

export function FichaPage() {
  const params = useParams();
  const id = Number(params.colaboradorId);

  const dados = useRequisicao(async () => {
    const [colaborador, avaliacoes, pdi, competencias] = await Promise.all([
      api.buscarColaborador(id),
      api.listarAvaliacoes(id),
      api.listarPdi(id),
      api.listarCompetencias(true),
    ]);
    return { colaborador, avaliacoes, pdi, competencias };
  }, [id]);

  const nomeCompetencia = useMemo(() => {
    const m = new Map<number, string>();
    (dados.dados?.competencias ?? []).forEach((c) => m.set(c.id, c.nome));
    return (cid: number) => m.get(cid) ?? `#${cid}`;
  }, [dados.dados]);

  if (dados.carregando) return <Carregando />;
  if (dados.erro) return <ErroBox mensagem={dados.erro} onTentar={dados.recarregar} />;
  if (!dados.dados) return null;

  const { colaborador: c, avaliacoes, pdi } = dados.dados;

  return (
    <>
      <div className="topo">
        <div>
          <h1>Ficha — {c.nome}</h1>
          <p>Dados, histórico de avaliações e plano de desenvolvimento.</p>
        </div>
        <div className="linha-acoes no-print">
          <Link className="btn btn-sutil" to="/colaboradores">
            Voltar
          </Link>
          <button className="btn btn-primario" onClick={() => window.print()}>
            Imprimir / PDF
          </button>
        </div>
      </div>

      <section className="cartao ficha-secao">
        <h2>Dados do colaborador</h2>
        <div className="ficha-dados">
          <div><span>Matrícula</span>{c.matricula}</div>
          <div><span>Nome</span>{c.nome}</div>
          <div><span>Cargo</span>{c.cargo}</div>
          <div><span>Setor</span>{c.setor}</div>
          <div><span>Admissão</span>{fmtData(c.dataAdmissao)}</div>
          <div><span>Escolaridade</span>{c.escolaridade ?? "—"}</div>
          <div><span>Situação</span>{c.ativo ? "Ativo" : "Inativo"}</div>
        </div>
      </section>

      <section className="cartao ficha-secao">
        <h2>Avaliações ({avaliacoes.length})</h2>
        {avaliacoes.length === 0 ? (
          <p style={{ color: "var(--cor-texto-suave)" }}>Nenhuma avaliação registrada.</p>
        ) : (
          avaliacoes.map((a) => (
            <div key={a.id} style={{ marginBottom: 14 }}>
              <strong>{fmtData(a.dataAvaliacao)}</strong>
              {a.observacao ? ` — ${a.observacao}` : ""}
              <div className="tabela-wrap">
                <table className="tabela" style={{ marginTop: 6 }}>
                  <thead>
                    <tr>
                      <th>Competência</th>
                      <th>Nota</th>
                    </tr>
                  </thead>
                  <tbody>
                    {a.notas.map((n) => (
                      <tr key={n.competenciaId}>
                        <td style={{ whiteSpace: "normal" }}>{nomeCompetencia(n.competenciaId)}</td>
                        <td>{n.nota}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          ))
        )}
      </section>

      <section className="cartao ficha-secao">
        <h2>PDI — ações de desenvolvimento ({pdi.length})</h2>
        {pdi.length === 0 ? (
          <p style={{ color: "var(--cor-texto-suave)" }}>Nenhuma ação de PDI.</p>
        ) : (
          <div className="tabela-wrap">
            <table className="tabela">
              <thead>
                <tr>
                  <th>Competência</th>
                  <th>Descrição</th>
                  <th>Responsável</th>
                  <th>Prazo</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {pdi.map((a) => (
                  <tr key={a.id}>
                    <td style={{ whiteSpace: "normal" }}>{nomeCompetencia(a.competenciaId)}</td>
                    <td style={{ whiteSpace: "normal" }}>{a.descricao}</td>
                    <td>{a.responsavel ?? "—"}</td>
                    <td>{fmtData(a.prazo)}</td>
                    <td>{STATUS_LABEL[a.status]}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </>
  );
}
