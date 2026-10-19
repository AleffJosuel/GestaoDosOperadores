package br.com.gestao.operadores.seguranca;

import br.com.gestao.operadores.dominio.SupervisorRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Ponto único para saber quem está logado. Todo filtro por dono parte daqui. */
@Component
public class SupervisorLogado {

    private final SupervisorRepository supervisores;

    public SupervisorLogado(SupervisorRepository supervisores) {
        this.supervisores = supervisores;
    }

    public Long id() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return supervisores.findByEmail(email).orElseThrow().getId();
    }
}
