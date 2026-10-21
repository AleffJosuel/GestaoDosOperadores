import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { ApiError } from "../api/client";
import { AvisoBox, ErroBox } from "../components/estados";
import { useTituloPagina } from "../hooks/useTituloPagina";

export function LoginPage() {
  const { entrar, motivoSaida } = useAuth();
  const navegar = useNavigate();
  const [email, setEmail] = useState("");
  const [senha, setSenha] = useState("");
  const [erro, setErro] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  useTituloPagina("Entrar");

  async function aoEnviar(e: FormEvent) {
    e.preventDefault();
    setErro(null);
    setEnviando(true);
    try {
      await entrar(email, senha);
      navegar("/", { replace: true });
    } catch (err) {
      setErro(err instanceof ApiError ? err.message : "Não foi possível entrar.");
    } finally {
      setEnviando(false);
    }
  }

  return (
    <div className="login-tela">
      <form className="cartao login-cartao" onSubmit={aoEnviar}>
        <div className="marca">
          <span className="marca-icone">GO</span>
          <strong>Gestão dos Operadores</strong>
        </div>
        <p style={{ marginTop: 0, color: "var(--cor-texto-suave)" }}>
          Entre com seu e-mail e senha de supervisor.
        </p>

        {erro && (
          <ErroBox mensagem={erro} />
        )}
        {!erro && motivoSaida && <AvisoBox>{motivoSaida}</AvisoBox>}

        <div className="campo">
          <label htmlFor="email">E-mail</label>
          <input
            id="email"
            type="email"
            autoComplete="username"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
        </div>
        <div className="campo">
          <label htmlFor="senha">Senha</label>
          <input
            id="senha"
            type="password"
            autoComplete="current-password"
            value={senha}
            onChange={(e) => setSenha(e.target.value)}
            required
          />
        </div>
        <button className="btn btn-primario" type="submit" disabled={enviando} style={{ width: "100%" }}>
          {enviando ? "Entrando…" : "Entrar"}
        </button>
      </form>
    </div>
  );
}
