package br.com.gestao.operadores.seguranca;

import br.com.gestao.operadores.dominio.SupervisorRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * API sem estado: o supervisor faz login em {@code POST /api/sessao} com e-mail e senha e recebe
 * um token JWT de curta duração, enviado no cabeçalho {@code Authorization: Bearer <token>} nas
 * demais requisições. O token é assinado com HMAC (segredo em variável de ambiente).
 *
 * <p>São duas cadeias de filtros, porque o mesmo processo responde a duas coisas diferentes:
 * {@code /api/**} é a API (autenticada, CSP trancada) e todo o resto é o site React embutido no
 * JAR (público — a interface precisa carregar <em>antes</em> do login, com uma CSP que permita
 * carregar os próprios scripts e estilos). Ver {@link #filterChain} e {@link #cadeiaDoSite}.
 */
@Configuration
public class SecurityConfig {

    /** Valor do claim {@code iss}: emitido aqui e exigido na validação. */
    public static final String EMISSOR = "gestao-operadores";

    /**
     * CSP do site. O build do Vite não gera script nem estilo inline, por isso não há
     * {@code 'unsafe-inline'}. {@code connect-src 'self'} basta porque a API é servida por este
     * mesmo processo, na mesma origem; {@code data:} em img-src cobre os ícones SVG embutidos.
     */
    private static final String CSP_DO_SITE = String.join("; ",
        "default-src 'self'",
        "script-src 'self'",
        "style-src 'self'",
        "img-src 'self' data:",
        "font-src 'self'",
        "connect-src 'self'",
        "object-src 'none'",
        "base-uri 'self'",
        "form-action 'self'",
        "frame-ancestors 'none'");

    private static final int TAMANHO_MINIMO_SEGREDO = 32; // HS256 exige chave de 256 bits
    private final SecretKey chave;
    private final List<String> origensPermitidas;

    public SecurityConfig(@Value("${app.jwt.secret}") String segredo,
                          @Value("${app.cors.origens}") String origens) {
        if (segredo == null || segredo.getBytes(StandardCharsets.UTF_8).length < TAMANHO_MINIMO_SEGREDO) {
            throw new IllegalStateException(
                "Defina JWT_SECRET com ao menos " + TAMANHO_MINIMO_SEGREDO + " caracteres.");
        }
        this.chave = new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.origensPermitidas = separarOrigens(origens);
    }

    /**
     * Origens do front liberadas no CORS, separadas por vírgula (CORS_ORIGENS).
     *
     * <p>Vazio é o caso da instalação no servidor da empresa: o site vem do mesmo JAR, na mesma
     * origem, então <b>nenhuma</b> origem externa precisa ser liberada — e aí nem se registra
     * configuração de CORS (ver {@link #corsConfigurationSource()}). Isso é mais restritivo que
     * qualquer valor, não menos: só vale a pena preencher quando o front for publicado em outro
     * domínio (ex.: Vercel).
     *
     * <p>Nunca aceita {@code *}: com credencial no cabeçalho, curinga permitiria que qualquer
     * site chamasse a API em nome de quem estivesse logado.
     */
    private static List<String> separarOrigens(String origens) {
        List<String> lista = Arrays.stream(origens == null ? new String[0] : origens.split(","))
            .map(String::trim)
            .filter(o -> !o.isEmpty())
            .toList();
        if (lista.contains("*")) {
            throw new IllegalStateException("CORS_ORIGENS não pode ser '*': informe as origens exatas.");
        }
        return lista;
    }

    /** Cadeia da API. Primeira na ordem: {@code /api/**} nunca cai na cadeia do site. */
    @Bean
    @Order(1)
    SecurityFilterChain filterChain(HttpSecurity http, Auditoria auditoria,
                                    IpDaRequisicao ips) throws Exception {
        http
            // /actuator entra aqui (e não na cadeia do site) para que a regra abaixo decida
            // endpoint por endpoint: na cadeia do site, tudo seria público, e bastaria alguém
            // expor mais um endpoint no application.properties para abri-lo à rede sem perceber.
            //
            // /error precisa estar numa cadeia que o libere: sem isso, o encaminhamento interno
            // para /error (em qualquer erro) cai em "authenticated" e o erro real vira um 401
            // vazio, mascarando a resposta.
            .securityMatcher("/api/**", "/actuator/**", "/error")
            // Sem sessão nem cookie: a credencial vai no cabeçalho, então não há alvo para CSRF.
            .csrf(csrf -> csrf.disable())
            .cors(c -> {})
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.POST, "/api/sessao").permitAll()
                .requestMatchers("/error").permitAll()
                // Saúde é público para o monitoramento da TI poder consultar sem credencial.
                // Revela só UP/DOWN (show-details=never); qualquer outro endpoint de actuator
                // que venha a ser exposto cai no anyRequest() abaixo e exige token.
                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> {}))
            // API só responde JSON: CSP trancada e sem referrer. (nosniff e frame DENY já são padrão.)
            .headers(h -> h
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                // HSTS: o navegador passa a recusar http:// neste domínio pelo próximo ano. O Spring
                // só emite em requisição https, então em dev o cabeçalho simplesmente não aparece.
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31_536_000))
                // A API não tem interface; nega de saída qualquer sensor que um navegador ofereça.
                .permissionsPolicy(p -> p.policy("camera=(), microphone=(), geolocation=()")))
            // Depois da autorização: é o último ponto em que ainda se sabe quem está autenticado.
            .addFilterAfter(new FiltroAuditoria(auditoria, ips), AuthorizationFilter.class);
        return http.build();
    }

    /**
     * Cadeia do site React embutido no JAR (tudo que não é {@code /api/**}).
     *
     * <p>É pública de propósito: HTML, JS e CSS do front não são dados da empresa, e exigir token
     * para baixá-los tornaria impossível desenhar a própria tela de login. O que protege os dados
     * é a cadeia da API acima — o site só mostra o que o token conseguir buscar lá.
     *
     * <p>Não leva o {@link FiltroAuditoria}: a trilha registra alteração de dados, e encher a
     * tabela com o GET de cada arquivo estático só atrapalharia a leitura dela.
     */
    @Bean
    @Order(2)
    SecurityFilterChain cadeiaDoSite(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a.anyRequest().permitAll())
            .headers(h -> h
                .contentSecurityPolicy(csp -> csp.policyDirectives(CSP_DO_SITE))
                .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31_536_000))
                .permissionsPolicy(p -> p.policy("camera=(), microphone=(), geolocation=()")));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(SupervisorRepository supervisores) {
        return email -> supervisores.findByEmail(email)
            .map(s -> User.withUsername(s.getEmail())
                          .password(s.getSenhaHash())
                          .roles("SUPERVISOR")
                          .build())
            .orElseThrow(() -> new UsernameNotFoundException("Credenciais inválidas"));
    }

    /** Usado pelo endpoint de login para conferir e-mail e senha. */
    @Bean
    AuthenticationManager authenticationManager(UserDetailsService uds, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(uds);
        provider.setPasswordEncoder(encoder);
        provider.setHideUserNotFoundExceptions(true); // não revela se o e-mail existe
        return new ProviderManager(provider);
    }

    @Bean
    JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(chave));
    }

    /**
     * Além da assinatura e da validade, exige que o emissor seja esta aplicação. Sem isso, qualquer
     * token HS256 assinado com a mesma chave — por outro sistema que reaproveitasse o segredo —
     * seria aceito aqui.
     */
    @Bean
    JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decodificador =
            NimbusJwtDecoder.withSecretKey(chave).macAlgorithm(MacAlgorithm.HS256).build();
        decodificador.setJwtValidator(JwtValidators.createDefaultWithIssuer(EMISSOR));
        return decodificador;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (origensPermitidas.isEmpty()) {
            // Front na mesma origem (JAR único): sem registro, nenhuma origem externa é liberada.
            return source;
        }
        CorsConfiguration cfg = new CorsConfiguration();
        // Dev: http://localhost:5173. Front em outro domínio (ex.: Vercel): via CORS_ORIGENS.
        cfg.setAllowedOrigins(origensPermitidas);
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        source.registerCorsConfiguration("/api/**", cfg);
        return source;
    }
}
