package br.com.gestao.operadores.web;

import br.com.gestao.operadores.dominio.*;
import br.com.gestao.operadores.seguranca.SupervisorLogado;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Ações de PDI. Tudo é filtrado pelo supervisor logado pela cadeia ação → colaborador → supervisor
 * (o colaborador é carregado por {@link ColaboradorService#buscar}, que responde 404 a quem não é dono).
 * A competência precisa ser do supervisor e a avaliação de origem precisa ser daquele colaborador.
 */
@Service
public class AcaoPdiService {

    private final AcaoPdiRepository acoes;
    private final CompetenciaRepository competencias;
    private final AvaliacaoRepository avaliacoes;
    private final ColaboradorService colaboradorService;
    private final SupervisorLogado logado;

    public AcaoPdiService(AcaoPdiRepository acoes, CompetenciaRepository competencias,
                          AvaliacaoRepository avaliacoes, ColaboradorService colaboradorService,
                          SupervisorLogado logado) {
        this.acoes = acoes;
        this.competencias = competencias;
        this.avaliacoes = avaliacoes;
        this.colaboradorService = colaboradorService;
        this.logado = logado;
    }

    @Transactional(readOnly = true)
    public List<AcaoPdi> listar(Long colaboradorId) {
        colaboradorService.buscar(colaboradorId); // garante que o colaborador é do supervisor logado
        return acoes.findByColaboradorIdOrderByPrazoAsc(colaboradorId);
    }

    /**
     * Ações de PDI dos colaboradores ATIVOS do supervisor logado, com o nome de cada um.
     * Desativado é quem saiu da equipe: não aparece na visão de equipe, pelo mesmo critério
     * do dashboard (cujos contadores de PDI já consideram só os ativos). Para ver o histórico
     * de alguém desativado, use a ficha dele, a partir de "Mostrar inativos" em Colaboradores.
     */
    @Transactional(readOnly = true)
    public List<Dtos.PdiItem> listarTodos() {
        Map<Long, Colaborador> porId = colaboradorService.listar(false).stream()
            .collect(Collectors.toMap(Colaborador::getId, Function.identity()));
        if (porId.isEmpty()) return List.of();
        return acoes.findByColaboradorIdInOrderByPrazoAsc(porId.keySet()).stream()
            .map(a -> Dtos.PdiItem.de(a, porId.get(a.getColaboradorId())))
            .toList();
    }

    @Transactional(readOnly = true)
    public AcaoPdi buscar(Long id) {
        AcaoPdi a = acoes.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ação de PDI não encontrada"));
        colaboradorService.buscar(a.getColaboradorId()); // confirma a posse; 404 para quem não é dono
        return a;
    }

    @Transactional
    public AcaoPdi criar(Long colaboradorId, Dtos.PdiRequest r) {
        Colaborador c = colaboradorService.buscar(colaboradorId);
        // Mesma regra da avaliação: quem saiu da equipe não recebe plano novo. Editar uma
        // ação já existente continua permitido, para corrigir o histórico dele.
        if (!c.isAtivo()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Não é possível criar ação de PDI para um colaborador inativo");
        }
        if (r.competenciaId() == null || r.avaliacaoOrigemId() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Informe a competência e a avaliação de origem");
        }
        competencias.findByIdAndSupervisorId(r.competenciaId(), logado.id())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Competência não encontrada"));
        if (!avaliacoes.existsByIdAndColaboradorId(r.avaliacaoOrigemId(), colaboradorId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Avaliação de origem não encontrada para este colaborador");
        }

        StatusPdi status = statusOuPadrao(r.status());
        LocalDate dataConclusao = conclusaoCoerente(status, r.dataConclusao());
        return acoes.save(new AcaoPdi(colaboradorId, r.competenciaId(), r.avaliacaoOrigemId(),
            r.descricao(), r.responsavel(), r.funcaoArea(), r.cargaHorariaMinutos(), r.prazo(),
            status, dataConclusao, r.evidencia(), r.observacao()));
    }

    @Transactional
    public AcaoPdi atualizar(Long id, Dtos.PdiRequest r) {
        AcaoPdi a = buscar(id); // competência/avaliação de origem não mudam na edição
        StatusPdi status = statusOuPadrao(r.status());
        LocalDate dataConclusao = conclusaoCoerente(status, r.dataConclusao());
        a.definir(r.descricao(), r.responsavel(), r.funcaoArea(), r.cargaHorariaMinutos(), r.prazo(),
            status, dataConclusao, r.evidencia(), r.observacao());
        return a;
    }

    private static StatusPdi statusOuPadrao(StatusPdi status) {
        return status == null ? StatusPdi.NAO_INICIADO : status;
    }

    /** Concluída exige data (default hoje); qualquer outro status não guarda data de conclusão. */
    private static LocalDate conclusaoCoerente(StatusPdi status, LocalDate dataConclusao) {
        if (status != StatusPdi.CONCLUIDO) return null;
        return dataConclusao != null ? dataConclusao : LocalDate.now();
    }
}
