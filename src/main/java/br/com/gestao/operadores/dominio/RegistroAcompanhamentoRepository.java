package br.com.gestao.operadores.dominio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RegistroAcompanhamentoRepository extends JpaRepository<RegistroAcompanhamento, Long> {
    // Mais recentes primeiro; o id desempata quando há vários no mesmo dia.
    List<RegistroAcompanhamento> findByColaboradorIdOrderByDataEventoDescIdDesc(Long colaboradorId);
}
