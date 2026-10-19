import { useState, type FormEvent } from "react";
import { api, ApiError } from "../api/client";
import { Modal } from "./Modal";

const MIN_SENHA = 12;

export function TrocarSenhaModal({ onFechar }: { onFechar: () => void }) {
  const [senhaAtual, setSenhaAtual] = useState("");
  const [novaSenha, setNovaSenha] = useState("");
  const [confirmar, setConfirmar] = useState("");
  const [erro, setErro] = useState<string | null>(null);
  const [ok, setOk] = useState(false);
  const [enviando, setEnviando] = useState(false);

  async function aoEnviar(e: FormEvent) {
    e.preventDefault();
    setErro(null);
    if (novaSenha.length < MIN_SENHA) {
      setErro(`A nova senha precisa ter ao menos ${MIN_SENHA} caracteres.`);
      return;
    }
    if (novaSenha !== confirmar) {
      setErro("A confirmação não bate com a nova senha.");
      return;
    }
    setEnviando(true);
    try {
      await api.trocarSenha(senhaAtual, novaSenha);
      setOk(true);
    } catch (err) {
      setErro(err instanceof ApiError ? err.message : "Erro ao trocar a senha.");
    } finally {
      setEnviando(false);
    }
  }

  return (
    <Modal titulo="Trocar senha" onFechar={onFechar}>
      {ok ? (
        <>
          <div className="estado-erro" role="status" style={{ background: "var(--cor-bom-suave)", color: "var(--cor-bom)" }}>
            Senha alterada com sucesso.
          </div>
          <div className="modal-acoes">
            <button className="btn btn-primario" onClick={onFechar}>
              Fechar
            </button>
          </div>
        </>
      ) : (
        <form onSubmit={aoEnviar}>
          {erro && (
            <div className="estado-erro" role="alert">
              {erro}
            </div>
          )}
          <div className="campo">
            <label htmlFor="atual">Senha atual</label>
            <input
              id="atual"
              type="password"
              autoComplete="current-password"
              value={senhaAtual}
              onChange={(e) => setSenhaAtual(e.target.value)}
              required
            />
          </div>
          <div className="campo">
            <label htmlFor="nova">Nova senha (mín. {MIN_SENHA} caracteres)</label>
            <input
              id="nova"
              type="password"
              autoComplete="new-password"
              value={novaSenha}
              onChange={(e) => setNovaSenha(e.target.value)}
              required
            />
          </div>
          <div className="campo">
            <label htmlFor="conf">Confirmar nova senha</label>
            <input
              id="conf"
              type="password"
              autoComplete="new-password"
              value={confirmar}
              onChange={(e) => setConfirmar(e.target.value)}
              required
            />
          </div>
          <div className="modal-acoes">
            <button type="button" className="btn btn-sutil" onClick={onFechar}>
              Cancelar
            </button>
            <button type="submit" className="btn btn-primario" disabled={enviando}>
              {enviando ? "Salvando…" : "Trocar senha"}
            </button>
          </div>
        </form>
      )}
    </Modal>
  );
}
