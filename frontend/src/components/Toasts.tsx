import { createContext, useCallback, useContext, useState, type ReactNode } from "react";

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

const ToastContext = createContext<ToastContextValor | null>(null);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const adicionar = useCallback((mensagem: string, tipo: TipoToast) => {
    const id = Date.now() + Math.random();
    setToasts((t) => [...t, { id, mensagem, tipo }]);
    window.setTimeout(() => setToasts((t) => t.filter((x) => x.id !== id)), 3500);
  }, []);

  const valor: ToastContextValor = {
    sucesso: (m) => adicionar(m, "sucesso"),
    erro: (m) => adicionar(m, "erro"),
  };

  return (
    <ToastContext.Provider value={valor}>
      {children}
      <div className="toasts" aria-live="polite">
        {toasts.map((t) => (
          <div key={t.id} className={`toast toast-${t.tipo}`} role="status">
            {t.mensagem}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast(): ToastContextValor {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error("useToast precisa estar dentro de ToastProvider");
  return ctx;
}
