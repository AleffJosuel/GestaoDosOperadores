import { useCallback, useEffect, useState } from "react";
import { ApiError } from "../api/client";

interface Estado<T> {
  dados: T | null;
  carregando: boolean;
  erro: string | null;
  recarregar: () => void;
}

/** Executa uma chamada assíncrona ao montar (e quando `deps` mudam), expondo estado de UI. */
export function useRequisicao<T>(fn: () => Promise<T>, deps: unknown[] = []): Estado<T> {
  const [dados, setDados] = useState<T | null>(null);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);

  // A função muda a cada render; fixamos sua identidade pelas deps informadas.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const executar = useCallback(fn, deps);

  const recarregar = useCallback(() => {
    let ativo = true;
    setCarregando(true);
    setErro(null);
    executar()
      .then((d) => ativo && setDados(d))
      .catch((e) => ativo && setErro(e instanceof ApiError ? e.message : "Erro inesperado"))
      .finally(() => ativo && setCarregando(false));
    return () => {
      ativo = false;
    };
  }, [executar]);

  useEffect(() => {
    const cancelar = recarregar();
    return cancelar;
  }, [recarregar]);

  return { dados, carregando, erro, recarregar };
}
