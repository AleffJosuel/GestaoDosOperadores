import { useMemo, useState, type FormEvent } from "react";
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
            Importar CSV
          </button>
          <button className="btn btn-primario" onClick={() => setCriando(true)}>
            Novo colaborador
          </button>
        </div>
      </div>

      <div className="toolbar">
        <input
          className="busca"
          type="search"
          placeholder="Buscar por nome, matrícula, cargo ou setor…"
          value={busca}
          onChange={(e) => setBusca(e.target.value)}
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
        <Vazio texto="Nenhum colaborador cadastrado ainda." />
      ) : filtrados.length === 0 ? (
        <Vazio texto="Nenhum colaborador corresponde à busca." />
      ) : (
        <div className="tabela-wrap cartao">
          <table className="tabela">
            <thead>
              <tr>
                <th>Matrícula</th>
                <th>Nome</th>
                <th>Cargo</th>
                <th>Setor</th>
                <th>Situação</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {filtrados.map((c) => (
                <tr key={c.id}>
                  <td>{c.matricula}</td>
                  <td style={{ whiteSpace: "normal" }}>{c.nome}</td>
                  <td>{c.cargo}</td>
                  <td>{c.setor}</td>
                  <td>
                    <span className={`selo ${c.ativo ? "selo-ativo" : "selo-inativo"}`}>
                      {c.ativo ? "Ativo" : "Inativo"}
                    </span>
                  </td>
                  <td>
                    <div className="linha-acoes">
                      {c.ativo && (
                        <button className="btn btn-sm btn-primario" onClick={() => setAvaliando(c)}>
                          Avaliar
                        </button>
                      )}
                      <Link className="btn btn-sm btn-sutil" to={`/ficha/${c.id}`}>
                        Ficha
                      </Link>
                      <button className="btn btn-sm btn-sutil" onClick={() => setEdicao(c)}>
                        Editar
                      </button>
                      <button
                        className={`btn btn-sm ${c.ativo ? "btn-perigo" : "btn-sutil"}`}
                        onClick={() => alterarAtivo(c)}
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
  const [campos, setCampos] = useState<Record<string, string>>({});
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
      <form onSubmit={aoEnviar}>
        {erro && (
          <div className="estado-erro" role="alert">
            {erro}
          </div>
        )}
        <div className="campo">
          <label htmlFor="mat">Matrícula</label>
          <input
            id="mat"
            value={form.matricula}
            onChange={(e) => set("matricula", e.target.value)}
            required
          />
          {campos.matricula && <span className="erro-campo">{campos.matricula}</span>}
        </div>
        <div className="campo">
          <label htmlFor="nome">Nome</label>
          <input id="nome" value={form.nome} onChange={(e) => set("nome", e.target.value)} required />
          {campos.nome && <span className="erro-campo">{campos.nome}</span>}
        </div>
        <div className="campo">
          <label htmlFor="cargo">Cargo</label>
          <input id="cargo" value={form.cargo} onChange={(e) => set("cargo", e.target.value)} required />
          {campos.cargo && <span className="erro-campo">{campos.cargo}</span>}
        </div>
        <div className="campo">
          <label htmlFor="setor">Setor</label>
          <input id="setor" value={form.setor} onChange={(e) => set("setor", e.target.value)} required />
          {campos.setor && <span className="erro-campo">{campos.setor}</span>}
        </div>
        <div className="campo">
          <label htmlFor="adm">Data de admissão (opcional)</label>
          <input
            id="adm"
            type="date"
            max={new Date().toISOString().slice(0, 10)}
            value={form.dataAdmissao ?? ""}
            onChange={(e) => set("dataAdmissao", e.target.value)}
          />
          {campos.dataAdmissao && <span className="erro-campo">{campos.dataAdmissao}</span>}
        </div>
        <div className="campo">
          <label htmlFor="esc">Escolaridade (opcional)</label>
          <input
            id="esc"
            value={form.escolaridade ?? ""}
            onChange={(e) => set("escolaridade", e.target.value)}
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
