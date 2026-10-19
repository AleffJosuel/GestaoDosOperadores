import { useMemo, useState, type FormEvent } from "react";
import { Link, useParams } from "react-router-dom";
import { api, ApiError } from "../api/client";
import { useRequisicao } from "../hooks/useRequisicao";
import { Carregando, ErroBox } from "../components/estados";
import { Modal } from "../components/Modal";
import { useToast } from "../components/Toasts";
import type { RegistroInput, StatusPdi, TipoRegistro } from "../api/types";

const STATUS_LABEL: Record<StatusPdi, string> = {
  NAO_INICIADO: "Não iniciado",
  PLANEJADO: "Planejado",
  EM_ANDAMENTO: "Em andamento",
  CONCLUIDO: "Concluído",
};
const TIPO_REGISTRO_LABEL: Record<TipoRegistro, string> = {
  FEEDBACK: "Feedback",
  CONVERSA: "Conversa",
  OCORRENCIA: "Ocorrência",
  OUTRO: "Outro",
};
const fmtData = (d: string | null) => (d ? d.split("-").reverse().join("/") : "—");

export function FichaPage() {
  const params = useParams();
  const id = Number(params.colaboradorId);
  const toast = useToast();
  const [registrando, setRegistrando] = useState(false);

  const dados = useRequisicao(async () => {
    const [colaborador, avaliacoes, pdi, competencias, acompanhamentos] = await Promise.all([
      api.buscarColaborador(id),
      api.listarAvaliacoes(id),
      api.listarPdi(id),
      api.listarCompetencias(true),
      api.listarAcompanhamentos(id),
    ]);
    return { colaborador, avaliacoes, pdi, competencias, acompanhamentos };
  }, [id]);

  const nomeCompetencia = useMemo(() => {
    const m = new Map<number, string>();
    (dados.dados?.competencias ?? []).forEach((c) => m.set(c.id, c.nome));
    return (cid: number) => m.get(cid) ?? `#${cid}`;
  }, [dados.dados]);

  if (dados.carregando) return <Carregando />;
  if (dados.erro) return <ErroBox mensagem={dados.erro} onTentar={dados.recarregar} />;
  if (!dados.dados) return null;

  const { colaborador: c, avaliacoes, pdi, acompanhamentos } = dados.dados;

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

      <section className="cartao ficha-secao">
        <div className="ficha-secao-topo">
          <h2>Acompanhamentos ({acompanhamentos.length})</h2>
          <button className="btn btn-sm btn-sutil no-print" onClick={() => setRegistrando(true)}>
            Novo registro
          </button>
        </div>
        {acompanhamentos.length === 0 ? (
          <p style={{ color: "var(--cor-texto-suave)" }}>Nenhum registro de acompanhamento.</p>
        ) : (
          <div className="tabela-wrap">
            <table className="tabela">
              <thead>
                <tr>
                  <th>Data</th>
                  <th>Tipo</th>
                  <th>Descrição</th>
                </tr>
              </thead>
              <tbody>
                {acompanhamentos.map((r) => (
                  <tr key={r.id}>
                    <td>{fmtData(r.dataEvento)}</td>
                    <td>{TIPO_REGISTRO_LABEL[r.tipo]}</td>
                    <td style={{ whiteSpace: "normal" }}>{r.descricao}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {registrando && (
        <RegistroForm
          colaboradorId={id}
          onFechar={() => setRegistrando(false)}
          onSalvo={() => {
            setRegistrando(false);
            toast.sucesso("Registro adicionado.");
            dados.recarregar();
          }}
        />
      )}
    </>
  );
}

function RegistroForm({
  colaboradorId,
  onFechar,
  onSalvo,
}: {
  colaboradorId: number;
  onFechar: () => void;
  onSalvo: () => void;
}) {
  const hoje = new Date().toISOString().slice(0, 10);
  const [form, setForm] = useState<RegistroInput>({
    tipo: "FEEDBACK",
    dataEvento: hoje,
    descricao: "",
  });
  const [erro, setErro] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  async function aoEnviar(e: FormEvent) {
    e.preventDefault();
    setErro(null);
    setEnviando(true);
    try {
      await api.criarAcompanhamento(colaboradorId, form);
      onSalvo();
    } catch (err) {
      setErro(err instanceof ApiError ? err.message : "Erro ao salvar o registro.");
    } finally {
      setEnviando(false);
    }
  }

  return (
    <Modal titulo="Novo registro de acompanhamento" onFechar={onFechar}>
      <form onSubmit={aoEnviar}>
        {erro && (
          <div className="estado-erro" role="alert">
            {erro}
          </div>
        )}
        <div className="campo">
          <label htmlFor="tipo-reg">Tipo</label>
          <select
            id="tipo-reg"
            value={form.tipo}
            onChange={(e) => setForm((f) => ({ ...f, tipo: e.target.value as TipoRegistro }))}
          >
            {Object.entries(TIPO_REGISTRO_LABEL).map(([valor, rotulo]) => (
              <option key={valor} value={valor}>
                {rotulo}
              </option>
            ))}
          </select>
        </div>
        <div className="campo">
          <label htmlFor="data-reg">Data</label>
          <input
            id="data-reg"
            type="date"
            max={hoje}
            value={form.dataEvento}
            onChange={(e) => setForm((f) => ({ ...f, dataEvento: e.target.value }))}
            required
          />
        </div>
        <div className="campo">
          <label htmlFor="desc-reg">Descrição</label>
          <textarea
            id="desc-reg"
            rows={4}
            value={form.descricao}
            onChange={(e) => setForm((f) => ({ ...f, descricao: e.target.value }))}
            required
          />
        </div>
        <div className="modal-acoes">
          <button type="button" className="btn btn-sutil" onClick={onFechar}>
            Cancelar
          </button>
          <button type="submit" className="btn btn-primario" disabled={enviando}>
            {enviando ? "Salvando…" : "Salvar"}
          </button>
        </div>
      </form>
    </Modal>
  );
}
