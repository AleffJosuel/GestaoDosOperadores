package br.com.gestao.operadores.dominio;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SupervisorRepository extends JpaRepository<Supervisor, Long> {
    Optional<Supervisor> findByEmail(String email);
}
