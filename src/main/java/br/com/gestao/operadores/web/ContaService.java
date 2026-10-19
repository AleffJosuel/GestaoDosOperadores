package br.com.gestao.operadores.web;

import br.com.gestao.operadores.dominio.Supervisor;
import br.com.gestao.operadores.dominio.SupervisorRepository;
import br.com.gestao.operadores.seguranca.SupervisorLogado;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Operações sobre a própria conta do supervisor logado. */
@Service
public class ContaService {

    private final SupervisorRepository supervisores;
    private final PasswordEncoder encoder;
    private final SupervisorLogado logado;

    public ContaService(SupervisorRepository supervisores, PasswordEncoder encoder, SupervisorLogado logado) {
        this.supervisores = supervisores;
        this.encoder = encoder;
        this.logado = logado;
    }

    /** Troca a senha do supervisor logado, conferindo a senha atual antes. */
    @Transactional
    public void trocarSenha(Dtos.TrocarSenhaRequest r) {
        Supervisor s = supervisores.findById(logado.id())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão inválida"));

        if (!encoder.matches(r.senhaAtual(), s.getSenhaHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Senha atual incorreta");
        }
        if (encoder.matches(r.novaSenha(), s.getSenhaHash())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "A nova senha precisa ser diferente da atual");
        }
        s.alterarSenha(encoder.encode(r.novaSenha()));
    }
}
