import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { aoReceber401, api, definirToken } from "../api/client";

interface Sessao {
  token: string;
  email: string;
}

interface AuthContextValor {
  autenticado: boolean;
  email: string | null;
  entrar: (email: string, senha: string) => Promise<void>;
  sair: () => void;
}

const CHAVE = "gestao-operadores-sessao";
const AuthContext = createContext<AuthContextValor | null>(null);

function lerSessao(): Sessao | null {
  try {
    const bruto = sessionStorage.getItem(CHAVE);
    return bruto ? (JSON.parse(bruto) as Sessao) : null;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [sessao, setSessao] = useState<Sessao | null>(() => {
    const s = lerSessao();
    if (s) definirToken(s.token);
    return s;
  });

  useEffect(() => {
    // Se qualquer chamada receber 401, encerramos a sessão.
    aoReceber401(() => {
      setSessao(null);
      definirToken(null);
      sessionStorage.removeItem(CHAVE);
    });
  }, []);

  const valor = useMemo<AuthContextValor>(
    () => ({
      autenticado: sessao !== null,
      email: sessao?.email ?? null,
      entrar: async (email, senha) => {
        const resp = await api.login(email, senha);
        const nova: Sessao = { token: resp.token, email };
        definirToken(nova.token);
        sessionStorage.setItem(CHAVE, JSON.stringify(nova));
        setSessao(nova);
      },
      sair: () => {
        setSessao(null);
        definirToken(null);
        sessionStorage.removeItem(CHAVE);
      },
    }),
    [sessao]
  );

  return <AuthContext.Provider value={valor}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValor {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth precisa estar dentro de AuthProvider");
  return ctx;
}
