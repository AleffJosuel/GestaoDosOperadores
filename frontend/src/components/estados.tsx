import type { ClassificacaoDto } from "../api/types";

export function Carregando({ texto = "Carregando…" }: { texto?: string }) {
  return (
    <div className="estado" role="status" aria-live="polite">
      <div className="spinner" aria-hidden="true" />
      {texto}
    </div>
  );
}

export function Vazio({ texto }: { texto: string }) {
  return <div className="estado">{texto}</div>;
}

export function ErroBox({ mensagem, onTentar }: { mensagem: string; onTentar?: () => void }) {
  return (
    <div className="estado-erro" role="alert">
      {mensagem}
      {onTentar && (
        <>
          {" "}
          <button className="btn btn-sm btn-sutil" onClick={onTentar}>
            Tentar de novo
          </button>
        </>
      )}
    </div>
  );
}

export function Clf({ c }: { c: ClassificacaoDto | null }) {
  if (!c) return <span className="clf clf-sem">—</span>;
  return <span className={`clf clf-${c.codigo}`}>{c.rotulo}</span>;
}
