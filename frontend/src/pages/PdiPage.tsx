import { useMemo, useState, type FormEvent } from "react";
import { api, ApiError } from "../api/client";
import { useRequisicao } from "../hooks/useRequisicao";
import { Carregando, ErroBox, Vazio } from "../components/estados";
import { Modal } from "../components/Modal";
import { useToast } from "../components/Toasts";
import type { AcaoPdi, PdiInput, StatusPdi } from "../api/types";

const STATUS_LABEL: Record<StatusPdi, string> = {
  NAO_INICIADO: "Não iniciado",
  PLANEJADO: "Planejado",
  EM_ANDAMENTO: "Em andamento",
  CONCLUIDO: "Concluído",
};
const STATUS_ORDEM: StatusPdi[] = ["NAO_INICIADO", "PLANEJADO", "EM_ANDAMENTO", "CONCLUIDO"];
const hoje = () => new Date().toISOString().slice(0, 10);
const fmtData = (d: string | null) => (d ? d.split("-").reverse().join("/") : "—");

export function PdiPage() {
  const toast = useToast();
  const colaboradores = useRequisicao(() => api.listarColaboradores(true), []);
  const competencias = useRequisicao(() => api.listarCompetencias(true), []);
  const [colaboradorId, setColaboradorId] = useState<number | null>(null);
  const [criando, setCriando] = useState(false);
  const [edicao, setEdicao] = useState<AcaoPdi | null>(null);

  const mapaCompetencias = useMemo(() => {
    const m = new Map<number, string>();
    (competencias.dados ?? []).forEach((c) => m.set(c.id, c.nome));
    return m;
  }, [competencias.dados]);

  const lista = useRequisicao(
    () => (colaboradorId ? api.listarPdi(colaboradorId) : Promise.resolve([])),
    [colaboradorId]
  );

  return (
    <>
      <div className="topo">
        <div>
          <h1>PDI — Plano de Desenvolvimento Individual</h1>
          <p>Ações de desenvolvimento por colaborador, ligadas às avaliações.</p>
        </div>
        {colaboradorId && (
          <button className="btn btn-primario" onClick={() => setCriando(true)}>
            Nova ação
          </button>
        )}
      </div>

      <div className="toolbar">
        <div className="campo" style={{ marginBottom: 0, minWidth: 280 }}>
          <label htmlFor="colab">Colaborador</label>
          {colaboradores.carregando ? (
            <Carregando texto="Carregando…" />
          ) : colaboradores.erro ? (
            <ErroBox mensagem={colaboradores.erro} onTentar={colaboradores.recarregar} />
          ) : (
            <select
              id="colab"
              value={colaboradorId ?? ""}
              onChange={(e) => setColaboradorId(e.target.value ? Number(e.target.value) : null)}
            >
              <option value="">Selecione um colaborador…</option>
              {(colaboradores.dados ?? []).map((c) => (
                <option key={c.id} value={c.id}>
                  {c.nome} {c.ativo ? "" : "(inativo)"}
                </option>
              ))}
            </select>
          )}
        </div>
      </div>

      {!colaboradorId ? (
        <Vazio texto="Escolha um colaborador para ver e criar ações de PDI." />
      ) : lista.carregando ? (
        <Carregando />
      ) : lista.erro ? (
        <ErroBox mensagem={lista.erro} onTentar={lista.recarregar} />
      ) : !lista.dados || lista.dados.length === 0 ? (
        <Vazio texto="Nenhuma ação de PDI para este colaborador ainda." />
      ) : (
        <div className="tabela-wrap cartao">
          <table className="tabela">
            <thead>
              <tr>
                <th>Competência</th>
                <th>Descrição</th>
                <th>Responsável</th>
                <th>Prazo</th>
                <th>Status</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {lista.dados.map((a) => {
                const vencido =
                  a.prazo && a.prazo < hoje() && a.status !== "CONCLUIDO";
                return (
                  <tr key={a.id}>
                    <td style={{ whiteSpace: "normal" }}>
                      {mapaCompetencias.get(a.competenciaId) ?? `#${a.competenciaId}`}
                    </td>
                    <td style={{ whiteSpace: "normal", maxWidth: 320 }}>{a.descricao}</td>
                    <td>{a.responsavel ?? "—"}</td>
                    <td className={vencido ? "prazo-vencido" : undefined}>
                      {fmtData(a.prazo)}
                      {vencido ? " ⚠" : ""}
                    </td>
                    <td>
                      <span className={`status status-${a.status}`}>{STATUS_LABEL[a.status]}</span>
                    </td>
                    <td>
                      <button className="btn btn-sm btn-sutil" onClick={() => setEdicao(a)}>
                        Editar
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {(criando || edicao) && colaboradorId && (
        <PdiForm
          colaboradorId={colaboradorId}
          acao={edicao}
          mapaCompetencias={mapaCompetencias}
          onFechar={() => {
            setCriando(false);
            setEdicao(null);
          }}
          onSalvo={() => {
            setCriando(false);
            setEdicao(null);
            toast.sucesso("Ação de PDI salva.");
            lista.recarregar();
          }}
        />
      )}
    </>
  );
}

function PdiForm({
  colaboradorId,
  acao,
  mapaCompetencias,
  onFechar,
  onSalvo,
}: {
  colaboradorId: number;
  acao: AcaoPdi | null;
  mapaCompetencias: Map<number, string>;
  onFechar: () => void;
  onSalvo: () => void;
}) {
  const editando = acao !== null;
  const avaliacoes = useRequisicao(() => api.listarAvaliacoes(colaboradorId), [colaboradorId]);

  const [form, setForm] = useState<PdiInput>({
    competenciaId: acao?.competenciaId ?? null,
    avaliacaoOrigemId: acao?.avaliacaoOrigemId ?? null,
    descricao: acao?.descricao ?? "",
    responsavel: acao?.responsavel ?? "",
    funcaoArea: acao?.funcaoArea ?? "",
    cargaHorariaMinutos: acao?.cargaHorariaMinutos ?? null,
    prazo: acao?.prazo ?? "",
    status: acao?.status ?? "NAO_INICIADO",
    dataConclusao: acao?.dataConclusao ?? "",
    evidencia: acao?.evidencia ?? "",
    observacao: acao?.observacao ?? "",
  });
  const [erro, setErro] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  function set<K extends keyof PdiInput>(chave: K, valor: PdiInput[K]) {
    setForm((f) => ({ ...f, [chave]: valor }));
  }

  // Na criação, as competências disponíveis são as pontuadas na avaliação escolhida.
  const avaliacaoSel = (avaliacoes.dados ?? []).find((a) => a.id === form.avaliacaoOrigemId);
  const competenciaOpcoes = avaliacaoSel
    ? avaliacaoSel.notas.map((n) => ({
        id: n.competenciaId,
        nome: mapaCompetencias.get(n.competenciaId) ?? `#${n.competenciaId}`,
        nota: n.nota,
      }))
    : [];

  function escolherAvaliacao(id: number | null) {
    setForm((f) => ({ ...f, avaliacaoOrigemId: id, competenciaId: null }));
  }

  async function aoEnviar(e: FormEvent) {
    e.preventDefault();
    setErro(null);
    const payload: PdiInput = {
      ...form,
      responsavel: form.responsavel || null,
      funcaoArea: form.funcaoArea || null,
      prazo: form.prazo || null,
      cargaHorariaMinutos: form.cargaHorariaMinutos || null,
      dataConclusao: form.status === "CONCLUIDO" ? form.dataConclusao || hoje() : null,
      evidencia: form.evidencia || null,
      observacao: form.observacao || null,
    };
    setEnviando(true);
    try {
      if (editando && acao) await api.atualizarPdi(acao.id, payload);
      else await api.criarPdi(colaboradorId, payload);
      onSalvo();
    } catch (err) {
      setErro(err instanceof ApiError ? err.message : "Erro ao salvar a ação.");
    } finally {
      setEnviando(false);
    }
  }

  return (
    <Modal titulo={editando ? "Editar ação de PDI" : "Nova ação de PDI"} onFechar={onFechar}>
      <form onSubmit={aoEnviar}>
        {erro && (
          <div className="estado-erro" role="alert">
            {erro}
          </div>
        )}

        {editando ? (
          <div className="campo">
            <label>Competência (da avaliação de origem)</label>
            <input
              value={mapaCompetencias.get(acao!.competenciaId) ?? `#${acao!.competenciaId}`}
              disabled
            />
          </div>
        ) : avaliacoes.carregando ? (
          <Carregando texto="Carregando avaliações…" />
        ) : avaliacoes.erro ? (
          <ErroBox mensagem={avaliacoes.erro} onTentar={avaliacoes.recarregar} />
        ) : (avaliacoes.dados ?? []).length === 0 ? (
          <div className="estado-erro" role="alert">
            Este colaborador não tem avaliações. Registre uma avaliação antes de criar o PDI.
          </div>
        ) : (
          <>
            <div className="campo">
              <label htmlFor="aval">Avaliação de origem</label>
              <select
                id="aval"
                value={form.avaliacaoOrigemId ?? ""}
                onChange={(e) => escolherAvaliacao(e.target.value ? Number(e.target.value) : null)}
                required
              >
                <option value="">Selecione a avaliação…</option>
                {(avaliacoes.dados ?? []).map((a) => (
                  <option key={a.id} value={a.id}>
                    {fmtData(a.dataAvaliacao)} ({a.notas.length} competências)
                  </option>
                ))}
              </select>
            </div>
            <div className="campo">
              <label htmlFor="comp">Competência a desenvolver</label>
              <select
                id="comp"
                value={form.competenciaId ?? ""}
                onChange={(e) => set("competenciaId", e.target.value ? Number(e.target.value) : null)}
                required
                disabled={!form.avaliacaoOrigemId}
              >
                <option value="">
                  {form.avaliacaoOrigemId ? "Selecione a competência…" : "Escolha a avaliação primeiro"}
                </option>
                {competenciaOpcoes.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.nome} — nota {c.nota}
                  </option>
                ))}
              </select>
            </div>
          </>
        )}

        <div className="campo">
          <label htmlFor="desc">Descrição da ação</label>
          <textarea
            id="desc"
            rows={2}
            value={form.descricao}
            onChange={(e) => set("descricao", e.target.value)}
            required
          />
        </div>
        <div className="campo">
          <label htmlFor="resp">Responsável</label>
          <input
            id="resp"
            value={form.responsavel ?? ""}
            onChange={(e) => set("responsavel", e.target.value)}
          />
        </div>
        <div className="campo">
          <label htmlFor="fa">Função / área</label>
          <input
            id="fa"
            value={form.funcaoArea ?? ""}
            onChange={(e) => set("funcaoArea", e.target.value)}
          />
        </div>
        <div className="campo">
          <label htmlFor="ch">Carga horária (minutos)</label>
          <input
            id="ch"
            type="number"
            min={1}
            value={form.cargaHorariaMinutos ?? ""}
            onChange={(e) =>
              set("cargaHorariaMinutos", e.target.value ? Number(e.target.value) : null)
            }
          />
        </div>
        <div className="campo">
          <label htmlFor="prazo">Prazo</label>
          <input
            id="prazo"
            type="date"
            value={form.prazo ?? ""}
            onChange={(e) => set("prazo", e.target.value)}
          />
        </div>
        <div className="campo">
          <label htmlFor="status">Status</label>
          <select
            id="status"
            value={form.status ?? "NAO_INICIADO"}
            onChange={(e) => set("status", e.target.value as StatusPdi)}
          >
            {STATUS_ORDEM.map((s) => (
              <option key={s} value={s}>
                {STATUS_LABEL[s]}
              </option>
            ))}
          </select>
        </div>
        {form.status === "CONCLUIDO" && (
          <>
            <div className="campo">
              <label htmlFor="dc">Data de conclusão</label>
              <input
                id="dc"
                type="date"
                max={hoje()}
                value={form.dataConclusao ?? ""}
                onChange={(e) => set("dataConclusao", e.target.value)}
              />
            </div>
            <div className="campo">
              <label htmlFor="ev">Evidência</label>
              <textarea
                id="ev"
                rows={2}
                value={form.evidencia ?? ""}
                onChange={(e) => set("evidencia", e.target.value)}
              />
            </div>
          </>
        )}
        <div className="campo">
          <label htmlFor="obs">Observação</label>
          <textarea
            id="obs"
            rows={2}
            value={form.observacao ?? ""}
            onChange={(e) => set("observacao", e.target.value)}
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
