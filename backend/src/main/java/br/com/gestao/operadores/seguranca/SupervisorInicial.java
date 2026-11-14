package br.com.gestao.operadores.seguranca;

import br.com.gestao.operadores.dominio.Supervisor;
import br.com.gestao.operadores.dominio.SupervisorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Provisiona a conta de supervisor configurada em ADMIN_EMAIL/ADMIN_PASSWORD, criando-a
 * apenas se aquele e-mail ainda não existir (idempotente). Rode a aplicação uma vez com
 * cada par e-mail/senha para criar cada conta — não há cadastro público. Junto com a conta,
 * cria a configuração inicial dela (faixas de classificação e rótulos das notas).
 * Para TROCAR a senha depois, use POST /api/sessao/senha (autenticado).
 *
 * <p>Com {@code RESET_SENHA=true}, redefine a senha de uma conta que já existe. É o socorro
 * para senha esquecida: como não há recuperação por e-mail, sem isto o supervisor que esquece a
 * senha só volta com {@code UPDATE} manual no banco — operação que exige gerar o hash BCrypt
 * à mão e que ninguém deveria precisar fazer às pressas.
 */
@Component
public class SupervisorInicial implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SupervisorInicial.class);
    private static final String[] ROTULOS = {"Crítico", "A Desenvolver", "Adequado", "Bom", "Excelente"};

    private final SupervisorRepository supervisores;
    private final PasswordEncoder encoder;
    private final JdbcTemplate jdbc;
    private final Auditoria auditoria;
    private final String email;
    private final String senha;
    private final boolean reset;

    public SupervisorInicial(SupervisorRepository supervisores, PasswordEncoder encoder, JdbcTemplate jdbc,
                             Auditoria auditoria,
                             @Value("${app.admin.email}") String email,
                             @Value("${app.admin.password}") String senha,
                             @Value("${app.admin.reset}") boolean reset) {
        this.supervisores = supervisores;
        this.encoder = encoder;
        this.jdbc = jdbc;
        this.auditoria = auditoria;
        this.email = email;
        this.senha = senha;
        this.reset = reset;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (email.isBlank() || senha.isBlank()) {
            if (reset) {
                log.error("RESET_SENHA=true, mas ADMIN_EMAIL e/ou ADMIN_PASSWORD estão vazios. "
                    + "Preencha os dois com a conta a redefinir. Nada foi alterado.");
                return;
            }
            log.warn("Nenhuma conta configurada. Defina ADMIN_EMAIL e ADMIN_PASSWORD para criar uma conta.");
            return;
        }
        Optional<Supervisor> existente = supervisores.findByEmail(email);
        if (existente.isPresent()) {
            if (reset) {
                redefinirSenha(existente.get());
            } else {
                // A conferência de existência vem antes da política de senha de propósito: contas já
                // criadas não devem gerar alarme a cada boot por causa do valor que ficou no ambiente.
                log.info("Supervisor {} já existe; nada a criar.", email);
            }
            return;
        }
        if (reset) {
            log.warn("RESET_SENHA=true, mas não existe conta com o e-mail {}. "
                + "Será criada agora, como numa primeira instalação.", email);
        }
        criar();
    }

    private void criar() {
        Optional<String> problema = PoliticaSenha.problema(senha, email);
        if (problema.isPresent()) {
            log.error("ADMIN_PASSWORD recusada pela política de senha: {} Conta não criada.", problema.get());
            return;
        }
        Long id = supervisores.save(new Supervisor(email, encoder.encode(senha))).getId();
        jdbc.update("INSERT INTO configuracao_classificacao (supervisor_id) VALUES (?)", id);
        for (int i = 0; i < ROTULOS.length; i++) {
            jdbc.update("INSERT INTO rotulo_nota (supervisor_id, nota, rotulo) VALUES (?, ?, ?)", id, i + 1, ROTULOS[i]);
        }
        log.info("Conta de supervisor criada: {}", email);
    }

    /**
     * Redefine a senha de uma conta existente. Passa pela mesma política de senha da troca
     * normal — um socorro operacional não é motivo para aceitar senha fraca.
     */
    private void redefinirSenha(Supervisor s) {
        Optional<String> problema = PoliticaSenha.problema(senha, email);
        if (problema.isPresent()) {
            log.error("RESET_SENHA=true, mas ADMIN_PASSWORD foi recusada pela política de senha: {} "
                + "A senha de {} NÃO foi alterada.", problema.get(), email);
            return;
        }
        if (encoder.matches(senha, s.getSenhaHash())) {
            // Caso típico do segundo reinício com o sinalizador esquecido no arquivo.
            log.warn("RESET_SENHA=true, mas a senha de {} já é a que está no arquivo de configuração. "
                + "Nada alterado. REMOVA RESET_SENHA do config/application.properties.", email);
            return;
        }
        s.alterarSenha(encoder.encode(senha));
        auditoria.registrar(email, "RESET", "/sistema/reset-senha", 200, "boot", 0);
        log.warn("""

            ===================================================================
             SENHA REDEFINIDA: {}
             Feito por RESET_SENHA=true no config/application.properties.

             AGORA, NESTA ORDEM:
               1. o supervisor entra com a senha temporária do arquivo;
               2. troca a senha dele em "Trocar senha", na barra lateral;
               3. a TI apaga RESET_SENHA e ADMIN_PASSWORD do arquivo e reinicia.

             Enquanto RESET_SENHA ficar true, CADA REINÍCIO desfaz a troca de
             senha feita pelo supervisor e devolve a do arquivo.

             Os tokens já emitidos para esta conta continuam valendo até
             expirarem (JWT_EXP_MIN); redefinir a senha não os invalida.
            ===================================================================
            """, email);
    }
}
