package br.com.gestao.operadores.dominio;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AvaliacaoRepository extends JpaRepository<Avaliacao, Long> {

    // @EntityGraph traz as notas na mesma consulta, evitando N+1 ao montar as respostas.
    @EntityGraph(attributePaths = "notas")
    List<Avaliacao> findByColaboradorIdOrderByDataAvaliacaoDesc(Long colaboradorId);

    @EntityGraph(attributePaths = "notas")
    Optional<Avaliacao> findWithNotasById(Long id);

    boolean existsByColaboradorIdAndDataAvaliacao(Long colaboradorId, LocalDate dataAvaliacao);

    // PDI: confirma que a avaliação de origem pertence ao colaborador informado.
    boolean existsByIdAndColaboradorId(Long id, Long colaboradorId);

    boolean existsByColaboradorIdAndDataAvaliacaoAndIdNot(Long colaboradorId, LocalDate dataAvaliacao, Long id);

    // Mapa/dashboard: todas as avaliações da equipe, mais recente primeiro (nota atual = primeira vista).
    @EntityGraph(attributePaths = "notas")
    List<Avaliacao> findByColaboradorIdInOrderByDataAvaliacaoDesc(Collection<Long> colaboradorIds);

    long countByColaboradorIdIn(Collection<Long> colaboradorIds);
}
