package br.com.gestao.operadores.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Roteamento do site embutido no JAR, exercitado pelo mesmo handler que o Spring usa em produção
 * (e não só pelo resolvedor): é essa composição que decide o que o navegador recebe.
 *
 * <p>O que está em jogo é o recarregar a página numa rota do React Router — sem o fallback, o
 * supervisor que aperta F5 em {@code /mapa} vê um erro em vez da tela.
 */
class SiteConfigTest {

    @TempDir
    Path site;

    private ResourceHttpRequestHandler handler;

    /** Monta o equivalente ao {@code /static} do JAR: um asset, o index e nada mais. */
    @BeforeEach
    void prepararSite() throws Exception {
        Files.createDirectories(site.resolve("assets"));
        Files.writeString(site.resolve("assets").resolve("app.js"), "console.log(1)");
        Files.writeString(site.resolve("index.html"), "<!doctype html><title>Gestão</title>");
        Files.writeString(site.getParent().resolve("segredo.txt"), "nao-deveria-sair");

        handler = new ResourceHttpRequestHandler();
        handler.setLocations(List.of(new FileSystemResource(site.toString() + "/")));
        handler.setResourceResolvers(List.of(new SiteConfig.ResolvedorDeRotasDoSpa()));
        handler.setServletContext(new MockServletContext());
        handler.afterPropertiesSet();
    }

    private MockHttpServletResponse get(String caminho) throws Exception {
        MockHttpServletRequest requisicao = new MockHttpServletRequest("GET", caminho);
        // O handler lê o caminho já resolvido pelo mapeamento, como em produção.
        requisicao.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE,
            caminho.startsWith("/") ? caminho.substring(1) : caminho);
        MockHttpServletResponse resposta = new MockHttpServletResponse();
        handler.handleRequest(requisicao, resposta);
        return resposta;
    }

    @Test
    void arquivoQueExisteEServidoComoEle() throws Exception {
        MockHttpServletResponse resposta = get("/assets/app.js");

        assertThat(resposta.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(resposta.getContentAsString()).isEqualTo("console.log(1)");
    }

    @Test
    void rotaDoSpaCaiNoIndexHtml() throws Exception {
        // Rota simples e rota com parâmetro: nenhuma das duas é um arquivo no disco.
        for (String rota : List.of("/mapa", "/ficha/12", "/configuracoes")) {
            MockHttpServletResponse resposta = get(rota);

            assertThat(resposta.getStatus()).as(rota).isEqualTo(HttpStatus.OK.value());
            assertThat(resposta.getContentAsString()).as(rota).contains("<!doctype html>");
            assertThat(resposta.getContentType()).as(rota).startsWith("text/html");
        }
    }

    @Test
    void chamadaDeApiInexistenteNaoRecebeHtml() {
        // Precisa continuar 404 (JSON, pelo ErrosHandler): um 200 com HTML no lugar dos dados
        // faria o front falhar ao interpretar a resposta, sem dizer que o endereço estava errado.
        assertThatThrownBy(() -> get("/api/colaboradores"))
            .isInstanceOf(NoResourceFoundException.class);
    }

    @Test
    void assetQueFaltaE404ENaoIndexHtml() {
        // Se um script sumisse do JAR e viesse HTML no lugar, o erro apareceria como sintaxe
        // inválida no console do navegador, muito longe da causa real.
        assertThatThrownBy(() -> get("/assets/sumiu.js")).isInstanceOf(NoResourceFoundException.class);
        assertThatThrownBy(() -> get("/favicon.svg")).isInstanceOf(NoResourceFoundException.class);
    }

    @Test
    void naoEscapaDaPastaDoSite() {
        // O '..' é recusado pelo ResourceHttpRequestHandler antes de chegar ao resolvedor — o
        // resolvedor sozinho não garante isto, porque compara prefixo de caminho e
        // ".../site/../segredo.txt" começa com ".../site/". Daí este teste ser no handler.
        assertThatThrownBy(() -> get("/../segredo.txt")).isInstanceOf(NoResourceFoundException.class);
        assertThatThrownBy(() -> get("/%2e%2e/segredo.txt")).isInstanceOf(NoResourceFoundException.class);
    }

    // A raiz "/" não passa por este handler: quem a atende é o WelcomePageHandlerMapping do
    // Spring Boot, que encontra o classpath:/static/index.html e tem precedência maior. Fica
    // coberta pela verificação do JAR no ar (ver o guia de implantação), não por teste de unidade.
}
