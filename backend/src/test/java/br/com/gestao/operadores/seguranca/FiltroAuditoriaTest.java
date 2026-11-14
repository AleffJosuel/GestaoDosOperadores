package br.com.gestao.operadores.seguranca;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * A trilha de auditoria é o que permite responder "quem alterou isso?" e "quem consultou os dados
 * desta pessoa?" meses depois. Estes testes fixam as decisões que a tornam útil e não perigosa:
 * registra toda alteração, registra leitura de ficha individual mas não de conjunto, identifica o
 * autor e nunca toca no corpo da requisição.
 */
@ExtendWith(MockitoExtension.class)
class FiltroAuditoriaTest {

    @Mock Auditoria auditoria;

    private final IpDaRequisicao ips = new IpDaRequisicao(false);

    @AfterEach
    void limparContexto() {
        SecurityContextHolder.clearContext();
    }

    private void passar(MockHttpServletRequest requisicao, MockHttpServletResponse resposta)
            throws ServletException, IOException {
        new FiltroAuditoria(auditoria, ips).doFilter(requisicao, resposta, new MockFilterChain());
    }

    private static MockHttpServletRequest requisicao(String metodo, String caminho) {
        MockHttpServletRequest req = new MockHttpServletRequest(metodo, caminho);
        req.setRemoteAddr("203.0.113.9");
        return req;
    }

    private static void logadoComo(String email) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(email, null, AuthorityUtils.NO_AUTHORITIES));
    }

    @Test
    void registraAlteracaoComAutorMetodoCaminhoEIp() throws Exception {
        logadoComo("ana@empresa.com");
        MockHttpServletResponse resposta = new MockHttpServletResponse();
        resposta.setStatus(201);

        passar(requisicao("POST", "/api/colaboradores"), resposta);

        verify(auditoria).registrar(eq("ana@empresa.com"), eq("POST"), eq("/api/colaboradores"),
            eq(201), eq("203.0.113.9"), anyLong());
    }

    @Test
    void ignoraLeituraDeConjunto() throws Exception {
        logadoComo("ana@empresa.com");

        // Abertas a cada navegação na interface: o volume afogaria a trilha, e nada dizem sobre
        // interesse num indivíduo — que é o que uma apuração de LGPD investiga.
        for (String caminho : new String[] {"/api/colaboradores", "/api/competencias",
                                            "/api/mapa", "/api/dashboard", "/api/pdi"}) {
            passar(requisicao("GET", caminho), new MockHttpServletResponse());
        }

        verifyNoInteractions(auditoria);
    }

    @Test
    void registraConsultaAFichaDeUmColaborador() throws Exception {
        logadoComo("ana@empresa.com");

        passar(requisicao("GET", "/api/colaboradores/42"), new MockHttpServletResponse());

        // Responde "quem consultou os dados desta pessoa?", que auditoria só de alteração não
        // responde. Sem isto, consulta indevida não deixa rastro nenhum.
        verify(auditoria).registrar(eq("ana@empresa.com"), eq("GET"), eq("/api/colaboradores/42"),
            anyInt(), eq("203.0.113.9"), anyLong());
    }

    @Test
    void registraConsultaAsSubListasDaFicha() throws Exception {
        logadoComo("ana@empresa.com");

        // A ficha carrega estas três além dos dados cadastrais; todas revelam a mesma pessoa.
        for (String sub : new String[] {"avaliacoes", "pdi", "acompanhamentos"}) {
            passar(requisicao("GET", "/api/colaboradores/42/" + sub), new MockHttpServletResponse());

            verify(auditoria).registrar(eq("ana@empresa.com"), eq("GET"),
                eq("/api/colaboradores/42/" + sub), anyInt(), anyString(), anyLong());
        }
    }

    @Test
    void naoConfundeCaminhoQueNaoEIdComFicha() throws Exception {
        logadoComo("ana@empresa.com");

        // /importar é POST na prática, mas o padrão precisa exigir dígitos: do contrário qualquer
        // sub-recurso novo de /api/colaboradores passaria a ser auditado como leitura de ficha.
        passar(requisicao("GET", "/api/colaboradores/importar"), new MockHttpServletResponse());

        verifyNoInteractions(auditoria);
    }

    @Test
    void ignoraPreflightDoNavegador() throws Exception {
        passar(requisicao("OPTIONS", "/api/colaboradores"), new MockHttpServletResponse());

        verifyNoInteractions(auditoria);
    }

    @Test
    void ignoraCaminhoForaDaApi() throws Exception {
        passar(requisicao("POST", "/qualquer-outra-coisa"), new MockHttpServletResponse());

        verifyNoInteractions(auditoria);
    }

    @Test
    void registraLoginSemAutor() throws Exception {
        // No login o autor ainda não existe; o que interessa é IP + status (401 repetido = ataque).
        MockHttpServletResponse resposta = new MockHttpServletResponse();
        resposta.setStatus(401);

        passar(requisicao("POST", "/api/sessao"), resposta);

        verify(auditoria).registrar(isNull(), eq("POST"), eq("/api/sessao"), eq(401),
            eq("203.0.113.9"), anyLong());
    }

    @Test
    void naoGravaUsuarioAnonimoComoAutor() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
            "chave", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        passar(requisicao("POST", "/api/sessao"), new MockHttpServletResponse());

        // "anonymousUser" na coluna de autor pareceria um usuário de verdade numa auditoria.
        verify(auditoria).registrar(isNull(), anyString(), anyString(), anyInt(), anyString(), anyLong());
    }

    @Test
    void registraMesmoQuandoARequisicaoFalha() {
        logadoComo("ana@empresa.com");
        MockHttpServletRequest requisicao = requisicao("DELETE", "/api/colaboradores/7");

        // Tentativa que estourou é justamente o que o auditor quer ver: não pode se perder.
        assertThatThrownBy(() -> new FiltroAuditoria(auditoria, ips)
            .doFilter(requisicao, new MockHttpServletResponse(),
                (req, resp) -> { throw new IllegalStateException("falha no meio da operação"); }))
            .isInstanceOf(IllegalStateException.class);

        verify(auditoria).registrar(eq("ana@empresa.com"), eq("DELETE"), eq("/api/colaboradores/7"),
            anyInt(), anyString(), anyLong());
    }

    @Test
    void naoConsomeOCorpoDaRequisicao() throws Exception {
        logadoComo("ana@empresa.com");
        MockHttpServletRequest requisicao = requisicao("POST", "/api/sessao");
        requisicao.setContent("{\"email\":\"ana@empresa.com\",\"senha\":\"segredo-em-texto-puro\"}".getBytes());

        passar(requisicao, new MockHttpServletResponse());

        verify(auditoria).registrar(any(), anyString(), anyString(), anyInt(), anyString(), anyLong());
        // O corpo tem de continuar intacto para o controller: se o filtro o lesse, o login
        // quebraria — e, pior, a senha teria passado pela auditoria.
        assertThat(requisicao.getInputStream().readAllBytes())
            .asString()
            .contains("segredo-em-texto-puro");
    }
}
