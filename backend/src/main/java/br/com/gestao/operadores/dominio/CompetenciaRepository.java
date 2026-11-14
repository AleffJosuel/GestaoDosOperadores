package br.com.gestao.operadores.dominio;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CompetenciaRepository extends JpaRepository<Competencia, Long> {
    List<Competencia> findBySupervisorIdOrderByNomeAsc(Long supervisorId);
    List<Competencia> findBySupervisorIdAndAtivaTrueOrderByNomeAsc(Long supervisorId);
    Optional<Competencia> findByIdAndSupervisorId(Long id, Long supervisorId);
    // Usado para validar, de uma só vez, que todas as competências de uma avaliação são do supervisor.
    List<Competencia> findBySupervisorIdAndIdIn(Long supervisorId, Collection<Long> ids);
    boolean existsBySupervisorIdAndNomeIgnoreCase(Long supervisorId, String nome);
    boolean existsBySupervisorIdAndNomeIgnoreCaseAndIdNot(Long supervisorId, String nome, Long id);
}
