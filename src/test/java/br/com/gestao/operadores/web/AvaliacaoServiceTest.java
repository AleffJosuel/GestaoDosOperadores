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

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Comportamento e autorização do serviço de avaliações, com repositórios e colaboradores
 * simulados. Não toca o banco: foca nas regras (posse, competência válida, duplicidade).
 */
@ExtendWith(MockitoExtension.class)
class AvaliacaoServiceTest {

    private static final Long SUPERVISOR = 7L;
    private static final Long COLABORADOR = 10L;
    private static final LocalDate DATA = LocalDate.of(2026, 1, 15);

    @Mock AvaliacaoRepository avaliacoes;
    @Mock CompetenciaRepository competencias;
    @Mock ColaboradorService colaboradorService;
    @Mock SupervisorLogado logado;

    @InjectMocks AvaliacaoService service;

    private Colaborador colaboradorAtivo;

    @BeforeEach
    void setUp() {
        colaboradorAtivo = new Colaborador(SUPERVISOR, "1001", "Fulano", "OPERADOR", "Injeção", null, null);
    }

    private Dtos.AvaliacaoRequest pedido(Dtos.NotaRequest... notas) {
        return new Dtos.AvaliacaoRequest(DATA, "observação", List.of(notas));
    }

    private Competencia competenciaComId(long id) {
        Competencia c = mock(Competencia.class);
        when(c.getId()).thenReturn(id);
        return c;
    }

    private HttpStatusCode statusDe(Throwable t) {
        return ((ResponseStatusException) t).getStatusCode();
    }

    @Test
    void criar_comDadosValidos_salvaComAsNotas() {
        when(colaboradorService.buscar(COLABORADOR)).thenReturn(colaboradorAtivo);
        when(logado.id()).thenReturn(SUPERVISOR);
        when(competencias.findBySupervisorIdAndIdIn(eq(SUPERVISOR), any()))
            .thenReturn(List.of(competenciaComId(1L), competenciaComId(2L)));
        when(avaliacoes.existsByColaboradorIdAndDataAvaliacao(COLABORADOR, DATA)).thenReturn(false);
        when(avaliacoes.save(any(Avaliacao.class))).thenAnswer(inv -> inv.getArgument(0));

        service.criar(COLABORADOR, pedido(new Dtos.NotaRequest(1L, 5), new Dtos.NotaRequest(2L, 3)));

        ArgumentCaptor<Avaliacao> capturada = ArgumentCaptor.forClass(Avaliacao.class);
        verify(avaliacoes).save(capturada.capture());
        Avaliacao a = capturada.getValue();
        assertThat(a.getColaboradorId()).isEqualTo(COLABORADOR);
        assertThat(a.getNotas()).hasSize(2);
    }

    @Test
    void criar_paraColaboradorInativo_retornaConflito() {
        colaboradorAtivo.definirAtivo(false);
        when(colaboradorService.buscar(COLABORADOR)).thenReturn(colaboradorAtivo);

        assertThatThrownBy(() -> service.criar(COLABORADOR, pedido(new Dtos.NotaRequest(1L, 5))))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(statusDe(e)).isEqualTo(HttpStatus.CONFLICT));

        verify(avaliacoes, never()).save(any());
    }

    @Test
    void criar_comCompetenciaDeOutroSupervisor_retorna404() {
        when(colaboradorService.buscar(COLABORADOR)).thenReturn(colaboradorAtivo);
        when(logado.id()).thenReturn(SUPERVISOR);
        // Pediu 2 competências, mas só 1 pertence ao supervisor.
        when(competencias.findBySupervisorIdAndIdIn(eq(SUPERVISOR), any()))
            .thenReturn(List.of(competenciaComId(1L)));

        assertThatThrownBy(() -> service.criar(COLABORADOR,
                pedido(new Dtos.NotaRequest(1L, 5), new Dtos.NotaRequest(99L, 4))))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(statusDe(e)).isEqualTo(HttpStatus.NOT_FOUND));

        verify(avaliacoes, never()).save(any());
    }

    @Test
    void criar_comCompetenciaRepetida_retorna422() {
        when(colaboradorService.buscar(COLABORADOR)).thenReturn(colaboradorAtivo);

        assertThatThrownBy(() -> service.criar(COLABORADOR,
                pedido(new Dtos.NotaRequest(1L, 5), new Dtos.NotaRequest(1L, 4))))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(statusDe(e)).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));

        verify(competencias, never()).findBySupervisorIdAndIdIn(anyLong(), any());
        verify(avaliacoes, never()).save(any());
    }

    @Test
    void criar_comDataJaAvaliada_retornaConflito() {
        when(colaboradorService.buscar(COLABORADOR)).thenReturn(colaboradorAtivo);
        when(logado.id()).thenReturn(SUPERVISOR);
        when(competencias.findBySupervisorIdAndIdIn(eq(SUPERVISOR), any()))
            .thenReturn(List.of(competenciaComId(1L)));
        when(avaliacoes.existsByColaboradorIdAndDataAvaliacao(COLABORADOR, DATA)).thenReturn(true);

        assertThatThrownBy(() -> service.criar(COLABORADOR, pedido(new Dtos.NotaRequest(1L, 5))))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(statusDe(e)).isEqualTo(HttpStatus.CONFLICT));

        verify(avaliacoes, never()).save(any());
    }

    @Test
    void buscar_avaliacaoInexistente_retorna404() {
        when(avaliacoes.findWithNotasById(1L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.buscar(1L))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(statusDe(e)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void buscar_avaliacaoDeColaboradorDeOutroSupervisor_retorna404() {
        Avaliacao a = new Avaliacao(COLABORADOR, DATA, null, List.of(new NotaCompetencia(1L, (short) 5)));
        when(avaliacoes.findWithNotasById(1L)).thenReturn(java.util.Optional.of(a));
        // O colaborador não é do supervisor logado: a própria busca do colaborador recusa com 404.
        when(colaboradorService.buscar(COLABORADOR))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Colaborador não encontrado"));

        assertThatThrownBy(() -> service.buscar(1L))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(statusDe(e)).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
