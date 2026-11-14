import { useState } from "react";
import { ApiError, type ArquivoBaixado } from "../api/client";
import { useToast } from "./Toasts";
import { Icone } from "./Icone";

/**
 * Entrega ao navegador um arquivo que já está na memória.
 *
 * <p>O caminho do `<a download>` com URL de blob é o que permite salvar uma resposta que exigiu
 * cabeçalho de autenticação — não há como pedir ao navegador que baixe o endereço direto sem
 * perder o token.
 */
function salvarArquivo({ blob, nome }: ArquivoBaixado) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = nome;
  document.body.appendChild(link);
  link.click();
  link.remove();
  // Sem revogar, o blob fica retido na memória da aba até ela ser fechada.
  URL.revokeObjectURL(url);
}

type Props = {
  /** Função do `api` que busca o arquivo (ex.: `api.exportarMapa`). */
  exportar: () => Promise<ArquivoBaixado>;
  /** O que foi exportado, para a mensagem de erro dizer algo útil. */
  oQue: string;
  rotulo?: string;
};

/** Botão de exportar com estado de espera: o arquivo é montado no servidor e pode demorar. */
export function BotaoExportar({ exportar, oQue, rotulo = "Exportar CSV" }: Props) {
  const [baixando, setBaixando] = useState(false);
  const toast = useToast();

  async function clicar() {
    setBaixando(true);
    try {
      salvarArquivo(await exportar());
    } catch (e) {
      toast.erro(e instanceof ApiError ? e.message : `Erro ao exportar ${oQue}.`);
    } finally {
      setBaixando(false);
    }
  }

  return (
    <button className="btn btn-sutil" onClick={clicar} disabled={baixando}>
      <Icone nome="baixar" />
      {baixando ? "Exportando…" : rotulo}
    </button>
  );
}
