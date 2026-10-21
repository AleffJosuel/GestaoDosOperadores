import { useEffect, type RefObject } from "react";

/** Erros por campo vindos do backend (ApiError.campos), indexados pelo nome do campo. */
export type ErrosCampo = Record<string, string>;

/** Liga o input à mensagem de erro: o leitor de tela lê o erro junto com o rótulo. */
export function ariaErro(erros: ErrosCampo, nome: string, id: string) {
  return erros[nome]
    ? { "aria-invalid": true as const, "aria-describedby": `${id}-erro` }
    : {};
}

export function ErroCampo({ erros, nome, id }: { erros: ErrosCampo; nome: string; id: string }) {
  const msg = erros[nome];
  return msg ? (
    <span id={`${id}-erro`} className="erro-campo">
      {msg}
    </span>
  ) : null;
}

/** Depois de um envio recusado, leva o foco ao primeiro campo marcado como inválido. */
export function useFocoNoPrimeiroErro(form: RefObject<HTMLFormElement>, erros: ErrosCampo) {
  useEffect(() => {
    if (Object.keys(erros).length === 0) return;
    form.current?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus();
  }, [form, erros]);
}
