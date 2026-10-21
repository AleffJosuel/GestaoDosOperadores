import { useEffect, useRef, useState } from "react";
import { NavLink, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { useTituloPagina } from "../hooks/useTituloPagina";
import { Icone, type NomeIcone } from "./Icone";
import { TrocarSenhaModal } from "./TrocarSenhaModal";

interface Item {
  para: string;
  rotulo: string;
  icone: NomeIcone;
  fim?: boolean;
}

const GRUPOS: { titulo: string; itens: Item[] }[] = [
  {
    titulo: "Visão geral",
    itens: [
      { para: "/", rotulo: "Dashboard", icone: "painel", fim: true },
      { para: "/mapa", rotulo: "Mapa de competências", icone: "mapa" },
    ],
  },
  {
    titulo: "Cadastros",
    itens: [
      { para: "/colaboradores", rotulo: "Colaboradores", icone: "usuarios" },
      { para: "/competencias", rotulo: "Competências", icone: "alvo" },
    ],
  },
  {
    titulo: "Desenvolvimento",
    itens: [{ para: "/pdi", rotulo: "PDI", icone: "prancheta" }],
  },
  {
    titulo: "Sistema",
    itens: [{ para: "/configuracoes", rotulo: "Configurações", icone: "ajustes" }],
  },
];

function tituloDaRota(caminho: string): string | null {
  if (caminho.startsWith("/ficha/")) return "Ficha do colaborador";
  for (const g of GRUPOS) {
    const item = g.itens.find((i) => i.para === caminho);
    if (item) return item.rotulo;
  }
  return null;
}

const linkClasse = ({ isActive }: { isActive: boolean }) =>
  isActive ? "nav-link ativo" : "nav-link";

export function Layout() {
  const { email, sair } = useAuth();
  const { pathname } = useLocation();
  const [trocandoSenha, setTrocandoSenha] = useState(false);
  // Só tem efeito em telas estreitas, onde a barra lateral vira um menu sobreposto.
  const [menuAberto, setMenuAberto] = useState(false);
  const botaoMenu = useRef<HTMLButtonElement>(null);
  const barra = useRef<HTMLElement>(null);
  const conteudo = useRef<HTMLElement>(null);

  useTituloPagina(tituloDaRota(pathname));

  const menuAbertoRef = useRef(menuAberto);
  menuAbertoRef.current = menuAberto;

  // Ao navegar pelo menu móvel: fecha e leva o foco ao conteúdo novo (senão ele some com o menu).
  useEffect(() => {
    if (!menuAbertoRef.current) return;
    setMenuAberto(false);
    conteudo.current?.focus();
  }, [pathname]);

  useEffect(() => {
    if (!menuAberto) return;
    // O Chromium só aceita foco no painel depois que a transição de abertura termina.
    const nav = barra.current;
    const focarPrimeiro = () => nav?.querySelector<HTMLElement>(".nav-link")?.focus();
    nav?.addEventListener("transitionend", focarPrimeiro, { once: true });
    const reserva = window.setTimeout(focarPrimeiro, 300);
    const aoTeclar = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        setMenuAberto(false);
        botaoMenu.current?.focus();
      }
    };
    document.addEventListener("keydown", aoTeclar);
    return () => {
      document.removeEventListener("keydown", aoTeclar);
      nav?.removeEventListener("transitionend", focarPrimeiro);
      window.clearTimeout(reserva);
    };
  }, [menuAberto]);

  const inicial = (email ?? "?").trim().charAt(0).toUpperCase();

  return (
    <div className={menuAberto ? "app menu-aberto" : "app"}>
      <a className="pular-link" href="#conteudo">
        Pular para o conteúdo
      </a>

      <header className="topo-movel">
        <button
          ref={botaoMenu}
          type="button"
          className="btn btn-icone"
          aria-label={menuAberto ? "Fechar menu" : "Abrir menu"}
          aria-expanded={menuAberto}
          aria-controls="barra-lateral"
          onClick={() => setMenuAberto((v) => !v)}
        >
          <Icone nome={menuAberto ? "fechar" : "menu"} />
        </button>
        <div className="marca marca-compacta">
          <span className="marca-icone" aria-hidden="true">GO</span>
          <strong>Gestão dos Operadores</strong>
        </div>
      </header>

      <div className="menu-fundo" aria-hidden="true" onClick={() => setMenuAberto(false)} />

      <nav id="barra-lateral" ref={barra} className="barra-lateral" aria-label="Navegação principal">
        <div className="marca">
          <span className="marca-icone" aria-hidden="true">GO</span>
          <strong>
            Gestão dos
            <br />
            Operadores
          </strong>
        </div>

        {GRUPOS.map((g) => (
          <div className="nav-grupo" key={g.titulo}>
            <span className="nav-grupo-titulo">{g.titulo}</span>
            {g.itens.map((i) => (
              <NavLink key={i.para} to={i.para} end={i.fim} className={linkClasse}>
                <Icone nome={i.icone} />
                {i.rotulo}
              </NavLink>
            ))}
          </div>
        ))}

        <div className="rodape conta">
          <div className="conta-usuario">
            <span className="conta-avatar" aria-hidden="true">{inicial}</span>
            <div className="conta-dados">
              <span className="conta-papel">Supervisor</span>
              <span className="conta-email" title={email ?? undefined}>
                {email}
              </span>
            </div>
          </div>
          <div className="conta-acoes">
            <button className="nav-link" type="button" onClick={() => setTrocandoSenha(true)}>
              <Icone nome="chave" />
              Trocar senha
            </button>
            <button className="nav-link" type="button" onClick={sair}>
              <Icone nome="sair" />
              Sair
            </button>
          </div>
        </div>
      </nav>

      <main id="conteudo" ref={conteudo} className="conteudo" tabIndex={-1}>
        <div className="conteudo-interno">
          <Outlet />
        </div>
      </main>
      {trocandoSenha && <TrocarSenhaModal onFechar={() => setTrocandoSenha(false)} />}
    </div>
  );
}
