package br.com.gestao.operadores.dominio;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ColaboradorRepository extends JpaRepository<Colaborador, Long> {
    List<Colaborador> findBySupervisorIdOrderByNomeAsc(Long supervisorId);
    List<Colaborador> findBySupervisorIdAndAtivoTrueOrderByNomeAsc(Long supervisorId);
    Optional<Colaborador> findByIdAndSupervisorId(Long id, Long supervisorId);
    boolean existsBySupervisorIdAndMatricula(Long supervisorId, String matricula);
    boolean existsBySupervisorIdAndMatriculaAndIdNot(Long supervisorId, String matricula, Long id);
}
