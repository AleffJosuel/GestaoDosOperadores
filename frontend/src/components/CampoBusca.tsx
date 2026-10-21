import { useRef } from "react";
import { Icone } from "./Icone";

/** Busca com rótulo acessível (o placeholder some ao digitar) e botão de limpar. */
export function CampoBusca({
  valor,
  onMudar,
  rotulo,
  placeholder,
}: {
  valor: string;
  onMudar: (v: string) => void;
  rotulo: string;
  placeholder: string;
}) {
  const input = useRef<HTMLInputElement>(null);
  return (
    <div className="busca-campo">
      <Icone nome="busca" className="busca-icone" />
      <input
        ref={input}
        className="busca"
        type="search"
        aria-label={rotulo}
        placeholder={placeholder}
        value={valor}
        onChange={(e) => onMudar(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === "Escape" && valor) {
            e.stopPropagation();
            onMudar("");
          }
        }}
      />
      {valor && (
        <button
          type="button"
          className="busca-limpar"
          aria-label="Limpar busca"
          onClick={() => {
            onMudar("");
            input.current?.focus();
          }}
        >
          <Icone nome="fechar" />
        </button>
      )}
    </div>
  );
}
