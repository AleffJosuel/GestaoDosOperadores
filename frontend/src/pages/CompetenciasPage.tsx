import { useMemo, useState, type FormEvent } from "react";
import { api, ApiError } from "../api/client";
import { useRequisicao } from "../hooks/useRequisicao";
import { Carregando, ErroBox, Vazio } from "../components/estados";
import { Modal } from "../components/Modal";
import { ImportModal } from "../components/ImportModal";
import { useToast } from "../components/Toasts";
import { useConfirm } from "../components/Confirm";
import type { Competencia, CompetenciaInput, TipoCompetencia } from "../api/types";

export function CompetenciasPage() {
  const toast = useToast();
  const confirmar = useConfirm();
  const [incluirInativas, setIncluirInativas] = useState(false);
  const [busca, setBusca] = useState("");
  const [edicao, setEdicao] = useState<Competencia | null>(null);
  const [criando, setCriando] = useState(false);
  const [importando, setImportando] = useState(false);

  const lista = useRequisicao(
    () => api.listarCompetencias(incluirInativas),
    [incluirInativas]
  );

  const filtradas = useMemo(() => {
    const termo = busca.trim().toLowerCase();
    if (!termo) return lista.dados ?? [];
    return (lista.dados ?? []).filter((c) => c.nome.toLowerCase().includes(termo));
  }, [lista.dados, busca]);

  async function alterarAtiva(c: Competencia) {
    if (c.ativa) {
      const ok = await confirmar({
        titulo: "Desativar competência",
        mensagem: `Desativar "${c.nome}"? Avaliações antigas mantêm o histórico; você pode reativar depois.`,
        confirmarTexto: "Desativar",
        perigo: true,
      });
      if (!ok) return;
    }
    try {
      if (c.ativa) await api.desativarCompetencia(c.id);
      else await api.reativarCompetencia(c.id);
      toast.sucesso(c.ativa ? "Competência desativada." : "Competência reativada.");
      lista.recarregar();
    } catch (e) {
      toast.erro(e instanceof ApiError ? e.message : "Erro ao alterar a competência.");
    }
  }

  return (
    <>
      <div className="topo">
        <div>
          <h1>Competências</h1>
          <p>Catálogo de competências avaliadas pela equipe.</p>
        </div>
        <div className="linha-acoes">
          <button className="btn btn-sutil" onClick={() => setImportando(true)}>
            Importar CSV
          </button>
          <button className="btn btn-primario" onClick={() => setCriando(true)}>
            Nova competência
          </button>
        </div>
      </div>

      <div className="toolbar">
        <input
          className="busca"
          type="search"
          placeholder="Buscar competência…"
          value={busca}
          onChange={(e) => setBusca(e.target.value)}
        />
        <label className="check-inline">
          <input
            type="checkbox"
            checked={incluirInativas}
            onChange={(e) => setIncluirInativas(e.target.checked)}
          />
          Mostrar inativas
        </label>
      </div>

      {lista.carregando ? (
        <Carregando />
      ) : lista.erro ? (
        <ErroBox mensagem={lista.erro} onTentar={lista.recarregar} />
      ) : !lista.dados || lista.dados.length === 0 ? (
        <Vazio texto="Nenhuma competência cadastrada ainda." />
      ) : filtradas.length === 0 ? (
        <Vazio texto="Nenhuma competência corresponde à busca." />
      ) : (
        <div className="tabela-wrap cartao">
          <table className="tabela">
            <thead>
              <tr>
                <th>Nome</th>
                <th>Tipo</th>
                <th>Nível esperado</th>
                <th>Situação</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {filtradas.map((c) => (
                <tr key={c.id}>
                  <td style={{ whiteSpace: "normal" }}>{c.nome}</td>
                  <td>
                    <span className="selo selo-tipo">
                      {c.tipo === "COMPORTAMENTAL" ? "Comportamental" : "Técnica"}
                    </span>
                  </td>
                  <td>{c.nivelEsperado}</td>
                  <td>
                    <span className={`selo ${c.ativa ? "selo-ativo" : "selo-inativo"}`}>
                      {c.ativa ? "Ativa" : "Inativa"}
                    </span>
                  </td>
                  <td>
                    <div className="linha-acoes">
                      <button className="btn btn-sm btn-sutil" onClick={() => setEdicao(c)}>
                        Editar
                      </button>
                      <button
                        className={`btn btn-sm ${c.ativa ? "btn-perigo" : "btn-sutil"}`}
                        onClick={() => alterarAtiva(c)}
                      >
                        {c.ativa ? "Desativar" : "Reativar"}
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {(criando || edicao) && (
        <CompetenciaForm
          competencia={edicao}
          onFechar={() => {
            setCriando(false);
            setEdicao(null);
          }}
          onSalvo={() => {
            setCriando(false);
            setEdicao(null);
            toast.sucesso("Competência salva.");
            lista.recarregar();
          }}
        />
      )}

      {importando && (
        <ImportModal
          titulo="Importar competências (CSV)"
          modelo={{
            cabecalho: ["nome", "tipo", "descricao", "criterio", "nivelEsperado", "acaoPadrao"],
            exemplo: [
              "Comunicação",
              "COMPORTAMENTAL",
              "Clareza ao se expressar",
              "Transmite ideias com objetividade",
              "4",
              "Treinamento de comunicação",
            ],
            nomeArquivo: "modelo-competencias",
          }}
          enviar={(arquivo) => api.importarCompetencias(arquivo)}
          onFechar={() => setImportando(false)}
          onImportado={() => lista.recarregar()}
        />
      )}
    </>
  );
}

function CompetenciaForm({
  competencia,
  onFechar,
  onSalvo,
}: {
  competencia: Competencia | null;
  onFechar: () => void;
  onSalvo: () => void;
}) {
  const editando = competencia !== null;
  const [form, setForm] = useState<CompetenciaInput>({
    nome: competencia?.nome ?? "",
    tipo: competencia?.tipo ?? "COMPORTAMENTAL",
    nivelEsperado: competencia?.nivelEsperado ?? 5,
    descricao: competencia?.descricao ?? "",
    criterio: competencia?.criterio ?? "",
    acaoPadrao: competencia?.acaoPadrao ?? "",
  });
  const [campos, setCampos] = useState<Record<string, string>>({});
  const [erro, setErro] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  function set<K extends keyof CompetenciaInput>(chave: K, valor: CompetenciaInput[K]) {
    setForm((f) => ({ ...f, [chave]: valor }));
  }

  async function aoEnviar(e: FormEvent) {
    e.preventDefault();
    setErro(null);
    setCampos({});
    setEnviando(true);
    try {
      if (editando && competencia) await api.atualizarCompetencia(competencia.id, form);
      else await api.criarCompetencia(form);
      onSalvo();
    } catch (err) {
      if (err instanceof ApiError) {
        setErro(err.message);
        if (err.campos) setCampos(err.campos);
      } else setErro("Erro ao salvar.");
    } finally {
      setEnviando(false);
    }
  }

  return (
    <Modal titulo={editando ? "Editar competência" : "Nova competência"} onFechar={onFechar}>
      <form onSubmit={aoEnviar}>
        {erro && (
          <div className="estado-erro" role="alert">
            {erro}
          </div>
        )}
        <div className="campo">
          <label htmlFor="nome">Nome</label>
          <input id="nome" value={form.nome} onChange={(e) => set("nome", e.target.value)} required />
          {campos.nome && <span className="erro-campo">{campos.nome}</span>}
        </div>
        <div className="campo">
          <label htmlFor="tipo">Tipo</label>
          <select
            id="tipo"
            value={form.tipo}
            onChange={(e) => set("tipo", e.target.value as TipoCompetencia)}
          >
            <option value="COMPORTAMENTAL">Comportamental</option>
            <option value="TECNICA">Técnica</option>
          </select>
        </div>
        <div className="campo">
          <label htmlFor="nivel">Nível esperado (1–5)</label>
          <input
            id="nivel"
            type="number"
            min={1}
            max={5}
            value={form.nivelEsperado ?? 5}
            onChange={(e) => set("nivelEsperado", Number(e.target.value))}
          />
          {campos.nivelEsperado && <span className="erro-campo">{campos.nivelEsperado}</span>}
        </div>
        <div className="campo">
          <label htmlFor="desc">Descrição</label>
          <textarea
            id="desc"
            rows={2}
            value={form.descricao ?? ""}
            onChange={(e) => set("descricao", e.target.value)}
          />
        </div>
        <div className="campo">
          <label htmlFor="acao">Ação padrão de desenvolvimento</label>
          <input
            id="acao"
            value={form.acaoPadrao ?? ""}
            onChange={(e) => set("acaoPadrao", e.target.value)}
          />
          {campos.acaoPadrao && <span className="erro-campo">{campos.acaoPadrao}</span>}
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
