import { useEffect, useId, useRef, type ReactNode } from "react";
import { Icone } from "./Icone";

interface Props {
  titulo: string;
  children: ReactNode;
  onFechar: () => void;
}

const FOCAVEIS =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

// Pilha dos modais abertos: só o do topo reage ao Esc (ex.: Confirm aberto sobre um formulário).
const pilha: symbol[] = [];

/**
 * Modal acessível: fecha no Esc, no ✕ e no clique fora; prende o Tab dentro do diálogo,
 * foca o primeiro campo ao abrir, devolve o foco a quem abriu e trava a rolagem da página.
 */
export function Modal({ titulo, children, onFechar }: Props) {
  const idTitulo = useId();
  const caixa = useRef<HTMLDivElement>(null);
  // Ref para não reexecutar o efeito de montagem quando o pai passar uma função nova.
  const fechar = useRef(onFechar);
  fechar.current = onFechar;

  useEffect(() => {
    const id = Symbol("modal");
    pilha.push(id);
    const anterior = document.activeElement as HTMLElement | null;
    const el = caixa.current!;

    // autoFocus de um filho já foi aplicado pelo React; senão, primeiro campo; senão, o diálogo.
    if (!el.contains(document.activeElement)) {
      const campo = el.querySelector<HTMLElement>(
        "input:not([disabled]):not([type=hidden]), select:not([disabled]), textarea:not([disabled])"
      );
      (campo ?? el).focus();
    }

    const overflowAntes = document.body.style.overflow;
    document.body.style.overflow = "hidden";

    const aoTeclar = (e: KeyboardEvent) => {
      if (pilha[pilha.length - 1] !== id) return;
      if (e.key === "Escape") {
        e.preventDefault();
        fechar.current();
        return;
      }
      if (e.key !== "Tab") return;
      const focaveis = Array.from(el.querySelectorAll<HTMLElement>(FOCAVEIS));
      if (focaveis.length === 0) {
        e.preventDefault();
        return;
      }
      const primeiro = focaveis[0];
      const ultimo = focaveis[focaveis.length - 1];
      if (e.shiftKey && (document.activeElement === primeiro || document.activeElement === el)) {
        e.preventDefault();
        ultimo.focus();
      } else if (!e.shiftKey && document.activeElement === ultimo) {
        e.preventDefault();
        primeiro.focus();
      }
    };
    document.addEventListener("keydown", aoTeclar);

    return () => {
      document.removeEventListener("keydown", aoTeclar);
      pilha.splice(pilha.indexOf(id), 1);
      if (pilha.length === 0) document.body.style.overflow = overflowAntes;
      if (anterior && document.contains(anterior)) anterior.focus();
    };
  }, []);

  return (
    <div className="modal-fundo" onMouseDown={() => fechar.current()}>
      <div
        ref={caixa}
        className="modal cartao"
        role="dialog"
        aria-modal="true"
        aria-labelledby={idTitulo}
        tabIndex={-1}
        onMouseDown={(e) => e.stopPropagation()}
      >
        <div className="modal-cabecalho">
          <h2 id={idTitulo}>{titulo}</h2>
          <button
            type="button"
            className="btn btn-icone"
            aria-label="Fechar"
            onClick={() => fechar.current()}
          >
            <Icone nome="fechar" />
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}
