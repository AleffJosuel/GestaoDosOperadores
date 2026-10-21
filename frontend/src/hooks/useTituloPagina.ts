import { useEffect } from "react";

const SISTEMA = "Gestão dos Operadores";

/** Título da aba do navegador (ajuda a orientar e é o que o leitor de tela anuncia). */
export function useTituloPagina(titulo: string | null) {
  useEffect(() => {
    document.title = titulo ? `${titulo} · ${SISTEMA}` : SISTEMA;
  }, [titulo]);
}
