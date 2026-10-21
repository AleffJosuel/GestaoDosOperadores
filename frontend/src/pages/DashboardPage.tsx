import { Link, useNavigate } from "react-router-dom";
import { api } from "../api/client";
import { useRequisicao } from "../hooks/useRequisicao";
import { Carregando, ErroBox } from "../components/estados";
import { DonutFaixas } from "../components/DonutFaixas";
import { Icone, type NomeIcone } from "../components/Icone";

function fmt(media: number | null): string {
  return media == null ? "—" : media.toFixed(2).replace(".", ",");
}

export function DashboardPage() {
  const { dados, carregando, erro, recarregar } = useRequisicao(() => api.dashboard());
  const navegar = useNavigate();

  if (carregando) return <Carregando />;
  if (erro) return <ErroBox mensagem={erro} onTentar={recarregar} />;
  if (!dados) return null;

  const totalFaixas = dados.distribuicao.reduce((s, f) => s + f.quantidade, 0);

  return (
    <>
      <div className="topo">
        <div>
          <h1>Dashboard</h1>
          <p>Visão geral da equipe a partir das avaliações mais recentes.</p>
        </div>
      </div>

      <div className="grade-cartoes">
        <Metrica
          valor={dados.colaboradoresAtivos}
          rotulo="Colaboradores ativos"
          icone="usuarios"
          para="/colaboradores"
        />
        <Metrica
          valor={dados.competenciasAtivas}
          rotulo="Competências ativas"
          icone="alvo"
          para="/competencias"
        />
        <Metrica
          valor={dados.colaboradoresAvaliados}
          rotulo="Colaboradores avaliados"
          contexto={`de ${dados.colaboradoresAtivos} ativos`}
          icone="sucesso"
          para="/mapa"
        />
        <Metrica valor={dados.totalAvaliacoes} rotulo="Avaliações registradas" icone="ficha" />
        <Metrica
          valor={fmt(dados.mediaGeral)}
          rotulo="Média geral"
          contexto="escala de 1 a 5"
          icone="estrela"
          para="/mapa"
        />
      </div>

      <div className="painel-duplo">
        <section className="cartao bloco" aria-labelledby="titulo-faixas">
          <div className="bloco-topo">
            <h2 id="titulo-faixas">Distribuição por faixa</h2>
            <span className="bloco-dica">Clique numa faixa para ver quem está nela</span>
          </div>
          {totalFaixas === 0 ? (
            <p className="texto-suave">Ainda não há notas registradas.</p>
          ) : (
            <DonutFaixas
              distribuicao={dados.distribuicao}
              onSelecionar={(codigo) => navegar(`/mapa?faixa=${codigo}`)}
            />
          )}
        </section>

        <section className="cartao bloco" aria-labelledby="titulo-criticas">
          <div className="bloco-topo">
            <h2 id="titulo-criticas">Competências mais críticas</h2>
            <Link className="link-acao" to="/mapa">
              Ver mapa <Icone nome="direita" />
            </Link>
          </div>
          {dados.competenciasCriticas.length === 0 ? (
            <p className="texto-suave">Sem dados suficientes.</p>
          ) : (
            <table className="tabela tabela-compacta">
              <caption className="sr-only">Menores médias por competência, de 1 a 5</caption>
              <thead>
                <tr>
                  <th>Competência</th>
                  <th className="col-num">Média (1–5)</th>
                </tr>
              </thead>
              <tbody>
                {dados.competenciasCriticas.map((c) => (
                  <tr key={c.id}>
                    <td style={{ whiteSpace: "normal" }}>{c.nome}</td>
                    <td className="col-num">
                      <span className="media-barra">
                        <span className="media-trilho" aria-hidden="true">
                          <span
                            className="media-preenche"
                            style={{ width: `${((c.media ?? 0) / 5) * 100}%` }}
                          />
                        </span>
                        {fmt(c.media)}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>
      </div>

      <div className="secao-topo">
        <h2>Acompanhamento (PDI)</h2>
        <Link className="link-acao" to="/pdi">
          Ver PDI <Icone nome="direita" />
        </Link>
      </div>
      <div className="grade-cartoes grade-cartoes-3">
        <Metrica
          valor={dados.pdiAbertas}
          rotulo="Ações abertas"
          icone="prancheta"
          para="/pdi?filtro=ABERTAS"
        />
        <Metrica
          valor={dados.pdiAtrasadas}
          rotulo="Ações atrasadas"
          contexto={dados.pdiAtrasadas > 0 ? "prazo vencido, ainda não concluídas" : "nenhuma com prazo vencido"}
          icone="aviso"
          alerta={dados.pdiAtrasadas > 0}
          para="/pdi?filtro=ATRASADAS"
        />
        <Metrica
          valor={dados.pdiConcluidas}
          rotulo="Ações concluídas"
          icone="sucesso"
          para="/pdi?filtro=CONCLUIDAS"
        />
      </div>
    </>
  );
}

/** Cartão de indicador; com `para`, o cartão inteiro leva à lista que explica o número. */
function Metrica({
  valor,
  rotulo,
  contexto,
  icone,
  alerta,
  para,
}: {
  valor: number | string;
  rotulo: string;
  contexto?: string;
  icone: NomeIcone;
  alerta?: boolean;
  para?: string;
}) {
  const conteudo = (
    <>
      <div className="metrica-cabeca">
        <span className="rotulo">{rotulo}</span>
        <Icone nome={icone} className="metrica-icone" />
      </div>
      <div className="valor">{valor}</div>
      {contexto && <div className="contexto">{contexto}</div>}
    </>
  );
  const classe = `cartao cartao-metrica${alerta ? " metrica-alerta" : ""}`;
  return para ? (
    <Link className={`${classe} metrica-link`} to={para}>
      {conteudo}
    </Link>
  ) : (
    <div className={classe}>{conteudo}</div>
  );
}
