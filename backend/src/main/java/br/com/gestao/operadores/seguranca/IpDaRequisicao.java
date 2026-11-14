package br.com.gestao.operadores.seguranca;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * IP de origem da requisição, usado pelo limitador de tentativas e pela trilha de auditoria.
 *
 * <p>Em produção a aplicação fica atrás de um proxy (Render, Railway, nginx), e aí
 * {@code getRemoteAddr()} devolve o IP do <em>proxy</em> — o mesmo para todo mundo. Sem isto, o
 * freio contra força bruta contaria todas as tentativas do mundo numa única chave e travaria o
 * login de todos os supervisores ao primeiro ataque.
 *
 * <p>O {@code X-Forwarded-For} só é lido quando {@code PROXY_CONFIAVEL=true}, porque o cabeçalho é
 * enviado pelo cliente e qualquer um pode forjá-lo: num acesso direto, confiar nele deixaria o
 * atacante escolher uma chave nova por tentativa e burlar o limite.
 */
@Component
public class IpDaRequisicao {

    private final boolean atrasDeProxyConfiavel;

    public IpDaRequisicao(@Value("${app.proxy.confiavel}") boolean atrasDeProxyConfiavel) {
        this.atrasDeProxyConfiavel = atrasDeProxyConfiavel;
    }

    public String de(HttpServletRequest requisicao) {
        if (atrasDeProxyConfiavel) {
            String encaminhado = primeiroDe(requisicao.getHeader("X-Forwarded-For"));
            if (encaminhado != null) {
                return encaminhado;
            }
        }
        String remoto = requisicao.getRemoteAddr();
        return remoto == null ? "desconhecido" : remoto;
    }

    /**
     * O {@code X-Forwarded-For} acumula a cadeia ("cliente, proxy1, proxy2"); o cliente real é o
     * primeiro. Devolve null quando o cabeçalho não veio ou veio vazio.
     */
    private static String primeiroDe(String cabecalho) {
        if (cabecalho == null) {
            return null;
        }
        String primeiro = cabecalho.split(",")[0].strip();
        return primeiro.isEmpty() ? null : primeiro;
    }
}
