package br.com.gestao.operadores.web;

import br.com.gestao.operadores.dominio.*;
import br.com.gestao.operadores.seguranca.SupervisorLogado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Dashboard. O ponto central coberto aqui é a coerência entre o card
 * "Colaboradores avaliados" e a soma da distribuição por faixa (o donut): os dois
 * saem do mesmo payload e precisam bater sempre, senão a tela se contradiz.
 */
@ExtendWith(MockitoExtension.class)
class PainelServiceTest {

    private static final Long SUPERVISOR = 7L;
    private static final Long COLABORADOR = 10L;
    private static final Long COMP_ATIVA = 1L;
    private static final Long COMP_INATIVA = 2L;

    @Mock ColaboradorRepository colaboradores;
    @Mock CompetenciaRepository competencias;
    @Mock AvaliacaoRepository avaliacoes;
    @Mock AcaoPdiRepository acoesPdi;
    @Mock ConfiguracaoClassificacaoRepository configuracoes;
    @Mock SupervisorLogado logado;

    private PainelService service;

    @BeforeEach
    void setUp() {
        service = new PainelService(colaboradores, competencias, avaliacoes, acoesPdi,
            configuracoes, logado);
        when(logado.id()).thenReturn(SUPERVISOR);
        when(configuracoes.findById(SUPERVISOR)).thenReturn(Optional.empty()); // usa os limites padrão
    }

    private Colaborador colaboradorComId(Long id) {
        Colaborador c = mock(Colaborador.class);
        when(c.getId()).thenReturn(id);
        return c;
    }

    private Competencia competencia(Long id, String nome) {
        Competencia c = mock(Competencia.class);
        when(c.getId()).thenReturn(id);
        when(c.getNome()).thenReturn(nome);
        return c;
    }

    /** Monta o cenário: 1 colaborador ativo, as competências ativas informadas e uma avaliação. */
    private void cenario(List<Competencia> ativas, Set<NotaCompetencia> notas) {
        // Mock criado ANTES do when(...): criá-lo dentro do thenReturn deixaria esta
        // stubagem inacabada (UnfinishedStubbingException).
        Colaborador col = colaboradorComId(COLABORADOR);
        when(colaboradores.findBySupervisorIdAndAtivoTrueOrderByNomeAsc(SUPERVISOR))
            .thenReturn(List.of(col));
        when(competencias.findBySupervisorIdAndAtivaTrueOrderByNomeAsc(SUPERVISOR))
            .thenReturn(ativas);
        when(avaliacoes.findByColaboradorIdInOrderByDataAvaliacaoDesc(any()))
            .thenReturn(List.of(new Avaliacao(COLABORADOR, LocalDate.of(2026, 1, 15), null, notas)));
        when(avaliacoes.countByColaboradorIdIn(any())).thenReturn(1L);
        when(acoesPdi.countByColaboradorIdInAndStatus(any(), any())).thenReturn(0L);
        when(acoesPdi.countByColaboradorIdInAndStatusNot(any(), any())).thenReturn(0L);
        when(acoesPdi.countByColaboradorIdInAndStatusNotAndPrazoBefore(any(), any(), any()))
            .thenReturn(0L);
    }

    private static long somaDasFaixas(Dtos.DashboardResponse d) {
        return d.distribuicao().stream().mapToLong(Dtos.FaixaContagem::quantidade).sum();
    }

    @Test
    void avaliadoApenasEmCompetenciaInativa_naoContaComoAvaliado() {
        // A única nota do colaborador é de uma competência que não está mais ativa.
        cenario(List.of(competencia(COMP_ATIVA, "Comunicação")),
            Set.of(new NotaCompetencia(COMP_INATIVA, (short) 5)));

        Dtos.DashboardResponse d = service.dashboard();

        // Antes da correção isto era 1 (notaAtual.size()) enquanto o donut mostrava 0.
        assertThat(d.colaboradoresAvaliados()).isZero();
        assertThat(somaDasFaixas(d)).isZero();
        assertThat(d.mediaGeral()).isNull();
    }

    @Test
    void comNotaEmCompetenciaAtiva_contaUmaVezNaFaixaCerta() {
        cenario(List.of(competencia(COMP_ATIVA, "Comunicação")),
            Set.of(new NotaCompetencia(COMP_ATIVA, (short) 5)));

        Dtos.DashboardResponse d = service.dashboard();

        assertThat(d.colaboradoresAvaliados()).isEqualTo(1);
        assertThat(somaDasFaixas(d)).isEqualTo(1);
        assertThat(d.mediaGeral()).isEqualTo(5.0);
        // Média 5 com os limites padrão (2/3/4) cai em "Bom".
        assertThat(d.distribuicao()).filteredOn(f -> f.quantidade() > 0)
            .singleElement()
            .satisfies(f -> assertThat(f.classificacao().codigo()).isEqualTo(Classificacao.BOM.name()));
    }

    @Test
    void notaDeCompetenciaInativaNaoEntraNaMedia() {
        // Ativa = 2 e inativa = 5. A média precisa ser 2,00 (só a ativa), não 3,50.
        cenario(List.of(competencia(COMP_ATIVA, "Comunicação")),
            Set.of(new NotaCompetencia(COMP_ATIVA, (short) 2),
                   new NotaCompetencia(COMP_INATIVA, (short) 5)));

        Dtos.DashboardResponse d = service.dashboard();

        assertThat(d.mediaGeral()).isEqualTo(2.0);
        assertThat(d.colaboradoresAvaliados()).isEqualTo(1);
        assertThat(somaDasFaixas(d)).isEqualTo(1);
    }

    @Test
    void semColaboradores_naoConsultaAvaliacoesNemPdi() {
        when(colaboradores.findBySupervisorIdAndAtivoTrueOrderByNomeAsc(SUPERVISOR)).thenReturn(List.of());
        when(competencias.findBySupervisorIdAndAtivaTrueOrderByNomeAsc(SUPERVISOR)).thenReturn(List.of());

        Dtos.DashboardResponse d = service.dashboard();

        assertThat(d.colaboradoresAvaliados()).isZero();
        assertThat(somaDasFaixas(d)).isZero();
        assertThat(d.totalAvaliacoes()).isZero();
        verify(avaliacoes, never()).findByColaboradorIdInOrderByDataAvaliacaoDesc(any());
        verify(avaliacoes, never()).countByColaboradorIdIn(any());
    }
}
