package br.com.gestao.operadores.web;

import br.com.gestao.operadores.dominio.*;
import br.com.gestao.operadores.seguranca.SupervisorLogado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Invariante central do sistema: <b>um supervisor só enxerga os próprios dados</b>.
 *
 * <p>O isolamento não é aplicado no controller nem por filtro: nasce de duas regras,
 * verificadas aqui para que ninguém as desfaça sem o teste acusar.
 * <ol>
 *   <li>Colaborador e competência sempre são buscados por <em>id + supervisor logado</em>
 *       (nunca por {@code findById}, que ignoraria o dono);</li>
 *   <li>O que pende de colaborador (avaliações, PDI, acompanhamentos) passa antes por
 *       {@link ColaboradorService#buscar}, herdando o filtro acima.</li>
 * </ol>
 *
 * <p>Dado de outro supervisor responde <b>404 e não 403</b>, de propósito: 403 confirmaria
 * que aquele id existe. Os testes abaixo fixam o 404.
 *
 * <p>Os serviços são montados de verdade (não há mock de {@code ColaboradorService}), só os
 * repositórios e o supervisor logado são simulados — assim a cadeia de posse é exercitada
 * de ponta a ponta.
 */
@ExtendWith(MockitoExtension.class)
class IsolamentoPorSupervisorTest {

    /** Quem está logado nos testes. */
    private static final Long SUPERVISOR = 7L;
    /** Colaborador/competência que pertencem a OUTRO supervisor: o repositório não os devolve. */
    private static final Long DE_OUTRO = 99L;
    private static final Long COLABORADOR = 10L;

    @Mock ColaboradorRepository colaboradores;
    @Mock CompetenciaRepository competencias;
    @Mock RegistroAcompanhamentoRepository registros;
    @Mock AcaoPdiRepository acoes;
    @Mock AvaliacaoRepository avaliacoes;
    @Mock SupervisorLogado logado;

    private ColaboradorService colaboradorService;
    private CompetenciaService competenciaService;
    private RegistroAcompanhamentoService registroService;
    private AcaoPdiService pdiService;

    @BeforeEach
    void setUp() {
        // lenient: nem todo teste chega a consultar quem está logado (alguns falham antes).
        lenient().when(logado.id()).thenReturn(SUPERVISOR);

        colaboradorService = new ColaboradorService(colaboradores, logado);
        competenciaService = new CompetenciaService(competencias, logado);
        registroService = new RegistroAcompanhamentoService(registros, colaboradorService);
        pdiService = new AcaoPdiService(acoes, competencias, avaliacoes, colaboradorService, logado);
    }

    private HttpStatusCode statusDe(Throwable t) {
        return ((ResponseStatusException) t).getStatusCode();
    }

    /** O repositório só devolve o que é do supervisor logado; para os demais ids, vazio. */
    private void colaboradorPertenceAoSupervisor(Long id) {
        when(colaboradores.findByIdAndSupervisorId(id, SUPERVISOR))
            .thenReturn(Optional.of(new Colaborador(SUPERVISOR, "1001", "Fulano", "OPERADOR", "Injeção", null, null)));
    }

    private void colaboradorNaoPertenceAoSupervisor(Long id) {
        when(colaboradores.findByIdAndSupervisorId(id, SUPERVISOR)).thenReturn(Optional.empty());
    }

    // ---------------------------------------------------------------- colaborador

    @Test
    void colaborador_buscar_deOutroSupervisor_retorna404ENaoVazaExistencia() {
        colaboradorNaoPertenceAoSupervisor(DE_OUTRO);

        assertThatThrownBy(() -> colaboradorService.buscar(DE_OUTRO))
            .isInstanceOf(ResponseStatusException.class)
            // 404 (e não 403): 403 revelaria que o colaborador existe.
            .satisfies(e -> assertThat(statusDe(e)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void colaborador_buscar_nuncaConsultaPorIdSemOSupervisor() {
        colaboradorPertenceAoSupervisor(COLABORADOR);

        colaboradorService.buscar(COLABORADOR);

        verify(colaboradores).findByIdAndSupervisorId(COLABORADOR, SUPERVISOR);
        // A busca global ignoraria o dono: nunca pode ser usada aqui.
        verify(colaboradores, never()).findById(any());
    }

    @Test
    void colaborador_listar_filtraPeloSupervisorLogado() {
        when(colaboradores.findBySupervisorIdAndAtivoTrueOrderByNomeAsc(SUPERVISOR)).thenReturn(List.of());

        colaboradorService.listar(false);

        verify(colaboradores).findBySupervisorIdAndAtivoTrueOrderByNomeAsc(SUPERVISOR);
        verify(colaboradores, never()).findAll();
    }

    @Test
    void colaborador_listarIncluindoInativos_tambemFiltraPeloSupervisorLogado() {
        when(colaboradores.findBySupervisorIdOrderByNomeAsc(SUPERVISOR)).thenReturn(List.of());

        colaboradorService.listar(true);

        verify(colaboradores).findBySupervisorIdOrderByNomeAsc(SUPERVISOR);
        verify(colaboradores, never()).findAll();
    }

    @Test
    void colaborador_criar_marcaSempreOSupervisorLogado() {
        when(colaboradores.existsBySupervisorIdAndMatricula(SUPERVISOR, "1001")).thenReturn(false);
        when(colaboradores.save(any(Colaborador.class))).thenAnswer(inv -> inv.getArgument(0));

        colaboradorService.criar(new Dtos.ColaboradorRequest("1001", "Fulano", "OPERADOR", "Injeção", null, null));

        ArgumentCaptor<Colaborador> cap = ArgumentCaptor.forClass(Colaborador.class);
        verify(colaboradores).save(cap.capture());
        // O dono vem de quem está logado — o pedido não tem (nem pode ter) campo de supervisor.
        assertThat(cap.getValue().getSupervisorId()).isEqualTo(SUPERVISOR);
    }

    @Test
    void colaborador_atualizar_deOutroSupervisor_retorna404ENaoGrava() {
        colaboradorNaoPertenceAoSupervisor(DE_OUTRO);

        assertThatThrownBy(() -> colaboradorService.atualizar(DE_OUTRO,
                new Dtos.ColaboradorRequest("1001", "Outro", "OPERADOR", "Injeção", null, null)))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(statusDe(e)).isEqualTo(HttpStatus.NOT_FOUND));

        verify(colaboradores, never()).save(any());
    }

    @Test
    void colaborador_desativar_deOutroSupervisor_retorna404() {
        colaboradorNaoPertenceAoSupervisor(DE_OUTRO);

        assertThatThrownBy(() -> colaboradorService.alterarAtivo(DE_OUTRO, false))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(statusDe(e)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ---------------------------------------------------------------- competência

    @Test
    void competencia_buscar_deOutroSupervisor_retorna404() {
        when(competencias.findByIdAndSupervisorId(DE_OUTRO, SUPERVISOR)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> competenciaService.buscar(DE_OUTRO))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(statusDe(e)).isEqualTo(HttpStatus.NOT_FOUND));

        verify(competencias, never()).findById(any());
    }

    @Test
    void competencia_listar_filtraPeloSupervisorLogado() {
        when(competencias.findBySupervisorIdAndAtivaTrueOrderByNomeAsc(SUPERVISOR)).thenReturn(List.of());

        competenciaService.listar(false);

        verify(competencias).findBySupervisorIdAndAtivaTrueOrderByNomeAsc(SUPERVISOR);
        verify(competencias, never()).findAll();
    }

    @Test
    void competencia_criar_marcaSempreOSupervisorLogado() {
        when(competencias.existsBySupervisorIdAndNomeIgnoreCase(SUPERVISOR, "Comunicação")).thenReturn(false);
        when(competencias.save(any(Competencia.class))).thenAnswer(inv -> inv.getArgument(0));

        competenciaService.criar(new Dtos.CompetenciaRequest(
            "Comunicação", TipoCompetencia.COMPORTAMENTAL, null, null, 4, null));

        ArgumentCaptor<Competencia> cap = ArgumentCaptor.forClass(Competencia.class);
        verify(competencias).save(cap.capture());
        assertThat(cap.getValue().getSupervisorId()).isEqualTo(SUPERVISOR);
    }

    @Test
    void competencia_desativar_deOutroSupervisor_retorna404() {
        when(competencias.findByIdAndSupervisorId(DE_OUTRO, SUPERVISOR)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> competenciaService.alterarAtiva(DE_OUTRO, false))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(statusDe(e)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ------------------------------------------- o que pende de colaborador (a cadeia)

    @Test
    void acompanhamentos_listar_deColaboradorDeOutroSupervisor_retorna404SemLerRegistros() {
        colaboradorNaoPertenceAoSupervisor(DE_OUTRO);

        assertThatThrownBy(() -> registroService.listar(DE_OUTRO))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(statusDe(e)).isEqualTo(HttpStatus.NOT_FOUND));

        // Barrou antes de tocar nos registros: nem o volume de dados do outro supervisor vaza.
        verify(registros, never()).findByColaboradorIdOrderByDataEventoDescIdDesc(any());
    }

    @Test
    void acompanhamentos_criar_paraColaboradorDeOutroSupervisor_retorna404SemSalvar() {
        colaboradorNaoPertenceAoSupervisor(DE_OUTRO);

        assertThatThrownBy(() -> registroService.criar(DE_OUTRO, new Dtos.RegistroRequest(
                TipoRegistro.FEEDBACK, LocalDate.of(2026, 1, 15), "tentativa indevida")))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(statusDe(e)).isEqualTo(HttpStatus.NOT_FOUND));

        verify(registros, never()).save(any());
    }

    @Test
    void pdi_listar_deColaboradorDeOutroSupervisor_retorna404SemLerAcoes() {
        colaboradorNaoPertenceAoSupervisor(DE_OUTRO);

        assertThatThrownBy(() -> pdiService.listar(DE_OUTRO))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(statusDe(e)).isEqualTo(HttpStatus.NOT_FOUND));

        verify(acoes, never()).findByColaboradorIdOrderByPrazoAsc(any());
    }

    @Test
    void pdi_criar_paraColaboradorDeOutroSupervisor_retorna404SemSalvar() {
        colaboradorNaoPertenceAoSupervisor(DE_OUTRO);

        assertThatThrownBy(() -> pdiService.criar(DE_OUTRO, new Dtos.PdiRequest(
                1L, 2L, "ação indevida", null, null, null, null, null, null, null, null)))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(statusDe(e)).isEqualTo(HttpStatus.NOT_FOUND));

        verify(acoes, never()).save(any());
    }

    @Test
    void pdi_buscarAcaoDeColaboradorDeOutroSupervisor_retorna404() {
        // A ação existe (a busca é por id), mas o dono do colaborador é outro supervisor.
        AcaoPdi acao = new AcaoPdi(DE_OUTRO, 1L, 2L, "ação do outro",
            null, null, null, null, StatusPdi.EM_ANDAMENTO, null, null, null);
        when(acoes.findById(1L)).thenReturn(Optional.of(acao));
        colaboradorNaoPertenceAoSupervisor(DE_OUTRO);

        assertThatThrownBy(() -> pdiService.buscar(1L))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(statusDe(e)).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void pdi_atualizarAcaoDeColaboradorDeOutroSupervisor_retorna404() {
        AcaoPdi acao = new AcaoPdi(DE_OUTRO, 1L, 2L, "ação do outro",
            null, null, null, null, StatusPdi.EM_ANDAMENTO, null, null, null);
        when(acoes.findById(1L)).thenReturn(Optional.of(acao));
        colaboradorNaoPertenceAoSupervisor(DE_OUTRO);

        assertThatThrownBy(() -> pdiService.atualizar(1L, new Dtos.PdiRequest(
                1L, 2L, "sequestro da ação", null, null, null, null, StatusPdi.CONCLUIDO, null, null, null)))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(statusDe(e)).isEqualTo(HttpStatus.NOT_FOUND));

        // A ação do outro supervisor não pode ter sido alterada em memória.
        assertThat(acao.getDescricao()).isEqualTo("ação do outro");
        assertThat(acao.getStatus()).isEqualTo(StatusPdi.EM_ANDAMENTO);
    }

    @Test
    void pdi_visaoDaEquipe_consultaApenasOsColaboradoresDoSupervisor() {
        Colaborador meu = mock(Colaborador.class);
        when(meu.getId()).thenReturn(COLABORADOR);
        when(meu.getNome()).thenReturn("Fulano");
        when(meu.isAtivo()).thenReturn(true);
        Colaborador outroMeu = mock(Colaborador.class);
        when(outroMeu.getId()).thenReturn(11L);

        when(colaboradores.findBySupervisorIdAndAtivoTrueOrderByNomeAsc(SUPERVISOR))
            .thenReturn(List.of(meu, outroMeu));
        when(acoes.findByColaboradorIdInOrderByPrazoAsc(any())).thenReturn(List.of(
            new AcaoPdi(COLABORADOR, 1L, 2L, "minha ação",
                null, null, null, null, StatusPdi.EM_ANDAMENTO, null, null, null)));

        List<Dtos.PdiItem> itens = pdiService.listarTodos();

        // A consulta de ações é restrita aos ids dos colaboradores do supervisor logado.
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(acoes).findByColaboradorIdInOrderByPrazoAsc(ids.capture());
        assertThat(ids.getValue()).containsExactlyInAnyOrder(COLABORADOR, 11L);
        verify(acoes, never()).findAll();

        assertThat(itens).singleElement()
            .satisfies(i -> assertThat(i.colaboradorNome()).isEqualTo("Fulano"));
    }

    @Test
    void pdi_visaoDaEquipe_naoTrazAcoesDeColaboradorDesativado() {
        // Desativado = saiu da equipe. A visão de equipe pede só os ativos, então as ações
        // de quem foi desativado não entram (nem o nome dele aparece na tela).
        Colaborador ativo = mock(Colaborador.class);
        when(ativo.getId()).thenReturn(COLABORADOR);
        when(ativo.getNome()).thenReturn("Fulano");
        when(ativo.isAtivo()).thenReturn(true);

        when(colaboradores.findBySupervisorIdAndAtivoTrueOrderByNomeAsc(SUPERVISOR))
            .thenReturn(List.of(ativo));
        when(acoes.findByColaboradorIdInOrderByPrazoAsc(any())).thenReturn(List.of(
            new AcaoPdi(COLABORADOR, 1L, 2L, "ação do ativo",
                null, null, null, null, StatusPdi.EM_ANDAMENTO, null, null, null)));

        List<Dtos.PdiItem> itens = pdiService.listarTodos();

        // O repositório que inclui inativos não pode nem ser consultado aqui.
        verify(colaboradores, never()).findBySupervisorIdOrderByNomeAsc(any());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Long>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(acoes).findByColaboradorIdInOrderByPrazoAsc(ids.capture());
        assertThat(ids.getValue()).containsExactly(COLABORADOR); // só o ativo
        assertThat(itens).singleElement()
            .satisfies(i -> assertThat(i.colaboradorNome()).isEqualTo("Fulano"));
    }

    @Test
    void pdi_visaoDaEquipe_supervisorSemColaboradores_naoConsultaAcoes() {
        when(colaboradores.findBySupervisorIdAndAtivoTrueOrderByNomeAsc(SUPERVISOR)).thenReturn(List.of());

        assertThat(pdiService.listarTodos()).isEmpty();

        // Sem ids para filtrar, uma consulta "IN ()" poderia virar varredura: nem chega a consultar.
        verify(acoes, never()).findByColaboradorIdInOrderByPrazoAsc(any());
    }
}
