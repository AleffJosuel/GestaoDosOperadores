import { createContext, useContext, useRef, useState, type ReactNode } from "react";
import { Modal } from "./Modal";

interface OpcoesConfirm {
  titulo?: string;
  mensagem: string;
  confirmarTexto?: string;
  perigo?: boolean;
}
interface ConfirmContextValor {
  confirmar: (opcoes: OpcoesConfirm) => Promise<boolean>;
}

const ConfirmContext = createContext<ConfirmContextValor | null>(null);

export function ConfirmProvider({ children }: { children: ReactNode }) {
  const [opcoes, setOpcoes] = useState<OpcoesConfirm | null>(null);
  const resolver = useRef<((v: boolean) => void) | null>(null);

  function confirmar(o: OpcoesConfirm): Promise<boolean> {
    setOpcoes(o);
    return new Promise<boolean>((resolve) => {
      resolver.current = resolve;
    });
  }

  function responder(valor: boolean) {
    setOpcoes(null);
    resolver.current?.(valor);
    resolver.current = null;
  }

  return (
    <ConfirmContext.Provider value={{ confirmar }}>
      {children}
      {opcoes && (
        <Modal titulo={opcoes.titulo ?? "Confirmar"} onFechar={() => responder(false)}>
          <p style={{ marginTop: 0 }}>{opcoes.mensagem}</p>
          <div className="modal-acoes">
            <button className="btn btn-sutil" onClick={() => responder(false)}>
              Cancelar
            </button>
            <button
              className={`btn ${opcoes.perigo ? "btn-perigo" : "btn-primario"}`}
              onClick={() => responder(true)}
              autoFocus
            >
              {opcoes.confirmarTexto ?? "Confirmar"}
            </button>
          </div>
        </Modal>
      )}
    </ConfirmContext.Provider>
  );
}

export function useConfirm(): ConfirmContextValor["confirmar"] {
  const ctx = useContext(ConfirmContext);
  if (!ctx) throw new Error("useConfirm precisa estar dentro de ConfirmProvider");
  return ctx.confirmar;
}
