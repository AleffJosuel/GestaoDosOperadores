import { useMemo, useRef, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { api, ApiError } from "../api/client";
import { useRequisicao } from "../hooks/useRequisicao";
import { Carregando, ErroBox, Vazio } from "../components/estados";
import { Modal } from "../components/Modal";
import { ImportModal } from "../components/ImportModal";
import { useToast } from "../components/Toasts";
import { useConfirm } from "../components/Confirm";
import { AvaliacaoModal } from "./AvaliacaoModal";
import type { Colaborador, ColaboradorInput } from "../api/types";
import { hoje } from "../datas";
import { Icone } from "../components/Icone";
import { CampoBusca } from "../components/CampoBusca";
import { ariaErro, ErroCampo, useFocoNoPrimeiroErro, type ErrosCampo } from "../components/campos";
import { ThOrdenavel, useOrdenacao } from "../components/Ordenacao";

type ColunaOrdem = "matricula" | "nome" | "cargo" | "setor" | "situacao";
const COLUNAS: [ColunaOrdem, string][] = [
  ["matricula", "Matrícula"],
  ["nome", "Nome"],
  ["cargo", "Cargo"],
  ["setor", "Setor"],
  ["situacao", "Situação"],
];
const VALOR_COLUNA: Record<ColunaOrdem, (c: Colaborador) => string> = {
  matricula: (c) => c.matricula,
  nome: (c) => c.nome,
  cargo: (c) => c.cargo,
  setor: (c) => c.setor,
  situacao: (c) => (c.ativo ? "Ativo" : "Inativo"),
};

export function ColaboradoresPage() {
  const toast = useToast();
  const confirmar = useConfirm();
  const [incluirInativos, setIncluirInativos] = useState(false);
  const [busca, setBusca] = useState("");
  const [edicao, setEdicao] = useState<Colaborador | null>(null);
  const [criando, setCriando] = useState(false);
  const [avaliando, setAvaliando] = useState<Colaborador | null>(null);
  const [importando, setImportando] = useState(false);

  const lista = useRequisicao(
    () => api.listarColaboradores(incluirInativos),
    [incluirInativos]
  );

  const filtrados = useMemo(() => {
    const termo = busca.trim().toLowerCase();
    if (!termo) return lista.dados ?? [];
    return (lista.dados ?? []).filter((c) =>
      [c.nome, c.matricula, c.cargo, c.setor].some((v) => v.toLowerCase().includes(termo))
    );
  }, [lista.dados, busca]);
  const ordem = useOrdenacao(filtrados, VALOR_COLUNA, "nome");

  async function alterarAtivo(c: Colaborador) {
    if (c.ativo) {
      const ok = await confirmar({
        titulo: "Desativar colaborador",
        mensagem: `Desativar ${c.nome}? O histórico é preservado e você pode reativar depois.`,
        confirmarTexto: "Desativar",
        perigo: true,
      });
      if (!ok) return;
    }
    try {
      if (c.ativo) await api.desativarColaborador(c.id);
      else await api.reativarColaborador(c.id);
      toast.sucesso(c.ativo ? "Colaborador desativado." : "Colaborador reativado.");
      lista.recarregar();
    } catch (e) {
      toast.erro(e instanceof ApiError ? e.message : "Erro ao alterar o colaborador.");
    }
  }

  return (
    <>
      <div className="topo">
        <div>
          <h1>Colaboradores</h1>
          <p>Equipe sob sua supervisão.</p>
        </div>
        <div className="linha-acoes">
          <button className="btn btn-sutil" onClick={() => setImportando(true)}>
            <Icone nome="enviar" />
            Importar CSV
          </button>
          <button className="btn btn-primario" onClick={() => setCriando(true)}>
            <Icone nome="mais" />
            Novo colaborador
          </button>
        </div>
      </div>

      <div className="toolbar">
        <CampoBusca
          valor={busca}
          onMudar={setBusca}
          rotulo="Buscar colaborador"
          placeholder="Nome, matrícula, cargo ou setor…"
        />
        <label className="check-inline">
          <input
            type="checkbox"
            checked={incluirInativos}
            onChange={(e) => setIncluirInativos(e.target.checked)}
          />
          Mostrar inativos
        </label>
      </div>

      {lista.carregando ? (
        <Carregando />
      ) : lista.erro ? (
        <ErroBox mensagem={lista.erro} onTentar={lista.recarregar} />
      ) : !lista.dados || lista.dados.length === 0 ? (
        <Vazio
          texto={
            incluirInativos
              ? "Nenhum colaborador cadastrado ainda."
              : "Nenhum colaborador ativo. Cadastre um ou marque “Mostrar inativos”."
          }
          acao={
            <button className="btn btn-primario" onClick={() => setCriando(true)}>
              <Icone nome="mais" />
              Novo colaborador
            </button>
          }
        />
      ) : filtrados.length === 0 ? (
        <Vazio
          texto={`Nenhum colaborador corresponde a “${busca.trim()}”.`}
          acao={
            <button className="btn btn-sutil" onClick={() => setBusca("")}>
              Limpar busca
            </button>
          }
        />
      ) : (
        <>
          <p className="contagem" aria-live="polite">
            {filtrados.length === lista.dados.length
              ? `${lista.dados.length} colaborador(es)`
              : `${filtrados.length} de ${lista.dados.length} colaborador(es)`}
          </p>
          <div className="tabela-wrap cartao">
            <table className="tabela">
              <thead>
                <tr>
                  {COLUNAS.map(([chave, rotulo]) => (
                    <ThOrdenavel
                      key={chave}
                      rotulo={rotulo}
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
                    <td className="num">{c.matricula}</td>
                    <td style={{ whiteSpace: "normal" }}>
                      <Link className="link-linha" to={`/ficha/${c.id}`}>
                        {c.nome}
                      </Link>
                    </td>
                    <td>{c.cargo}</td>
                    <td>{c.setor}</td>
                    <td>
                      <span className={`selo ${c.ativo ? "selo-ativo" : "selo-inativo"}`}>
                        {c.ativo ? "Ativo" : "Inativo"}
                      </span>
                    </td>
                    <td className="col-acoes">
                      <div className="linha-acoes acoes-tabela">
                        {c.ativo && (
                          <button
                            className="btn btn-sm btn-sutil"
                            onClick={() => setAvaliando(c)}
                            aria-label={`Avaliar ${c.nome}`}
                          >
                            <Icone nome="estrela" />
                            Avaliar
                          </button>
                        )}
                        <Link
                          className="btn btn-sm btn-sutil"
                          to={`/ficha/${c.id}`}
                          aria-label={`Ficha de ${c.nome}`}
                        >
                          <Icone nome="ficha" />
                          Ficha
                        </Link>
                        <button
                          className="btn btn-sm btn-sutil"
                          onClick={() => setEdicao(c)}
                          aria-label={`Editar ${c.nome}`}
                        >
                          <Icone nome="editar" />
                          Editar
                        </button>
                        <button
                          className={`btn btn-sm ${c.ativo ? "btn-perigo" : "btn-sutil"}`}
                          onClick={() => alterarAtivo(c)}
                          aria-label={`${c.ativo ? "Desativar" : "Reativar"} ${c.nome}`}
                        >
                          {c.ativo ? "Desativar" : "Reativar"}
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
        <ColaboradorForm
          colaborador={edicao}
          onFechar={() => {
            setCriando(false);
            setEdicao(null);
          }}
          onSalvo={() => {
            setCriando(false);
            setEdicao(null);
            toast.sucesso("Colaborador salvo.");
            lista.recarregar();
          }}
        />
      )}

      {importando && (
        <ImportModal
          titulo="Importar colaboradores (CSV)"
          modelo={{
            cabecalho: ["matricula", "nome", "cargo", "setor", "dataAdmissao", "escolaridade"],
            exemplo: ["00123", "Maria Silva", "Operadora", "Produção", "2023-05-10", "Ensino Médio"],
            nomeArquivo: "modelo-colaboradores",
          }}
          enviar={(arquivo) => api.importarColaboradores(arquivo)}
          onFechar={() => setImportando(false)}
          onImportado={() => lista.recarregar()}
        />
      )}

      {avaliando && (
        <AvaliacaoModal
          colaboradorId={avaliando.id}
          colaboradorNome={avaliando.nome}
          onFechar={() => setAvaliando(null)}
          onSalvo={() => {
            setAvaliando(null);
            toast.sucesso("Avaliação registrada.");
          }}
        />
      )}
    </>
  );
}

function ColaboradorForm({
  colaborador,
  onFechar,
  onSalvo,
}: {
  colaborador: Colaborador | null;
  onFechar: () => void;
  onSalvo: () => void;
}) {
  const editando = colaborador !== null;
  const [form, setForm] = useState<ColaboradorInput>({
    matricula: colaborador?.matricula ?? "",
    nome: colaborador?.nome ?? "",
    cargo: colaborador?.cargo ?? "",
    setor: colaborador?.setor ?? "",
    dataAdmissao: colaborador?.dataAdmissao ?? "",
    escolaridade: colaborador?.escolaridade ?? "",
  });
  const [campos, setCampos] = useState<ErrosCampo>({});
  const formRef = useRef<HTMLFormElement>(null);
  useFocoNoPrimeiroErro(formRef, campos);
  const [erro, setErro] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  function set<K extends keyof ColaboradorInput>(chave: K, valor: ColaboradorInput[K]) {
    setForm((f) => ({ ...f, [chave]: valor }));
  }

  async function aoEnviar(e: FormEvent) {
    e.preventDefault();
    setErro(null);
    setCampos({});
    setEnviando(true);
    const payload: ColaboradorInput = {
      ...form,
      dataAdmissao: form.dataAdmissao || null,
      escolaridade: form.escolaridade || null,
    };
    try {
      if (editando && colaborador) await api.atualizarColaborador(colaborador.id, payload);
      else await api.criarColaborador(payload);
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
    <Modal titulo={editando ? "Editar colaborador" : "Novo colaborador"} onFechar={onFechar}>
      <form ref={formRef} onSubmit={aoEnviar}>
        {erro && <ErroBox mensagem={erro} />}
        <p className="form-legenda">
          Campos com <span aria-hidden="true">*</span>
          <span className="sr-only">asterisco</span> são obrigatórios.
        </p>
        <div className="form-grade">
          <div className="campo">
            <label htmlFor="mat">
              Matrícula <span className="obrigatorio" aria-hidden="true">*</span>
            </label>
            <input
              id="mat"
              value={form.matricula}
              onChange={(e) => set("matricula", e.target.value)}
              required
              maxLength={30}
              {...ariaErro(campos, "matricula", "mat")}
            />
            <ErroCampo erros={campos} nome="matricula" id="mat" />
          </div>
          <div className="campo campo-largo">
            <label htmlFor="nome">
              Nome <span className="obrigatorio" aria-hidden="true">*</span>
            </label>
            <input
              id="nome"
              value={form.nome}
              onChange={(e) => set("nome", e.target.value)}
              required
              maxLength={150}
              autoComplete="off"
              {...ariaErro(campos, "nome", "nome")}
            />
            <ErroCampo erros={campos} nome="nome" id="nome" />
          </div>
          <div className="campo">
            <label htmlFor="cargo">
              Cargo <span className="obrigatorio" aria-hidden="true">*</span>
            </label>
            <input
              id="cargo"
              value={form.cargo}
              onChange={(e) => set("cargo", e.target.value)}
              required
              maxLength={80}
              {...ariaErro(campos, "cargo", "cargo")}
            />
            <ErroCampo erros={campos} nome="cargo" id="cargo" />
          </div>
          <div className="campo">
            <label htmlFor="setor">
              Setor <span className="obrigatorio" aria-hidden="true">*</span>
            </label>
            <input
              id="setor"
              value={form.setor}
              onChange={(e) => set("setor", e.target.value)}
              required
              maxLength={80}
              {...ariaErro(campos, "setor", "setor")}
            />
            <ErroCampo erros={campos} nome="setor" id="setor" />
          </div>
          <div className="campo">
            <label htmlFor="adm">Data de admissão</label>
            <input
              id="adm"
              type="date"
              max={hoje()}
              value={form.dataAdmissao ?? ""}
              onChange={(e) => set("dataAdmissao", e.target.value)}
              {...ariaErro(campos, "dataAdmissao", "adm")}
            />
            <ErroCampo erros={campos} nome="dataAdmissao" id="adm" />
          </div>
          <div className="campo">
            <label htmlFor="esc">Escolaridade</label>
            <input
              id="esc"
              value={form.escolaridade ?? ""}
              onChange={(e) => set("escolaridade", e.target.value)}
              maxLength={60}
              {...ariaErro(campos, "escolaridade", "esc")}
            />
            <ErroCampo erros={campos} nome="escolaridade" id="esc" />
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
