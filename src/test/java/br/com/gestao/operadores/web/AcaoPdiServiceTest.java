package br.com.gestao.operadores.web;

import br.com.gestao.operadores.dominio.*;
import br.com.gestao.operadores.seguranca.SupervisorLogado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Comportamento e autorização do serviço de PDI, com repositórios e colaboradores simulados.
 */
@ExtendWith(MockitoExtension.class)
class AcaoPdiServiceTest {

    private static final Long SUPERVISOR = 7L;
    private static final Long COLABORADOR = 10L;
    private static final Long COMPETENCIA = 3L;
    private static final Long AVALIACAO = 20L;

    @Mock AcaoPdiRepository acoes;
    @Mock CompetenciaRepository competencias;
    @Mock AvaliacaoRepository avaliacoes;
    @Mock ColaboradorService colaboradorService;
    @Mock SupervisorLogado logado;

    @InjectMocks AcaoPdiService service;

    private Colaborador colaborador;

    @BeforeEach
    void setUp() {
        colaborador = new Colaborador(SUPERVISOR, "1001", "Fulano", "OPERADOR", "Injeção", null, null);
    }

    private Dtos.PdiRequest req(Long competenciaId, Long avaliacaoId, StatusPdi status) {
        return new Dtos.PdiRequest(competenciaId, avaliacaoId, "Treinar comunicação",
            null, null, null, null, status, null, null, null);
    }

    private HttpStatusCode statusDe(Throwable t) {
        return ((ResponseStatusException) t).getStatusCode();
    }

    @Test
    void criar_comDadosValidos_salva() {
        when(colaboradorService.buscar(COLABORADOR)).thenReturn(colaborador);
        when(logado.id()).thenReturn(SUPERVISOR);
        when(competencias.findByIdAndSupervisorId(COMPETENCIA, SUPERVISOR))
            .thenReturn(Optional.of(mock(Competencia.class)));
        when(avaliacoes.existsByIdAndColaboradorId(AVALIACAO, COLABORADOR)).thenReturn(true);
        when(acoes.save(any(AcaoPdi.class))).thenAnswer(inv -> inv.getArgument(0));

        service.criar(COLABORADOR, req(COMPETENCIA, AVALIACAO, null));

        ArgumentCaptor<AcaoPdi> cap = ArgumentCaptor.forClass(AcaoPdi.class);
        verify(acoes).save(cap.capture());
        AcaoPdi a = cap.getValue();
        assertThat(a.getColaboradorId()).isEqualTo(COLABORADOR);
        assertThat(a.getCompetenciaId()).isEqualTo(COMPETENCIA);
        assertThat(a.getStatus()).isEqualTo(StatusPdi.NAO_INICIADO); // default quando não informado
        assertThat(a.getDataConclusao()).isNull();
    }

    @Test
    void criar_semCompetenciaOuAvaliacao_retorna422() {
        when(colaboradorService.buscar(COLABORADOR)).thenReturn(colaborador);

        assertThatThrownBy(() -> service.criar(COLABORADOR, req(null, AVALIACAO, null)))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(statusDe(e)).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));

        verify(acoes, never()).save(any());
    }

    @Test
    void criar_comCompetenciaDeOutroSupervisor_retorna404() {
        when(colaboradorService.buscar(COLABORADOR)).thenReturn(colaborador);
        when(logado.id()).thenReturn(SUPERVISOR);
        when(competencias.findByIdAndSupervisorId(COMPETENCIA, SUPERVISOR)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criar(COLABORADOR, req(COMPETENCIA, AVALIACAO, null)))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(statusDe(e)).isEqualTo(HttpStatus.NOT_FOUND));

        verify(acoes, never()).save(any());
    }

    @Test
    void criar_comAvaliacaoDeOutroColaborador_retorna404() {
        when(colaboradorService.buscar(COLABORADOR)).thenReturn(colaborador);
        when(logado.id()).thenReturn(SUPERVISOR);
        when(competencias.findByIdAndSupervisorId(COMPETENCIA, SUPERVISOR))
            .thenReturn(Optional.of(mock(Competencia.class)));
        when(avaliacoes.existsByIdAndColaboradorId(AVALIACAO, COLABORADOR)).thenReturn(false);

        assertThatThrownBy(() -> service.criar(COLABORADOR, req(COMPETENCIA, AVALIACAO, null)))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(statusDe(e)).isEqualTo(HttpStatus.NOT_FOUND));

        verify(acoes, never()).save(any());
    }

    @Test
    void criar_concluido_defineDataConclusao() {
        when(colaboradorService.buscar(COLABORADOR)).thenReturn(colaborador);
        when(logado.id()).thenReturn(SUPERVISOR);
        when(competencias.findByIdAndSupervisorId(COMPETENCIA, SUPERVISOR))
            .thenReturn(Optional.of(mock(Competencia.class)));
        when(avaliacoes.existsByIdAndColaboradorId(AVALIACAO, COLABORADOR)).thenReturn(true);
        when(acoes.save(any(AcaoPdi.class))).thenAnswer(inv -> inv.getArgument(0));

        service.criar(COLABORADOR, req(COMPETENCIA, AVALIACAO, StatusPdi.CONCLUIDO));

        ArgumentCaptor<AcaoPdi> cap = ArgumentCaptor.forClass(AcaoPdi.class);
        verify(acoes).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(StatusPdi.CONCLUIDO);
        assertThat(cap.getValue().getDataConclusao()).isNotNull(); // concluída sempre tem data
    }

    @Test
    void buscar_inexistente_retorna404() {
        when(acoes.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscar(1L))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(statusDe(e)).isEqualTo(HttpStatus.NOT_FOUND));

        verify(colaboradorService, never()).buscar(eq(COLABORADOR));
    }
}
