import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { Icone } from "./Icone";

type TipoToast = "sucesso" | "erro";
interface Toast {
  id: number;
  mensagem: string;
  tipo: TipoToast;
}
interface ToastContextValor {
  sucesso: (mensagem: string) => void;
  erro: (mensagem: string) => void;
}

// Erro fica mais tempo: costuma ter texto para ler e algo a corrigir.
const DURACAO: Record<TipoToast, number> = { sucesso: 4000, erro: 7000 };

const ToastContext = createContext<ToastContextValor | null>(null);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const remover = useCallback((id: number) => {
    setToasts((t) => t.filter((x) => x.id !== id));
  }, []);

  const adicionar = useCallback((mensagem: string, tipo: TipoToast) => {
    const id = Date.now() + Math.random();
    setToasts((t) => [...t, { id, mensagem, tipo }]);
  }, []);

  const valor = useMemo<ToastContextValor>(
    () => ({
      sucesso: (m) => adicionar(m, "sucesso"),
      erro: (m) => adicionar(m, "erro"),
    }),
    [adicionar]
  );

  // Duas regiões vivas fixas (precisam existir antes do conteúdo para o leitor de tela anunciar):
  // erros com prioridade (assertive), sucessos sem interromper (polite).
  return (
    <ToastContext.Provider value={valor}>
      {children}
      <div className="toasts">
        <div className="toasts-regiao" aria-live="assertive">
          {toasts
            .filter((t) => t.tipo === "erro")
            .map((t) => (
              <ToastItem key={t.id} toast={t} remover={remover} />
            ))}
        </div>
        <div className="toasts-regiao" aria-live="polite">
          {toasts
            .filter((t) => t.tipo === "sucesso")
            .map((t) => (
              <ToastItem key={t.id} toast={t} remover={remover} />
            ))}
        </div>
      </div>
    </ToastContext.Provider>
  );
}

/** Some sozinho, mas pausa enquanto o mouse ou o foco estão nele; pode ser fechado no ✕. */
function ToastItem({ toast, remover }: { toast: Toast; remover: (id: number) => void }) {
  const [pausado, setPausado] = useState(false);

  useEffect(() => {
    if (pausado) return;
    const t = window.setTimeout(() => remover(toast.id), DURACAO[toast.tipo]);
    return () => window.clearTimeout(t);
  }, [pausado, toast.id, toast.tipo, remover]);

  return (
    <div
      className={`toast toast-${toast.tipo}`}
      onMouseEnter={() => setPausado(true)}
      onMouseLeave={() => setPausado(false)}
      onFocus={() => setPausado(true)}
      onBlur={() => setPausado(false)}
    >
      <Icone nome={toast.tipo === "erro" ? "erro" : "sucesso"} />
      <span className="toast-texto">{toast.mensagem}</span>
      <button
        type="button"
        className="toast-fechar"
        aria-label="Fechar aviso"
        onClick={() => remover(toast.id)}
      >
        <Icone nome="fechar" />
      </button>
    </div>
  );
}

export function useToast(): ToastContextValor {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error("useToast precisa estar dentro de ToastProvider");
  return ctx;
}
