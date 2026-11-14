package br.com.gestao.operadores.web;

import br.com.gestao.operadores.dominio.Supervisor;
import br.com.gestao.operadores.dominio.SupervisorRepository;
import br.com.gestao.operadores.seguranca.LimitadorTentativas;
import br.com.gestao.operadores.seguranca.PoliticaSenha;
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
    private final LimitadorTentativas limitador;

    public ContaService(SupervisorRepository supervisores, PasswordEncoder encoder,
                        SupervisorLogado logado, LimitadorTentativas limitador) {
        this.supervisores = supervisores;
        this.encoder = encoder;
        this.logado = logado;
        this.limitador = limitador;
    }

    /**
     * Troca a senha do supervisor logado, conferindo a senha atual antes.
     *
     * <p>Tem freio próprio: o endpoint exige sessão válida, então quem chega aqui já está logado —
     * mas um token roubado poderia ser usado para descobrir a senha atual por tentativa e erro, e
     * com ela trocar a senha e tomar a conta de vez.
     */
    @Transactional
    public void trocarSenha(Dtos.TrocarSenhaRequest r) {
        Supervisor s = supervisores.findById(logado.id())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessão inválida"));

        if (!limitador.registrarEPermitir(LimitadorTentativas.Limites.TROCA_SENHA, s.getEmail())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "Muitas tentativas de troca de senha. Aguarde alguns minutos.");
        }
        if (!encoder.matches(r.senhaAtual(), s.getSenhaHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Senha atual incorreta");
        }
        if (encoder.matches(r.novaSenha(), s.getSenhaHash())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "A nova senha precisa ser diferente da atual");
        }
        PoliticaSenha.problema(r.novaSenha(), s.getEmail()).ifPresent(motivo -> {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, motivo);
        });
        s.alterarSenha(encoder.encode(r.novaSenha()));
    }
}
