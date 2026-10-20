package br.com.gestao.operadores.web;

import br.com.gestao.operadores.dominio.*;
import br.com.gestao.operadores.seguranca.SupervisorLogado;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * Mapa de competências e dashboard. Ambos partem da mesma "grade": para cada colaborador
 * ativo e cada competência ativa, a nota mais recente (a avaliação mais recente que pontuou
 * aquela competência). Tudo é filtrado pelo supervisor logado.
 */
@Service
public class PainelService {

    private final ColaboradorRepository colaboradores;
    private final CompetenciaRepository competencias;
    private final AvaliacaoRepository avaliacoes;
    private final AcaoPdiRepository acoesPdi;
    private final ConfiguracaoClassificacaoRepository configuracoes;
    private final SupervisorLogado logado;

    public PainelService(ColaboradorRepository colaboradores, CompetenciaRepository competencias,
                         AvaliacaoRepository avaliacoes, AcaoPdiRepository acoesPdi,
                         ConfiguracaoClassificacaoRepository configuracoes, SupervisorLogado logado) {
        this.colaboradores = colaboradores;
        this.competencias = competencias;
        this.avaliacoes = avaliacoes;
        this.acoesPdi = acoesPdi;
        this.configuracoes = configuracoes;
        this.logado = logado;
    }

    /** colaboradores e competências ativos + nota atual[colaboradorId][competenciaId]. */
    private record Grade(List<Colaborador> colaboradores, List<Competencia> competencias,
                         Map<Long, Map<Long, Short>> notaAtual, ConfiguracaoClassificacao cfg) {}

    private Grade montarGrade() {
        Long supervisor = logado.id();
        List<Colaborador> cols = colaboradores.findBySupervisorIdAndAtivoTrueOrderByNomeAsc(supervisor);
        List<Competencia> comps = competencias.findBySupervisorIdAndAtivaTrueOrderByNomeAsc(supervisor);
        ConfiguracaoClassificacao cfg = configuracoes.findById(supervisor)
            .orElseGet(ConfiguracaoClassificacao::padrao);

        Map<Long, Map<Long, Short>> notaAtual = new HashMap<>();
        if (!cols.isEmpty()) {
            List<Long> ids = cols.stream().map(Colaborador::getId).toList();
            // Ordenado por data desc: a primeira nota vista de cada competência é a mais recente.
            for (Avaliacao a : avaliacoes.findByColaboradorIdInOrderByDataAvaliacaoDesc(ids)) {
                Map<Long, Short> doColaborador =
                    notaAtual.computeIfAbsent(a.getColaboradorId(), k -> new HashMap<>());
                for (NotaCompetencia n : a.getNotas()) {
                    doColaborador.putIfAbsent(n.getCompetenciaId(), n.getNota());
                }
            }
        }
        return new Grade(cols, comps, notaAtual, cfg);
    }

    @Transactional(readOnly = true)
    public Dtos.MapaResponse mapa() {
        Grade g = montarGrade();

        List<Dtos.MapaCompetencia> colunas = g.competencias().stream()
            .map(c -> new Dtos.MapaCompetencia(c.getId(), c.getNome(), c.getTipo(),
                c.getNivelEsperado(), mediaDaCompetencia(g, c.getId())))
            .toList();

        List<Dtos.MapaLinha> linhas = g.colaboradores().stream().map(col -> {
            Map<Long, Short> doColaborador = g.notaAtual().getOrDefault(col.getId(), Map.of());
            List<Short> notasDaLinha = new ArrayList<>();
            List<Dtos.MapaCelula> celulas = new ArrayList<>(g.competencias().size());
            for (Competencia c : g.competencias()) {
                Short nota = doColaborador.get(c.getId());
                Classificacao classif = nota == null ? null : Classificacao.de(nota, g.cfg());
                celulas.add(new Dtos.MapaCelula(c.getId(),
                    nota == null ? null : nota.intValue(), Dtos.ClassificacaoDto.de(classif)));
                if (nota != null) notasDaLinha.add(nota);
            }
            Double media = media(notasDaLinha);
            Classificacao classifLinha = media == null ? null : Classificacao.de(media, g.cfg());
            return new Dtos.MapaLinha(col.getId(), col.getNome(), col.getCargo(), col.getSetor(),
                media, Dtos.ClassificacaoDto.de(classifLinha), celulas);
        }).toList();

        return new Dtos.MapaResponse(colunas, linhas);
    }

