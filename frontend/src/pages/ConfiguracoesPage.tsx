import { useEffect, useState, type FormEvent } from "react";
import { api, ApiError } from "../api/client";
import { useRequisicao } from "../hooks/useRequisicao";
import { Carregando, ErroBox } from "../components/estados";
import { useToast } from "../components/Toasts";
import type { Configuracao } from "../api/types";

export function ConfiguracoesPage() {
  const toast = useToast();
  const { dados, carregando, erro, recarregar } = useRequisicao(() => api.obterConfiguracao());
  const [form, setForm] = useState<Configuracao | null>(null);
  const [erroForm, setErroForm] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  // Copia os dados carregados para o formulário editável.
  useEffect(() => {
    if (dados) setForm(structuredClone(dados));
  }, [dados]);

  if (carregando) return <Carregando />;
  if (erro) return <ErroBox mensagem={erro} onTentar={recarregar} />;
  if (!form) return null;

  function setLimite(chave: "limiteCritico" | "limiteDesenvolver" | "limiteBom", valor: string) {
    setForm((f) => (f ? { ...f, [chave]: valor === "" ? NaN : Number(valor) } : f));
  }

  function setRotulo(nota: number, rotulo: string) {
    setForm((f) =>
      f ? { ...f, rotulos: f.rotulos.map((r) => (r.nota === nota ? { ...r, rotulo } : r)) } : f
    );
  }

  async function aoEnviar(e: FormEvent) {
    e.preventDefault();
    if (!form) return;
    setErroForm(null);
    const { limiteCritico, limiteDesenvolver, limiteBom } = form;
    if ([limiteCritico, limiteDesenvolver, limiteBom].some((n) => Number.isNaN(n))) {
      setErroForm("Preencha os três limites.");
      return;
    }
    if (!(limiteCritico < limiteDesenvolver && limiteDesenvolver < limiteBom)) {
      setErroForm("Os limites precisam ser crescentes: crítico < a desenvolver < bom.");
      return;
    }
    if (form.rotulos.some((r) => !r.rotulo.trim())) {
      setErroForm("Preencha o rótulo de todas as notas.");
      return;
    }
    setEnviando(true);
    try {
      const salvo = await api.salvarConfiguracao(form);
      setForm(structuredClone(salvo));
      toast.sucesso("Configuração salva.");
    } catch (err) {
      setErroForm(err instanceof ApiError ? err.message : "Erro ao salvar a configuração.");
    } finally {
      setEnviando(false);
    }
  }

  return (
    <>
      <div className="topo">
        <div>
          <h1>Configurações</h1>
          <p>Faixas de classificação e rótulos das notas usados no mapa e no dashboard.</p>
        </div>
      </div>

      <form onSubmit={aoEnviar}>
        {erroForm && (
          <div className="estado-erro" role="alert">
            {erroForm}
          </div>
        )}

        <section className="cartao ficha-secao">
          <h2>Faixas de classificação</h2>
          <p style={{ color: "var(--cor-texto-suave)", marginTop: 0 }}>
            Limites (de 1 a 5) que separam as faixas de uma média. Devem ser crescentes.
          </p>
          <div className="config-faixas">
            <div className="campo">
              <label htmlFor="lc">Crítico abaixo de</label>
              <input
                id="lc"
                type="number"
                step="0.01"
                min="1"
                max="5"
                value={Number.isNaN(form.limiteCritico) ? "" : form.limiteCritico}
                onChange={(e) => setLimite("limiteCritico", e.target.value)}
              />
            </div>
            <div className="campo">
              <label htmlFor="ld">A desenvolver abaixo de</label>
              <input
                id="ld"
                type="number"
                step="0.01"
                min="1"
                max="5"
                value={Number.isNaN(form.limiteDesenvolver) ? "" : form.limiteDesenvolver}
                onChange={(e) => setLimite("limiteDesenvolver", e.target.value)}
              />
            </div>
            <div className="campo">
              <label htmlFor="lb">Adequado abaixo de / Bom a partir de</label>
              <input
                id="lb"
                type="number"
                step="0.01"
                min="1"
                max="5"
                value={Number.isNaN(form.limiteBom) ? "" : form.limiteBom}
                onChange={(e) => setLimite("limiteBom", e.target.value)}
              />
            </div>
          </div>
        </section>

        <section className="cartao ficha-secao">
          <h2>Rótulos das notas</h2>
          <p style={{ color: "var(--cor-texto-suave)", marginTop: 0 }}>
            Nome de cada nota de 1 a 5 (aparece como dica ao passar o mouse no mapa).
          </p>
          <div className="config-rotulos">
            {form.rotulos.map((r) => (
              <div className="campo" key={r.nota}>
                <label htmlFor={`rot-${r.nota}`}>Nota {r.nota}</label>
                <input
                  id={`rot-${r.nota}`}
                  value={r.rotulo}
                  maxLength={40}
                  onChange={(e) => setRotulo(r.nota, e.target.value)}
                />
              </div>
            ))}
          </div>
        </section>

        <div className="modal-acoes">
          <button type="submit" className="btn btn-primario" disabled={enviando}>
            {enviando ? "Salvando…" : "Salvar configuração"}
          </button>
        </div>
      </form>
    </>
  );
}
