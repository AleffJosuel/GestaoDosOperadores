import type { ReactNode } from "react";
import type { ClassificacaoDto } from "../api/types";
import { Icone } from "./Icone";

export function Carregando({ texto = "Carregando…" }: { texto?: string }) {
  return (
    <div className="estado" role="status" aria-live="polite">
      <div className="spinner" aria-hidden="true" />
      {texto}
    </div>
  );
}

/** Estado vazio; `acao` (opcional) é o próximo passo real, ex.: botão de cadastrar. */
export function Vazio({ texto, acao }: { texto: string; acao?: ReactNode }) {
  return (
    <div className="estado estado-vazio">
      <Icone nome="vazio" className="estado-icone" />
      <span>{texto}</span>
      {acao}
    </div>
  );
}

export function ErroBox({ mensagem, onTentar }: { mensagem: string; onTentar?: () => void }) {
  return (
    <div className="estado-erro" role="alert">
      <Icone nome="erro" />
      <span>{mensagem}</span>
      {onTentar && (
        <button type="button" className="btn btn-sm btn-sutil" onClick={onTentar}>
          Tentar de novo
        </button>
      )}
    </div>
  );
}

export function AvisoBox({ children }: { children: ReactNode }) {
  return (
    <div className="estado-aviso" role="status">
      <Icone nome="aviso" />
      <span>{children}</span>
    </div>
  );
}

export function SucessoBox({ children }: { children: ReactNode }) {
  return (
    <div className="estado-sucesso" role="status">
      <Icone nome="sucesso" />
      <span>{children}</span>
    </div>
  );
}

export function Clf({ c }: { c: ClassificacaoDto | null }) {
  if (!c) return <span className="clf clf-sem">—</span>;
  return <span className={`clf clf-${c.codigo}`}>{c.rotulo}</span>;
}
