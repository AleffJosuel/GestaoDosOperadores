import { useRef, useState } from "react";
import { Modal } from "./Modal";
import { ErroBox } from "./estados";
import { ApiError } from "../api/client";
import type { ImportacaoResultado } from "../api/types";

export interface ModeloCsv {
  /** Cabeçalho do arquivo-modelo, na ordem. Primeiras colunas são as obrigatórias. */
  cabecalho: string[];
  /** Uma linha de exemplo para o usuário ver o formato esperado. */
  exemplo: string[];
  /** Nome sugerido do arquivo baixado (sem extensão). */
  nomeArquivo: string;
}

interface Props {
  titulo: string;
  modelo: ModeloCsv;
  enviar: (arquivo: File) => Promise<ImportacaoResultado>;
  onFechar: () => void;
  /** Chamado quando a importação cria ao menos um registro (para recarregar a lista). */
  onImportado: () => void;
}

/** Escapa um campo CSV: coloca aspas se houver separador, aspas ou quebra de linha. */
function campoCsv(valor: string, sep: string): string {
  if (valor.includes(sep) || valor.includes('"') || valor.includes("\n")) {
    return `"${valor.replace(/"/g, '""')}"`;
  }
  return valor;
}

export function ImportModal({ titulo, modelo, enviar, onFechar, onImportado }: Props) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [arquivo, setArquivo] = useState<File | null>(null);
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState<string | null>(null);
  const [resultado, setResultado] = useState<ImportacaoResultado | null>(null);

  function baixarModelo() {
    const sep = ";"; // amigável para Excel em pt-BR
    const linhas = [modelo.cabecalho, modelo.exemplo]
      .map((l) => l.map((c) => campoCsv(c, sep)).join(sep))
      .join("\r\n");
    // BOM para o Excel reconhecer o UTF-8 (acentos).
    const blob = new Blob(["﻿" + linhas], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `${modelo.nomeArquivo}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }

  async function aoEnviar() {
    if (!arquivo) return;
    setErro(null);
    setResultado(null);
    setEnviando(true);
    try {
      const r = await enviar(arquivo);
      setResultado(r);
      if (r.criados > 0) onImportado();
    } catch (e) {
      setErro(e instanceof ApiError ? e.message : "Falha ao importar o arquivo.");
    } finally {
      setEnviando(false);
    }
  }

  return (
    <Modal titulo={titulo} onFechar={onFechar}>
      {!resultado ? (
        <>
          <p className="import-ajuda">
            Envie um arquivo <strong>.csv</strong> (separado por vírgula ou ponto e vírgula).
            Colunas obrigatórias: <strong>{modelo.cabecalho.join(", ")}</strong>. A primeira linha
            deve ser o cabeçalho.
          </p>

          <button type="button" className="btn btn-sutil btn-sm" onClick={baixarModelo}>
            Baixar modelo CSV
          </button>

          <div className="campo" style={{ marginTop: "1rem" }}>
            <label htmlFor="csv">Arquivo CSV</label>
            <input
              id="csv"
              ref={inputRef}
              type="file"
              accept=".csv,text/csv"
              onChange={(e) => {
                setArquivo(e.target.files?.[0] ?? null);
                setErro(null);
              }}
            />
          </div>

          {erro && (
            <ErroBox mensagem={erro} />
          )}

          <div className="modal-acoes">
            <button type="button" className="btn btn-sutil" onClick={onFechar}>
              Cancelar
            </button>
            <button
              type="button"
              className="btn btn-primario"
              disabled={!arquivo || enviando}
              onClick={aoEnviar}
            >
              {enviando ? "Importando…" : "Importar"}
            </button>
          </div>
        </>
      ) : (
        <>
          <div className="import-resumo">
            <span className="selo selo-ativo">{resultado.criados} criado(s)</span>{" "}
            {resultado.falhas > 0 && (
              <span className="selo selo-inativo">{resultado.falhas} falha(s)</span>
            )}{" "}
            <span className="import-total">de {resultado.total} linha(s)</span>
          </div>

          {resultado.linhas.some((l) => !l.ok) && (
            <div className="tabela-wrap cartao" style={{ maxHeight: 280, overflow: "auto" }}>
              <table className="tabela">
                <thead>
                  <tr>
                    <th>Linha</th>
                    <th>Situação</th>
                    <th>Mensagem</th>
                  </tr>
                </thead>
                <tbody>
                  {resultado.linhas
                    .filter((l) => !l.ok)
                    .map((l) => (
                      <tr key={l.linha}>
                        <td>{l.linha}</td>
                        <td>
                          <span className="selo selo-inativo">Falha</span>
                        </td>
                        <td style={{ whiteSpace: "normal" }}>{l.mensagem}</td>
                      </tr>
                    ))}
                </tbody>
              </table>
            </div>
          )}

          <div className="modal-acoes">
            <button type="button" className="btn btn-primario" onClick={onFechar}>
              Fechar
            </button>
          </div>
        </>
      )}
    </Modal>
  );
}
