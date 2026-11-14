package br.com.gestao.operadores;

import br.com.gestao.operadores.web.Dtos;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O único teste que sobe a aplicação inteira — Tomcat, Spring Security, Flyway, JPA — e fala com
 * ela por HTTP de verdade.
 *
 * <p><b>Por que ele existe.</b> Os outros testes montam os objetos à mão e são rápidos, mas não
 * conseguem falhar quando a aplicação não <em>inicializa</em>: bean duplicado, cadeia de filtros
 * mal ordenada, propriedade ausente, migration incompatível. Esse tipo de erro passava por todos
 * eles e só aparecia ao rodar o JAR — ou seja, no servidor da empresa. Este teste fecha essa
 * porta: se o contexto não subir, nada aqui passa.
 *
 * <p>Precisa do banco {@code gestao_operadores_teste}. Como criar está no
 * {@code application-teste.properties}, ao lado deste código.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("teste")
class SubidaDaAplicacaoTest {

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    /** O site só está no classpath depois de `npm run build` (ver a cópia no pom.xml). */
    private static void exigeSiteCompilado() {
        Assumptions.assumeTrue(new ClassPathResource("static/index.html").exists(),
            "frontend/dist ausente: rode `npm run build` no frontend para cobrir o site também.");
    }

    /**
     * Desliga o envio do corpo em streaming. Com ele ligado, um 401 num POST faz o cliente HTTP
     * da JDK tentar reautenticar, não conseguir reenviar o corpo já transmitido, e estourar
     * "cannot retry due to server authentication" — que esconde o 401 por trás de um erro de I/O
     * e manda quem investiga para o lado errado.
     */
    @BeforeEach
    void falharDeFormaLegivel() {
        if (http.getRestTemplate().getRequestFactory() instanceof SimpleClientHttpRequestFactory fabrica) {
            fabrica.setOutputStreaming(false);
        }
    }

    /** Corpo da resposta como texto, tratando corpo ausente — 401 costuma vir sem corpo nenhum. */
    private static String corpoDe(ResponseEntity<String> resposta) {
        return resposta.getBody() == null ? "" : resposta.getBody();
    }

    // ------------------------------------------------------------------ a aplicação no ar

    @Test
    void saudeRespondeSemCredencialENaoDetalhaNada() {
        ResponseEntity<String> resposta = http.getForEntity("/actuator/health", String.class);

        // Público de propósito: é o que o monitoramento da TI consulta.
        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains("UP");
        // E só isso. Nome de banco, versão e lista de componentes seriam mapa para quem sonda.
        assertThat(resposta.getBody()).doesNotContain("postgres", "PostgreSQL", "components", "diskSpace");
    }

    @Test
    void demaisEndpointsDeActuatorNaoEstaoExpostos() {
        // Não basta não estarem na lista de exposição: se alguém ampliá-la, a cadeia de segurança
        // tem de continuar exigindo token. Aqui o esperado é 401 (protegido) ou 404 (não exposto),
        // nunca 200.
        for (String endpoint : new String[] {"/actuator/env", "/actuator/beans", "/actuator/configprops"}) {
            ResponseEntity<String> resposta = http.getForEntity(endpoint, String.class);

            assertThat(resposta.getStatusCode()).as(endpoint).isNotEqualTo(HttpStatus.OK);
        }
    }

    // ------------------------------------------------------------------ a API

    @Test
    void apiSemTokenRecusaENaoDevolveHtml() {
        ResponseEntity<String> resposta = http.getForEntity("/api/colaboradores", String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // Se o fallback do SPA alcançasse /api, isto viria 200 com uma página — e o front
        // quebraria ao interpretar HTML onde esperava dados. Na prática o corpo vem vazio,
        // que é melhor ainda: não há o que vazar.
        assertThat(corpoDe(resposta)).doesNotContain("<!doctype html>", "<div id=\"root\">");
    }

    @Test
    void apiMantemCspTrancada() {
        ResponseEntity<String> resposta = http.getForEntity("/api/colaboradores", String.class);

        // A API não tem interface: nada pode ser carregado a partir de uma resposta dela.
        assertThat(resposta.getHeaders().getFirst("Content-Security-Policy"))
            .isEqualTo("default-src 'none'; frame-ancestors 'none'");
    }

    @Test
    void origemExternaNaoRecebeLiberacaoDeCors() {
        // CORS_ORIGENS vazio (como na instalação da empresa): nenhuma origem externa é liberada.
        ResponseEntity<String> resposta = http.getForEntity("/api/colaboradores", String.class);

        assertThat(resposta.getHeaders().getFirst("Access-Control-Allow-Origin")).isNull();
    }

    // ------------------------------------------------------------------ o site

    @Test
    void raizEntregaAInterface() {
        exigeSiteCompilado();

        ResponseEntity<String> resposta = http.getForEntity("/", String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getHeaders().getContentType()).isNotNull();
        assertThat(resposta.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_HTML)).isTrue();
        assertThat(resposta.getBody()).contains("<div id=\"root\">");
    }

    @Test
    void rotaDoSpaSobreviveAoRecarregarAPagina() {
        exigeSiteCompilado();

        // O caso real: o supervisor aperta F5 estando em /mapa. O navegador pede /mapa ao
        // servidor, que não tem arquivo nenhum ali — e precisa devolver a interface.
        for (String rota : new String[] {"/mapa", "/ficha/12", "/configuracoes"}) {
            ResponseEntity<String> resposta = http.getForEntity(rota, String.class);

            assertThat(resposta.getStatusCode()).as(rota).isEqualTo(HttpStatus.OK);
            assertThat(resposta.getBody()).as(rota).contains("<div id=\"root\">");
        }
    }

