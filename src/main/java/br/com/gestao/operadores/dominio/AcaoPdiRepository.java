package br.com.gestao.operadores.dominio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface AcaoPdiRepository extends JpaRepository<AcaoPdi, Long> {
    // Prazo ASC: no PostgreSQL, prazos nulos (sem data) ficam por último.
    List<AcaoPdi> findByColaboradorIdOrderByPrazoAsc(Long colaboradorId);

    // Indicadores do dashboard (contagens por equipe).
    long countByColaboradorIdInAndStatus(Collection<Long> colaboradorIds, StatusPdi status);
    long countByColaboradorIdInAndStatusNot(Collection<Long> colaboradorIds, StatusPdi status);
    long countByColaboradorIdInAndStatusNotAndPrazoBefore(
        Collection<Long> colaboradorIds, StatusPdi status, LocalDate prazo);
}
