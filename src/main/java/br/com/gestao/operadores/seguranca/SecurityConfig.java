package br.com.gestao.operadores.seguranca;

import br.com.gestao.operadores.dominio.SupervisorRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
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
 */
@Configuration
public class SecurityConfig {

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
     * Nunca aceita {@code *}: com credencial no cabeçalho, curinga permitiria que
     * qualquer site chamasse a API em nome de quem estivesse logado.
     */
    private static List<String> separarOrigens(String origens) {
        List<String> lista = Arrays.stream(origens == null ? new String[0] : origens.split(","))
            .map(String::trim)
            .filter(o -> !o.isEmpty())
            .toList();
        if (lista.isEmpty()) {
            throw new IllegalStateException(
                "Defina CORS_ORIGENS com a origem do front (ex.: https://seu-app.vercel.app).");
        }
        if (lista.contains("*")) {
            throw new IllegalStateException("CORS_ORIGENS não pode ser '*': informe as origens exatas.");
        }
        return lista;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Sem sessão nem cookie: a credencial vai no cabeçalho, então não há alvo para CSRF.
            .csrf(csrf -> csrf.disable())
            .cors(c -> {})
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.POST, "/api/sessao").permitAll()
                // Sem isto, o encaminhamento interno para /error (em qualquer erro) cai em
                // "authenticated" e o erro real vira um 401 vazio, mascarando a resposta.
                .requestMatchers("/error").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> {}))
            // API só responde JSON: CSP trancada e sem referrer. (nosniff e frame DENY já são padrão.)
            .headers(h -> h
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)));
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

    @Bean
    JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(chave).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        // Dev: http://localhost:5173. Produção: o domínio do front (ex.: Vercel), via CORS_ORIGENS.
        cfg.setAllowedOrigins(origensPermitidas);
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cfg);
        return source;
    }
}
