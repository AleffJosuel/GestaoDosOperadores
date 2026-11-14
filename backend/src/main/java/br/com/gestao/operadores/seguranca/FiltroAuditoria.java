package br.com.gestao.operadores.seguranca;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Registra na trilha de auditoria toda requisição que <b>altera</b> dados, além do login.
 *
 * <p>Fica <em>dentro</em> da cadeia do Spring Security (depois da autorização), e não solto entre os
 * filtros da aplicação: é o único ponto em que, ao voltar da chamada, ainda se sabe quem estava
 * autenticado — o contexto de segurança é limpo antes disso lá fora.
 *
 * <p>Leituras entram só quando identificam <b>uma pessoa</b>: a ficha de um colaborador e as
 * sub-listas dela (avaliações, PDI, acompanhamentos). É o que permite responder "quem consultou
 * os dados deste colaborador?", pergunta legítima sob a LGPD e que não se responde com auditoria
 * apenas de alteração.
 *
 * <p>As leituras de conjunto — a lista de colaboradores, o mapa, o dashboard — continuam fora.
 * Elas são abertas a cada navegação na interface e o volume afogaria a trilha; além disso, nada
 * dizem sobre interesse num indivíduo, que é o que uma apuração investiga.
 *
 * <p>O corpo da requisição não é lido em nenhum momento: além de consumir o stream que o controller
 * precisa, gravaria senha (no login) e avaliação de desempenho (nas demais) no banco de auditoria.
 *
 * <p>Não é um {@code @Component} de propósito: todo bean de {@code Filter} é registrado
 * automaticamente pelo Spring Boot no contêiner servlet, e aí a requisição passaria duas vezes por
 * aqui — uma fora e outra dentro da cadeia de segurança —, gravando cada operação em dobro. Quem o
 * instancia é o {@link SecurityConfig}.
 */
public class FiltroAuditoria extends OncePerRequestFilter {

    private final Auditoria auditoria;
    private final IpDaRequisicao ips;

    public FiltroAuditoria(Auditoria auditoria, IpDaRequisicao ips) {
        this.auditoria = auditoria;
        this.ips = ips;
    }

    /**
     * Leitura de dados de um colaborador específico: {@code /api/colaboradores/{id}} e as
     * sub-listas da ficha. O {@code \d+} no lugar do id é o que mantém fora caminhos como
     * {@code /api/colaboradores/importar}, que não são leitura de ficha.
     */
    private static final Pattern FICHA_DE_UM_COLABORADOR =
        Pattern.compile("^/api/colaboradores/\\d+(/(avaliacoes|pdi|acompanhamentos))?$");

    /**
     * Exportação em CSV. É leitura de conjunto, mas entra na trilha: aqui os dados <b>saem</b> do
     * sistema e passam a viver num arquivo que ninguém controla mais. Para quem apura um
     * vazamento, é o registro mais valioso que existe.
     */
    private static final Pattern EXPORTACAO =
        Pattern.compile("^/api/(mapa|dashboard)/exportar$");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest requisicao) {
        if (!requisicao.getRequestURI().startsWith("/api/")
            || "OPTIONS".equalsIgnoreCase(requisicao.getMethod())) {
            return true;
        }
        if (!"GET".equalsIgnoreCase(requisicao.getMethod())) {
            return false; // toda alteração é registrada
        }
        String uri = requisicao.getRequestURI();
        return !FICHA_DE_UM_COLABORADOR.matcher(uri).matches() && !EXPORTACAO.matcher(uri).matches();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requisicao, HttpServletResponse resposta,
                                    FilterChain cadeia) throws ServletException, IOException {
        long inicio = System.nanoTime();
        try {
            cadeia.doFilter(requisicao, resposta);
        } finally {
            long duracaoMs = (System.nanoTime() - inicio) / 1_000_000;
            auditoria.registrar(autor(requisicao), requisicao.getMethod(),
                requisicao.getRequestURI(), resposta.getStatus(), ips.de(requisicao), duracaoMs);
        }
    }

    /**
     * E-mail de quem fez a chamada, ou null quando não havia sessão — o caso do login, em que o
     * autor ainda não existe e o que importa é o par IP + status (401 repetido = ataque).
     */
    private static String autor(HttpServletRequest requisicao) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        String nome = auth.getName();
        return "anonymousUser".equals(nome) ? null : nome;
    }
}
