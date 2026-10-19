import { useEffect, type ReactNode } from "react";

interface Props {
  titulo: string;
  children: ReactNode;
  onFechar: () => void;
}

/** Modal simples: fecha no Esc e no clique fora. */
export function Modal({ titulo, children, onFechar }: Props) {
  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => {
      if (e.key === "Escape") onFechar();
    };
    document.addEventListener("keydown", aoTeclar);
    return () => document.removeEventListener("keydown", aoTeclar);
  }, [onFechar]);

  return (
    <div className="modal-fundo" onMouseDown={onFechar}>
      <div
        className="modal cartao"
        role="dialog"
        aria-modal="true"
        aria-label={titulo}
        onMouseDown={(e) => e.stopPropagation()}
      >
        <h2>{titulo}</h2>
        {children}
      </div>
    </div>
  );
}
