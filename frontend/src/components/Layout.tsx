import { useState } from "react";
import { NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { TrocarSenhaModal } from "./TrocarSenhaModal";

const linkClasse = ({ isActive }: { isActive: boolean }) =>
  isActive ? "nav-link ativo" : "nav-link";

export function Layout() {
  const { email, sair } = useAuth();
  const [trocandoSenha, setTrocandoSenha] = useState(false);
  return (
    <div className="app">
      <nav className="barra-lateral" aria-label="Navegação principal">
        <div className="marca">
          <span className="marca-icone">GO</span>
          <strong>
            Gestão dos
            <br />
            Operadores
          </strong>
        </div>
        <NavLink to="/" end className={linkClasse}>
          Dashboard
        </NavLink>
        <NavLink to="/mapa" className={linkClasse}>
          Mapa de competências
        </NavLink>
        <NavLink to="/colaboradores" className={linkClasse}>
          Colaboradores
        </NavLink>
        <NavLink to="/competencias" className={linkClasse}>
          Competências
        </NavLink>
        <NavLink to="/pdi" className={linkClasse}>
          PDI
        </NavLink>
        <NavLink to="/configuracoes" className={linkClasse}>
          Configurações
        </NavLink>
        <div className="rodape">
          <div title={email ?? undefined} style={{ marginBottom: 8, wordBreak: "break-all" }}>
            {email}
          </div>
          <div className="linha-acoes">
            <button className="btn btn-sm btn-sutil" onClick={() => setTrocandoSenha(true)}>
              Trocar senha
            </button>
            <button className="btn btn-sm btn-sutil" onClick={sair}>
              Sair
            </button>
          </div>
        </div>
      </nav>
      <main className="conteudo">
        <Outlet />
      </main>
      {trocandoSenha && <TrocarSenhaModal onFechar={() => setTrocandoSenha(false)} />}
    </div>
  );
}
