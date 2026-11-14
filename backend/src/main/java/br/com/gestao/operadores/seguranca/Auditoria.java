package br.com.gestao.operadores.seguranca;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Gravação da trilha de auditoria (tabela {@code auditoria}).
 *
 * <p>Usa {@link JdbcTemplate} em vez de JPA porque o registro acontece fora da transação do
 * negócio: a trilha tem de sobreviver ao rollback da operação que falhou — é justamente a
 * tentativa malsucedida que interessa a quem audita.
 */
@Component
public class Auditoria {

    private static final Logger log = LoggerFactory.getLogger(Auditoria.class);
    private static final int MAX_CAMINHO = 255;

    private final JdbcTemplate jdbc;

    public Auditoria(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Registra uma operação. Nunca propaga exceção: auditoria indisponível não pode derrubar a
     * requisição do usuário — a falha vai para o log da aplicação, que é o alarme de que a trilha
     * está cega.
     */
    public void registrar(String autorEmail, String metodo, String caminho, int status,
                          String ip, long duracaoMs) {
        try {
            jdbc.update("""
                INSERT INTO auditoria (autor_email, metodo, caminho, status, ip, duracao_ms)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                autorEmail, metodo, truncar(caminho), status, ip, (int) Math.min(duracaoMs, Integer.MAX_VALUE));
        } catch (RuntimeException e) {
            log.error("Falha ao gravar auditoria de {} {} (status {}): {}",
                metodo, caminho, status, e.getMessage());
        }
    }

    private static String truncar(String caminho) {
        if (caminho == null) {
            return "";
        }
        return caminho.length() <= MAX_CAMINHO ? caminho : caminho.substring(0, MAX_CAMINHO);
    }
}
