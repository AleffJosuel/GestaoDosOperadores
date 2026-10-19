import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { aoReceber401, api, definirToken } from "../api/client";

interface Sessao {
  token: string;
  email: string;
}

interface AuthContextValor {
  autenticado: boolean;
  email: string | null;
  /** Mensagem a mostrar no login quando a sessão foi encerrada sozinha (expirou/401). */
  motivoSaida: string | null;
  entrar: (email: string, senha: string) => Promise<void>;
  sair: () => void;
}

const CHAVE = "gestao-operadores-sessao";
const AuthContext = createContext<AuthContextValor | null>(null);

/** Lê o instante de expiração (ms época) do claim `exp` do JWT, ou null se não der. */
function expiracaoDoToken(token: string): number | null {
  try {
    const payload = token.split(".")[1];
    if (!payload) return null;
    const json = atob(payload.replace(/-/g, "+").replace(/_/g, "/"));
    const dados = JSON.parse(json) as { exp?: number };
    return typeof dados.exp === "number" ? dados.exp * 1000 : null;
  } catch {
    return null;
  }
}

/** Sessão expirada (segundo o próprio token)? Sem `exp` legível, assumimos que não. */
function expirada(sessao: Sessao): boolean {
  const exp = expiracaoDoToken(sessao.token);
  return exp !== null && exp <= Date.now();
}

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
    // Se o token guardado já venceu, começa deslogado (não restaura sessão morta).
    if (s && !expirada(s)) {
      definirToken(s.token);
      return s;
    }
    if (s) sessionStorage.removeItem(CHAVE);
    return null;
  });
  const [motivoSaida, setMotivoSaida] = useState<string | null>(null);
  const temporizador = useRef<ReturnType<typeof setTimeout> | null>(null);

  const encerrar = useCallback((motivo: string | null) => {
    if (temporizador.current) {
      clearTimeout(temporizador.current);
      temporizador.current = null;
    }
    setSessao(null);
    definirToken(null);
    sessionStorage.removeItem(CHAVE);
    setMotivoSaida(motivo);
  }, []);

  useEffect(() => {
    // Se qualquer chamada receber 401, encerramos a sessão (token revogado/inválido).
    aoReceber401(() => encerrar("Sua sessão expirou. Entre novamente."));
  }, [encerrar]);

  // Logout proativo: agenda o encerramento para o instante em que o token vence,
  // sem depender de uma requisição tomar 401 primeiro.
  useEffect(() => {
    if (!sessao) return;
    const exp = expiracaoDoToken(sessao.token);
    if (exp === null) return; // token sem exp legível: só o 401 encerra

    const restante = exp - Date.now();
    if (restante <= 0) {
      encerrar("Sua sessão expirou. Entre novamente.");
      return;
    }
    // setTimeout satura acima de ~24,8 dias; limita para não disparar cedo demais.
    const atraso = Math.min(restante, 2_147_483_647);
    temporizador.current = setTimeout(() => {
      encerrar("Sua sessão expirou. Entre novamente.");
    }, atraso);

    return () => {
      if (temporizador.current) {
        clearTimeout(temporizador.current);
        temporizador.current = null;
      }
    };
  }, [sessao, encerrar]);

  const valor = useMemo<AuthContextValor>(
    () => ({
      autenticado: sessao !== null,
      email: sessao?.email ?? null,
      motivoSaida,
      entrar: async (email, senha) => {
        const resp = await api.login(email, senha);
        const nova: Sessao = { token: resp.token, email };
        definirToken(nova.token);
        sessionStorage.setItem(CHAVE, JSON.stringify(nova));
        setMotivoSaida(null);
        setSessao(nova);
      },
      sair: () => encerrar(null),
    }),
    [sessao, motivoSaida, encerrar]
  );

  return <AuthContext.Provider value={valor}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValor {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth precisa estar dentro de AuthProvider");
  return ctx;
}
