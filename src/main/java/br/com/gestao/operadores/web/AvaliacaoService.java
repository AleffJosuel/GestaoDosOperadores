package br.com.gestao.operadores.web;

import br.com.gestao.operadores.dominio.*;
import br.com.gestao.operadores.seguranca.SupervisorLogado;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Regras e autorização das avaliações. O dono é alcançado pela cadeia
 * avaliação → colaborador → supervisor logado: o colaborador é sempre carregado
 * por {@link ColaboradorService#buscar}, que responde 404 para quem não é dono.
 * As competências de cada nota precisam pertencer ao mesmo supervisor.
 */
@Service
public class AvaliacaoService {

    private final AvaliacaoRepository avaliacoes;
    private final CompetenciaRepository competencias;
    private final ColaboradorService colaboradorService;
    private final SupervisorLogado logado;

    public AvaliacaoService(AvaliacaoRepository avaliacoes, CompetenciaRepository competencias,
                            ColaboradorService colaboradorService, SupervisorLogado logado) {
        this.avaliacoes = avaliacoes;
        this.competencias = competencias;
        this.colaboradorService = colaboradorService;
        this.logado = logado;
    }

    @Transactional(readOnly = true)
    public List<Avaliacao> listar(Long colaboradorId) {
        colaboradorService.buscar(colaboradorId); // garante que o colaborador é do supervisor logado
        return avaliacoes.findByColaboradorIdOrderByDataAvaliacaoDesc(colaboradorId);
    }

    @Transactional(readOnly = true)
    public Avaliacao buscar(Long id) {
        Avaliacao a = avaliacoes.findWithNotasById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Avaliação não encontrada"));
        // Confirma a posse pelo colaborador; para quem não é dono, 404 (não revela que existe).
        colaboradorService.buscar(a.getColaboradorId());
        return a;
    }

    @Transactional
    public Avaliacao criar(Long colaboradorId, Dtos.AvaliacaoRequest r) {
        Colaborador c = colaboradorService.buscar(colaboradorId);
        if (!c.isAtivo()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Não é possível avaliar um colaborador inativo");
        }
        Set<NotaCompetencia> notas = validarNotas(r.notas());
        if (avaliacoes.existsByColaboradorIdAndDataAvaliacao(colaboradorId, r.dataAvaliacao())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Já existe uma avaliação deste colaborador nesta data");
        }
        return avaliacoes.save(new Avaliacao(colaboradorId, r.dataAvaliacao(), r.observacao(), notas));
    }

    @Transactional
    public Avaliacao atualizar(Long id, Dtos.AvaliacaoRequest r) {
        Avaliacao a = buscar(id);
        Set<NotaCompetencia> notas = validarNotas(r.notas());
        if (avaliacoes.existsByColaboradorIdAndDataAvaliacaoAndIdNot(a.getColaboradorId(), r.dataAvaliacao(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Já existe uma avaliação deste colaborador nesta data");
        }
        a.definir(r.dataAvaliacao(), r.observacao(), notas);
        return a;
    }

    /**
     * Converte as notas do pedido, rejeitando competência repetida e garantindo que
     * todas pertencem ao supervisor logado (competência de outro responde 404).
     */
    private Set<NotaCompetencia> validarNotas(List<Dtos.NotaRequest> pedidas) {
        List<Long> ids = pedidas.stream().map(Dtos.NotaRequest::competenciaId).toList();
        Set<Long> idsUnicos = Set.copyOf(ids);
        if (idsUnicos.size() != ids.size()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "A mesma competência aparece mais de uma vez na avaliação");
        }
        Set<Long> idsDoSupervisor = competencias.findBySupervisorIdAndIdIn(logado.id(), idsUnicos).stream()
            .map(Competencia::getId)
            .collect(Collectors.toSet());
        if (!idsDoSupervisor.containsAll(idsUnicos)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Competência não encontrada");
        }
        return pedidas.stream()
            .map(n -> new NotaCompetencia(n.competenciaId(), n.nota().shortValue()))
            .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }
}
