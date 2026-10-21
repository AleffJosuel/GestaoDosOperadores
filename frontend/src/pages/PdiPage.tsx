import { useMemo, useState, type FormEvent } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { api, ApiError } from "../api/client";
import { useRequisicao } from "../hooks/useRequisicao";
import { AvisoBox, Carregando, ErroBox, Vazio } from "../components/estados";
import { Modal } from "../components/Modal";
import { useToast } from "../components/Toasts";
import { Icone } from "../components/Icone";
import type { AcaoPdi, PdiInput, PdiItem, StatusPdi } from "../api/types";
import { hoje } from "../datas";

const STATUS_LABEL: Record<StatusPdi, string> = {
  NAO_INICIADO: "Não iniciado",
  PLANEJADO: "Planejado",
  EM_ANDAMENTO: "Em andamento",
  CONCLUIDO: "Concluído",
};
const STATUS_ORDEM: StatusPdi[] = ["NAO_INICIADO", "PLANEJADO", "EM_ANDAMENTO", "CONCLUIDO"];
const fmtData = (d: string | null) => (d ? d.split("-").reverse().join("/") : "—");

type Filtro = "ABERTAS" | "ATRASADAS" | "CONCLUIDAS" | "TODAS";
const FILTROS: Filtro[] = ["ABERTAS", "ATRASADAS", "CONCLUIDAS", "TODAS"];

const aberta = (a: PdiItem) => a.status !== "CONCLUIDO";
const atrasada = (a: PdiItem) => aberta(a) && !!a.prazo && a.prazo < hoje();

/** Atrasadas primeiro; depois por prazo crescente (sem prazo por último). */
function ordenar(itens: PdiItem[]): PdiItem[] {
  return [...itens].sort((a, b) => {
    const atrA = atrasada(a) ? 0 : 1;
    const atrB = atrasada(b) ? 0 : 1;
    if (atrA !== atrB) return atrA - atrB;
    if (a.prazo && b.prazo) return a.prazo < b.prazo ? -1 : a.prazo > b.prazo ? 1 : 0;
    if (a.prazo) return -1;
    if (b.prazo) return 1;
    return 0;
  });
}

