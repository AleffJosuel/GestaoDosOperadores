import { useMemo, useState, type FormEvent } from "react";
import { api, ApiError } from "../api/client";
import { useRequisicao } from "../hooks/useRequisicao";
import { Carregando, ErroBox, Vazio } from "../components/estados";
import { Modal } from "../components/Modal";
import type { NotaInput } from "../api/types";
import { hoje } from "../datas";

export function AvaliacaoModal({
  colaboradorId,
  colaboradorNome,
  onFechar,
  onSalvo,
}: {
  colaboradorId: number;
  colaboradorNome: string;
  onFechar: () => void;
  onSalvo: () => void;
}) {
  const competencias = useRequisicao(() => api.listarCompetencias(false), []);
  const [data, setData] = useState(hoje());
  const [observacao, setObservacao] = useState("");
  const [notas, setNotas] = useState<Record<number, number>>({});
  const [erro, setErro] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  const totalEscolhidas = useMemo(() => Object.keys(notas).length, [notas]);

  function definirNota(competenciaId: number, valor: string) {
    setNotas((atual) => {
      const copia = { ...atual };
      if (valor === "") delete copia[competenciaId];
      else copia[competenciaId] = Number(valor);
      return copia;
    });
  }

  async function aoEnviar(e: FormEvent) {
    e.preventDefault();
    setErro(null);
    if (totalEscolhidas === 0) {
      setErro("Dê nota a pelo menos uma competência.");
      return;
    }
    const listaNotas: NotaInput[] = Object.entries(notas).map(([id, nota]) => ({
      competenciaId: Number(id),
      nota,
    }));
    setEnviando(true);
    try {
      await api.criarAvaliacao(colaboradorId, {
        dataAvaliacao: data,
        observacao: observacao || null,
        notas: listaNotas,
      });
      onSalvo();
    } catch (err) {
      setErro(err instanceof ApiError ? err.message : "Erro ao salvar a avaliação.");
    } finally {
      setEnviando(false);
    }
  }

  return (
    <Modal titulo={`Avaliar — ${colaboradorNome}`} onFechar={onFechar}>
      <form onSubmit={aoEnviar}>
        {erro && (
          <div className="estado-erro" role="alert">
            {erro}
          </div>
        )}

        <div className="campo">
          <label htmlFor="data">Data da avaliação</label>
          <input
            id="data"
            type="date"
            max={hoje()}
            value={data}
            onChange={(e) => setData(e.target.value)}
            required
          />
        </div>

        <fieldset style={{ border: "none", padding: 0, margin: 0 }}>
          <legend style={{ fontWeight: 600, marginBottom: 6 }}>
            Notas por competência (1–5)
          </legend>
          {competencias.carregando ? (
            <Carregando texto="Carregando competências…" />
          ) : competencias.erro ? (
            <ErroBox mensagem={competencias.erro} onTentar={competencias.recarregar} />
          ) : !competencias.dados || competencias.dados.length === 0 ? (
            <Vazio texto="Cadastre competências ativas antes de avaliar." />
          ) : (
            <div className="tabela-wrap">
              <table className="tabela">
                <thead>
                  <tr>
                    <th>Competência</th>
                    <th>Nota</th>
                  </tr>
                </thead>
                <tbody>
                  {competencias.dados.map((c) => (
                    <tr key={c.id}>
                      <td style={{ whiteSpace: "normal" }}>{c.nome}</td>
                      <td>
                        <select
                          aria-label={`Nota de ${c.nome}`}
                          value={notas[c.id] ?? ""}
                          onChange={(e) => definirNota(c.id, e.target.value)}
                        >
                          <option value="">—</option>
                          {[1, 2, 3, 4, 5].map((n) => (
                            <option key={n} value={n}>
                              {n}
                            </option>
                          ))}
                        </select>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </fieldset>

        <div className="campo" style={{ marginTop: 12 }}>
          <label htmlFor="obs">Observação (opcional)</label>
          <textarea
            id="obs"
            rows={2}
            value={observacao}
            onChange={(e) => setObservacao(e.target.value)}
          />
        </div>

        <div className="modal-acoes">
          <button type="button" className="btn btn-sutil" onClick={onFechar}>
            Cancelar
          </button>
          <button type="submit" className="btn btn-primario" disabled={enviando}>
            {enviando ? "Salvando…" : `Salvar avaliação (${totalEscolhidas})`}
          </button>
        </div>
      </form>
    </Modal>
  );
}
