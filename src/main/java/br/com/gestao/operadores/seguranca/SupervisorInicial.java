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

/**
 * Provisiona a conta de supervisor configurada em ADMIN_EMAIL/ADMIN_PASSWORD, criando-a
 * apenas se aquele e-mail ainda não existir (idempotente). Rode a aplicação uma vez com
 * cada par e-mail/senha para criar cada conta — não há cadastro público. Junto com a conta,
 * cria a configuração inicial dela (faixas de classificação e rótulos das notas).
 * Para TROCAR a senha depois, use POST /api/sessao/senha (autenticado).
 */
@Component
public class SupervisorInicial implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SupervisorInicial.class);
    private static final int TAMANHO_MINIMO_SENHA = 12;
    private static final String[] ROTULOS = {"Crítico", "A Desenvolver", "Adequado", "Bom", "Excelente"};

    private final SupervisorRepository supervisores;
    private final PasswordEncoder encoder;
    private final JdbcTemplate jdbc;
    private final String email;
    private final String senha;

    public SupervisorInicial(SupervisorRepository supervisores, PasswordEncoder encoder, JdbcTemplate jdbc,
                             @Value("${app.admin.email}") String email,
                             @Value("${app.admin.password}") String senha) {
        this.supervisores = supervisores;
        this.encoder = encoder;
        this.jdbc = jdbc;
        this.email = email;
        this.senha = senha;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (email.isBlank() || senha.isBlank()) {
            log.warn("Nenhuma conta configurada. Defina ADMIN_EMAIL e ADMIN_PASSWORD para criar uma conta.");
            return;
        }
        if (senha.length() < TAMANHO_MINIMO_SENHA) {
            log.error("ADMIN_PASSWORD precisa ter ao menos {} caracteres. Conta não criada.", TAMANHO_MINIMO_SENHA);
            return;
        }
        if (supervisores.findByEmail(email).isPresent()) {
            log.info("Supervisor {} já existe; nada a criar.", email);
            return;
        }
        Long id = supervisores.save(new Supervisor(email, encoder.encode(senha))).getId();
        jdbc.update("INSERT INTO configuracao_classificacao (supervisor_id) VALUES (?)", id);
        for (int i = 0; i < ROTULOS.length; i++) {
            jdbc.update("INSERT INTO rotulo_nota (supervisor_id, nota, rotulo) VALUES (?, ?, ?)", id, i + 1, ROTULOS[i]);
        }
        log.info("Conta de supervisor criada: {}", email);
    }
}