    @Transactional(readOnly = true)
    public Dtos.DashboardResponse dashboard() {
        Grade g = montarGrade();

        // Distribuição por COLABORADOR: cada colaborador avaliado cai numa única faixa,
        // pela sua média (mesmo critério da coluna "Média" do mapa). Assim o gráfico conta
        // pessoas e o clique p/ filtrar o mapa por faixa sempre encontra os colaboradores.
        List<Short> todasAsNotas = new ArrayList<>();
        Map<Classificacao, Long> porFaixa = new EnumMap<>(Classificacao.class);
        for (Colaborador col : g.colaboradores()) {
            Map<Long, Short> doColaborador = g.notaAtual().getOrDefault(col.getId(), Map.of());
            // conta só competências ainda ativas (as que aparecem no mapa)
            List<Short> notasDoColaborador = new ArrayList<>();
            for (Competencia c : g.competencias()) {
                Short nota = doColaborador.get(c.getId());
                if (nota != null) {
                    notasDoColaborador.add(nota);
                    todasAsNotas.add(nota);
                }
            }
            Double mediaColaborador = media(notasDoColaborador);
            if (mediaColaborador != null) {
                porFaixa.merge(Classificacao.de(mediaColaborador, g.cfg()), 1L, Long::sum);
            }
        }

        List<Dtos.FaixaContagem> distribuicao = new ArrayList<>();
        for (Classificacao faixa : Classificacao.values()) {
            distribuicao.add(new Dtos.FaixaContagem(
                Dtos.ClassificacaoDto.de(faixa), porFaixa.getOrDefault(faixa, 0L)));
        }

        List<Dtos.CompetenciaMedia> criticas = g.competencias().stream()
            .map(c -> new Dtos.CompetenciaMedia(c.getId(), c.getNome(), mediaDaCompetencia(g, c.getId())))
            .filter(cm -> cm.media() != null)
            .sorted(Comparator.comparing(Dtos.CompetenciaMedia::media))
            .limit(5)
            .toList();

        List<Long> ids = g.colaboradores().stream().map(Colaborador::getId).toList();
        long totalAvaliacoes = ids.isEmpty() ? 0 : avaliacoes.countByColaboradorIdIn(ids);

        long pdiConcluidas = ids.isEmpty() ? 0
            : acoesPdi.countByColaboradorIdInAndStatus(ids, StatusPdi.CONCLUIDO);
        long pdiAbertas = ids.isEmpty() ? 0
            : acoesPdi.countByColaboradorIdInAndStatusNot(ids, StatusPdi.CONCLUIDO);
        long pdiAtrasadas = ids.isEmpty() ? 0
            : acoesPdi.countByColaboradorIdInAndStatusNotAndPrazoBefore(ids, StatusPdi.CONCLUIDO, LocalDate.now());

        return new Dtos.DashboardResponse(
            g.colaboradores().size(),
            g.competencias().size(),
            totalAvaliacoes,
            g.notaAtual().size(),
            media(todasAsNotas),
            distribuicao,
            criticas,
            pdiAbertas,
            pdiAtrasadas,
            pdiConcluidas);
    }

    private static Double mediaDaCompetencia(Grade g, Long competenciaId) {
        List<Short> notas = new ArrayList<>();
        for (Map<Long, Short> doColaborador : g.notaAtual().values()) {
            Short nota = doColaborador.get(competenciaId);
            if (nota != null) notas.add(nota);
        }
        return media(notas);
    }

    /** Média arredondada a 2 casas, ou null quando não há notas. */
    private static Double media(List<Short> notas) {
        if (notas.isEmpty()) return null;
        double soma = 0;
        for (short n : notas) soma += n;
        return Math.round((soma / notas.size()) * 100.0) / 100.0;
    }
}