    @Test
    void siteRecebeCspQuePermiteCarregarOProprioBundle() {
        exigeSiteCompilado();

        String csp = http.getForEntity("/", String.class).getHeaders().getFirst("Content-Security-Policy");

        // Distinta da CSP da API: aqui o próprio script e estilo têm de carregar. O build do Vite
        // não gera nada inline, então 'unsafe-inline' não aparece — e não deve passar a aparecer.
        assertThat(csp).contains("script-src 'self'", "connect-src 'self'", "frame-ancestors 'none'");
        assertThat(csp).doesNotContain("unsafe-inline", "unsafe-eval");
    }

    @Test
    void arquivoInexistenteE404ENaoAInterface() {
        exigeSiteCompilado();

        // Asset que falta tem de ser 404. Devolver a interface faria o navegador acusar erro de
        // sintaxe ao tentar executar HTML como script, muito longe da causa real.
        ResponseEntity<String> resposta = http.getForEntity("/assets/nao-existe.js", String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resposta.getBody()).doesNotContain("<div id=\"root\">");
    }

    // ------------------------------------------------------------------ com sessão

    /** Login de verdade, pela conta que o perfil de teste provisiona no boot. */
    private HttpHeaders comToken() {
        ResponseEntity<Dtos.TokenResponse> login = http.postForEntity("/api/sessao",
            new Dtos.LoginRequest("teste@exemplo.invalid", "Palmeira!Sereno#42xK"),
            Dtos.TokenResponse.class);

        assertThat(login.getStatusCode())
            .as("login da conta de teste — 401 aqui costuma significar que o boot não criou a "
                + "conta: veja no log se a ADMIN_PASSWORD do application-teste.properties foi "
                + "recusada pela política de senha")
            .isEqualTo(HttpStatus.OK);
        assertThat(login.getBody()).isNotNull();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(login.getBody().token());
        return headers;
    }

    private ResponseEntity<byte[]> baixar(String caminho) {
        return http.exchange(caminho, HttpMethod.GET, new HttpEntity<>(comToken()), byte[].class);
    }

    @Test
    void exportacaoDoMapaVemComoArquivoCsvQueOExcelAbre() {
        ResponseEntity<byte[]> resposta = baixar("/api/mapa/exportar");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getHeaders().getContentType()).isNotNull();
        assertThat(resposta.getHeaders().getContentType().toString()).startsWith("text/csv");
        // attachment + nome com data: o navegador salva em vez de exibir, e o RH recebe um
        // arquivo que se identifica sozinho.
        assertThat(resposta.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
            .contains("attachment", "mapa-de-competencias", LocalDate.now().toString());
        // Dados pessoais de desempenho não podem ficar em cache de proxy.
        assertThat(resposta.getHeaders().getCacheControl()).contains("no-store");

        String csv = new String(resposta.getBody(), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFF");
        assertThat(csv).contains("Colaborador;Cargo;Setor;Média;Classificação");
    }

    @Test
    void exportacaoDoDashboardTrazOsIndicadores() {
        ResponseEntity<byte[]> resposta = baixar("/api/dashboard/exportar");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        String csv = new String(resposta.getBody(), StandardCharsets.UTF_8);
        assertThat(csv).contains("Indicador;Valor", "Colaboradores ativos", "Faixa de classificação");
    }

    @Test
    void exportacaoSemTokenNaoVazaNada() {
        ResponseEntity<String> resposta = http.getForEntity("/api/mapa/exportar", String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(corpoDe(resposta)).doesNotContain("Colaborador;", "Média");
    }

    @Test
    void exportacaoFicaNaTrilhaDeAuditoria() {
        baixar("/api/mapa/exportar");

        // Exportar tira dados do sistema: é o registro mais valioso para quem apura vazamento.
        Integer registros = jdbc.queryForObject("""
            SELECT count(*) FROM auditoria
             WHERE caminho = '/api/mapa/exportar' AND metodo = 'GET' AND autor_email = ?
            """, Integer.class, "teste@exemplo.invalid");

        assertThat(registros).isPositive();
    }

    @Test
    void consultaAFichaFicaNaTrilhaDeAuditoria() {
        // Colaborador 999999 não existe: dá 404, e é justamente o que se quer provar — a trilha
        // registra a TENTATIVA de consultar os dados de alguém, não só a consulta bem-sucedida.
        http.exchange("/api/colaboradores/999999", HttpMethod.GET,
            new HttpEntity<>(comToken()), String.class);

        Integer registros = jdbc.queryForObject("""
            SELECT count(*) FROM auditoria
             WHERE caminho = '/api/colaboradores/999999' AND metodo = 'GET'
            """, Integer.class);

        assertThat(registros).isPositive();
    }

    @Test
    void leituraDeConjuntoNaoPoluiATrilha() {
        http.exchange("/api/mapa", HttpMethod.GET, new HttpEntity<>(comToken()), String.class);

        // O mapa é aberto a cada navegação: registrá-lo afogaria a trilha.
        Integer registros = jdbc.queryForObject(
            "SELECT count(*) FROM auditoria WHERE caminho = '/api/mapa' AND metodo = 'GET'",
            Integer.class);

        assertThat(registros).isZero();
    }
}