export function PdiPage() {
  const toast = useToast();
  const todos = useRequisicao(() => api.listarTodosPdi(), []);
  // Só ativos: desativado saiu da equipe e não entra na visão de equipe nem no filtro.
  const colaboradores = useRequisicao(() => api.listarColaboradores(false), []);
  // Competências, ao contrário, vêm todas: uma desativada ainda precisa ter o nome
  // exibido nas ações antigas que apontam para ela.
  const competencias = useRequisicao(() => api.listarCompetencias(true), []);
  const [colaboradorId, setColaboradorId] = useState<number | null>(null);
  // Filtro na URL (?filtro=ATRASADAS): o dashboard linka direto e o "voltar" do navegador o preserva.
  const [params, setParams] = useSearchParams();
  const doParam = params.get("filtro") as Filtro | null;
  const filtro: Filtro = doParam && FILTROS.includes(doParam) ? doParam : "ABERTAS";
  function setFiltro(f: Filtro) {
    const p = new URLSearchParams(params);
    p.set("filtro", f);
    setParams(p, { replace: true });
  }
  const [criando, setCriando] = useState(false);
  const [edicao, setEdicao] = useState<PdiItem | null>(null);

  const mapaCompetencias = useMemo(() => {
    const m = new Map<number, string>();
    (competencias.dados ?? []).forEach((c) => m.set(c.id, c.nome));
    return m;
  }, [competencias.dados]);

  // Base = tudo do colaborador filtrado (ou toda a equipe). Os contadores saem daqui.
  const base = useMemo(() => {
    const lista = todos.dados ?? [];
    return colaboradorId ? lista.filter((a) => a.colaboradorId === colaboradorId) : lista;
  }, [todos.dados, colaboradorId]);

  const contagem = useMemo(
    () => ({
      abertas: base.filter(aberta).length,
      atrasadas: base.filter(atrasada).length,
      concluidas: base.filter((a) => a.status === "CONCLUIDO").length,
    }),
    [base]
  );

  const visiveis = useMemo(() => {
    const filtrada = base.filter((a) => {
      if (filtro === "ABERTAS") return aberta(a);
      if (filtro === "ATRASADAS") return atrasada(a);
      if (filtro === "CONCLUIDAS") return a.status === "CONCLUIDO";
      return true; // TODAS
    });
    return ordenar(filtrada);
  }, [base, filtro]);

  const chips: { id: Filtro; rotulo: string; valor: number | null }[] = [
    { id: "ABERTAS", rotulo: "Abertas", valor: contagem.abertas },
    { id: "ATRASADAS", rotulo: "Atrasadas", valor: contagem.atrasadas },
    { id: "CONCLUIDAS", rotulo: "Concluídas", valor: contagem.concluidas },
    { id: "TODAS", rotulo: "Todas", valor: null },
  ];

  const carregando = todos.carregando || competencias.carregando;
  const formColaboradorId = edicao ? edicao.colaboradorId : colaboradorId;

  return (
    <>
      <div className="topo">
        <div>
          <h1>PDI — Plano de Desenvolvimento Individual</h1>
          <p>Ações de desenvolvimento da equipe, ligadas às avaliações.</p>
        </div>
        {colaboradorId ? (
          <button className="btn btn-primario" onClick={() => setCriando(true)}>
            <Icone nome="mais" />
            Nova ação
          </button>
        ) : (
          <span className="pdi-dica-nova">Escolha um colaborador no filtro para criar uma ação.</span>
        )}
      </div>

      <div className="pdi-resumo" role="group" aria-label="Filtrar ações por situação">
        {chips.map((c) => (
          <button
            key={c.id}
            type="button"
            aria-pressed={filtro === c.id}
            className={`pdi-chip ${filtro === c.id ? "pdi-chip-ativo" : ""} ${
              c.id === "ATRASADAS" && c.valor ? "pdi-chip-alerta" : ""
            }`}
            onClick={() => setFiltro(c.id)}
          >
            {c.rotulo}
            {c.valor !== null && <span className="pdi-chip-num">{c.valor}</span>}
          </button>
        ))}
      </div>

      <div className="toolbar">
        <div className="campo" style={{ marginBottom: 0, minWidth: 280 }}>
          <label htmlFor="colab">Colaborador</label>
          {colaboradores.erro ? (
            <ErroBox mensagem={colaboradores.erro} onTentar={colaboradores.recarregar} />
          ) : (
            <select
              id="colab"
              value={colaboradorId ?? ""}
              onChange={(e) => setColaboradorId(e.target.value ? Number(e.target.value) : null)}
            >
              <option value="">Toda a equipe</option>
              {(colaboradores.dados ?? []).map((c) => (
                <option key={c.id} value={c.id}>
                  {c.nome} {c.ativo ? "" : "(inativo)"}
                </option>
              ))}
            </select>
          )}
        </div>
      </div>

      {carregando ? (
        <Carregando />
      ) : todos.erro ? (
        <ErroBox mensagem={todos.erro} onTentar={todos.recarregar} />
      ) : visiveis.length === 0 ? (
        <Vazio
          texto={
            (todos.dados ?? []).length === 0
              ? "Nenhuma ação de PDI cadastrada ainda."
              : "Nenhuma ação corresponde ao filtro."
          }
        />
      ) : (
        <div className="tabela-wrap cartao">
          <table className="tabela">
            <thead>
              <tr>
                <th>Colaborador</th>
                <th>Competência</th>
                <th>Descrição</th>
                <th>Responsável</th>
                <th>Prazo</th>
                <th>Status</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {visiveis.map((a) => {
                const vencido = atrasada(a);
                return (
                  <tr key={a.id} className={vencido ? "linha-alerta" : undefined}>
                    <td style={{ whiteSpace: "normal" }}>
                      <Link className="link-linha" to={`/ficha/${a.colaboradorId}`}>
                        {a.colaboradorNome}
                      </Link>
                    </td>
                    <td style={{ whiteSpace: "normal" }}>
                      {mapaCompetencias.get(a.competenciaId) ?? `#${a.competenciaId}`}
                    </td>
                    <td style={{ whiteSpace: "normal", maxWidth: 280 }}>{a.descricao}</td>
                    <td>{a.responsavel ?? "—"}</td>
                    <td className={vencido ? "prazo-vencido" : undefined}>
                      <span className="prazo">
                        {vencido && <Icone nome="aviso" />}
                        {fmtData(a.prazo)}
                        {vencido && <span className="sr-only"> (atrasada)</span>}
                      </span>
                    </td>
                    <td>
                      <span className={`status status-${a.status}`}>{STATUS_LABEL[a.status]}</span>
                    </td>
                    <td className="col-acoes">
                      <button
                        className="btn btn-sm btn-sutil"
                        onClick={() => setEdicao(a)}
                        aria-label={`Editar ação de ${a.colaboradorNome}: ${a.descricao}`}
                      >
                        <Icone nome="editar" />
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

      {(criando || edicao) && formColaboradorId && (
        <PdiForm
          colaboradorId={formColaboradorId}
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
            todos.recarregar();
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
          <ErroBox mensagem={erro} />
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
          <AvisoBox>
            Este colaborador não tem avaliações. Registre uma avaliação antes de criar o PDI.
          </AvisoBox>
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
