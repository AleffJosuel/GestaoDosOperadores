import { useMemo, useRef, useState, type FormEvent } from "react";
import { api, ApiError } from "../api/client";
import { useRequisicao } from "../hooks/useRequisicao";
import { Carregando, ErroBox, Vazio } from "../components/estados";
import { Modal } from "../components/Modal";
import { ImportModal } from "../components/ImportModal";
import { useToast } from "../components/Toasts";
import { useConfirm } from "../components/Confirm";
import type { Competencia, CompetenciaInput, TipoCompetencia } from "../api/types";
import { Icone } from "../components/Icone";
import { CampoBusca } from "../components/CampoBusca";
import { ariaErro, ErroCampo, useFocoNoPrimeiroErro, type ErrosCampo } from "../components/campos";
import { ThOrdenavel, useOrdenacao } from "../components/Ordenacao";

const TIPO_LABEL: Record<TipoCompetencia, string> = {
  COMPORTAMENTAL: "Comportamental",
  TECNICA: "Técnica",
};

type ColunaOrdem = "nome" | "tipo" | "nivel" | "situacao";
const COLUNAS: [ColunaOrdem, string, string?][] = [
  ["nome", "Nome"],
  ["tipo", "Tipo"],
  ["nivel", "Nível esperado", "col-num"],
  ["situacao", "Situação"],
];
const VALOR_COLUNA: Record<ColunaOrdem, (c: Competencia) => string | number> = {
  nome: (c) => c.nome,
  tipo: (c) => TIPO_LABEL[c.tipo],
  nivel: (c) => c.nivelEsperado,
  situacao: (c) => (c.ativa ? "Ativa" : "Inativa"),
};

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
  const ordem = useOrdenacao(filtradas, VALOR_COLUNA, "nome");

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
            <Icone nome="enviar" />
            Importar CSV
          </button>
          <button className="btn btn-primario" onClick={() => setCriando(true)}>
            <Icone nome="mais" />
            Nova competência
          </button>
        </div>
      </div>

      <div className="toolbar">
        <CampoBusca
          valor={busca}
          onMudar={setBusca}
          rotulo="Buscar competência"
          placeholder="Nome da competência…"
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
        <Vazio
          texto={
            incluirInativas
              ? "Nenhuma competência cadastrada ainda."
              : "Nenhuma competência ativa. Cadastre uma ou marque “Mostrar inativas”."
          }
          acao={
            <button className="btn btn-primario" onClick={() => setCriando(true)}>
              <Icone nome="mais" />
              Nova competência
            </button>
          }
        />
      ) : filtradas.length === 0 ? (
        <Vazio
          texto={`Nenhuma competência corresponde a “${busca.trim()}”.`}
          acao={
            <button className="btn btn-sutil" onClick={() => setBusca("")}>
              Limpar busca
            </button>
          }
        />
      ) : (
        <>
          <p className="contagem" aria-live="polite">
            {filtradas.length === lista.dados.length
              ? `${lista.dados.length} competência(s)`
              : `${filtradas.length} de ${lista.dados.length} competência(s)`}
          </p>
          <div className="tabela-wrap cartao">
            <table className="tabela">
              <thead>
                <tr>
                  {COLUNAS.map(([chave, rotulo, classe]) => (
                    <ThOrdenavel
                      key={chave}
                      rotulo={rotulo}
                      className={classe}
                      ativo={ordem.chave === chave}
                      dir={ordem.dir}
                      onOrdenar={() => ordem.alternar(chave)}
                    />
                  ))}
                  <th className="col-acoes">
                    <span className="sr-only">Ações</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {ordem.ordenados.map((c) => (
                  <tr key={c.id}>
                    <td style={{ whiteSpace: "normal" }}>
                      <strong className="celula-titulo">{c.nome}</strong>
                      {c.descricao && <div className="celula-sub">{c.descricao}</div>}
                    </td>
                    <td>
                      <span className="selo selo-tipo">{TIPO_LABEL[c.tipo]}</span>
                    </td>
                    <td className="col-num">{c.nivelEsperado}</td>
                    <td>
                      <span className={`selo ${c.ativa ? "selo-ativo" : "selo-inativo"}`}>
                        {c.ativa ? "Ativa" : "Inativa"}
                      </span>
                    </td>
                    <td className="col-acoes">
                      <div className="linha-acoes acoes-tabela">
                        <button
                          className="btn btn-sm btn-sutil"
                          onClick={() => setEdicao(c)}
                          aria-label={`Editar ${c.nome}`}
                        >
                          <Icone nome="editar" />
                          Editar
                        </button>
                        <button
                          className={`btn btn-sm ${c.ativa ? "btn-perigo" : "btn-sutil"}`}
                          onClick={() => alterarAtiva(c)}
                          aria-label={`${c.ativa ? "Desativar" : "Reativar"} ${c.nome}`}
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
        </>
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
  const [campos, setCampos] = useState<ErrosCampo>({});
  const formRef = useRef<HTMLFormElement>(null);
  useFocoNoPrimeiroErro(formRef, campos);
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
      <form ref={formRef} onSubmit={aoEnviar}>
        {erro && <ErroBox mensagem={erro} />}
        <p className="form-legenda">
          Campos com <span aria-hidden="true">*</span>
          <span className="sr-only">asterisco</span> são obrigatórios.
        </p>
        <div className="form-grade">
          <div className="campo campo-largo">
            <label htmlFor="nome">
              Nome <span className="obrigatorio" aria-hidden="true">*</span>
            </label>
            <input
              id="nome"
              value={form.nome}
              onChange={(e) => set("nome", e.target.value)}
              required
              maxLength={120}
              {...ariaErro(campos, "nome", "nome")}
            />
            <ErroCampo erros={campos} nome="nome" id="nome" />
          </div>
          <div className="campo">
            <label htmlFor="tipo">
              Tipo <span className="obrigatorio" aria-hidden="true">*</span>
            </label>
            <select
              id="tipo"
              value={form.tipo}
              onChange={(e) => set("tipo", e.target.value as TipoCompetencia)}
              {...ariaErro(campos, "tipo", "tipo")}
            >
              <option value="COMPORTAMENTAL">Comportamental</option>
              <option value="TECNICA">Técnica</option>
            </select>
            <ErroCampo erros={campos} nome="tipo" id="tipo" />
          </div>
          <div className="campo">
            <label htmlFor="nivel">Nível esperado</label>
            <input
              id="nivel"
              type="number"
              min={1}
              max={5}
              value={form.nivelEsperado ?? 5}
              onChange={(e) => set("nivelEsperado", Number(e.target.value))}
              aria-describedby={campos.nivelEsperado ? "nivel-erro" : "nivel-ajuda"}
              aria-invalid={campos.nivelEsperado ? true : undefined}
            />
            <span id="nivel-ajuda" className="ajuda-campo">
              De 1 a 5: a nota que se espera nesta competência.
            </span>
            <ErroCampo erros={campos} nome="nivelEsperado" id="nivel" />
          </div>
          <div className="campo campo-largo">
            <label htmlFor="desc">Descrição</label>
            <textarea
              id="desc"
              rows={2}
              value={form.descricao ?? ""}
              onChange={(e) => set("descricao", e.target.value)}
              {...ariaErro(campos, "descricao", "desc")}
            />
            <ErroCampo erros={campos} nome="descricao" id="desc" />
          </div>
          <div className="campo campo-largo">
            <label htmlFor="acao">Ação padrão de desenvolvimento</label>
            <input
              id="acao"
              value={form.acaoPadrao ?? ""}
              onChange={(e) => set("acaoPadrao", e.target.value)}
              maxLength={200}
              {...ariaErro(campos, "acaoPadrao", "acao")}
            />
            <ErroCampo erros={campos} nome="acaoPadrao" id="acao" />
          </div>
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
