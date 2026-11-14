package br.com.gestao.operadores.seguranca;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Origens de CORS vindas do ambiente (CORS_ORIGENS). O valor deixou de ser fixo em
 * localhost para a API poder ser publicada com o front em outro domínio.
 */
class SecurityConfigTest {

    private static final String SEGREDO = "segredo-de-teste-com-mais-de-32-caracteres";

    private static UrlBasedCorsConfigurationSource fonteDeCors(String origens) {
        return (UrlBasedCorsConfigurationSource)
            new SecurityConfig(SEGREDO, origens).corsConfigurationSource();
    }

    private static CorsConfiguration corsDaApi(String origens) {
        return fonteDeCors(origens).getCorsConfigurations().get("/api/**");
    }

    @Test
    void aceitaVariasOrigensSeparadasPorVirgula() {
        CorsConfiguration cors = corsDaApi("https://app.vercel.app, http://localhost:5173");

        assertThat(cors.getAllowedOrigins())
            .containsExactly("https://app.vercel.app", "http://localhost:5173");
    }

    @Test
    void ignoraEspacosEItensVazios() {
        CorsConfiguration cors = corsDaApi("  https://app.vercel.app ,, ");

        assertThat(cors.getAllowedOrigins()).containsExactly("https://app.vercel.app");
    }

    /**
     * Instalação no servidor da empresa: o site vem do mesmo JAR, na mesma origem, e CORS_ORIGENS
     * fica vazio. Nada deve ser registrado — sem configuração de CORS, o navegador recusa qualquer
     * chamada vinda de outra origem, que é exatamente o que se quer aqui.
     */
    @Test
    void semOrigemNenhumaNaoRegistraCors() {
        assertThat(fonteDeCors("   ").getCorsConfigurations()).isEmpty();
        assertThat(fonteDeCors(null).getCorsConfigurations()).isEmpty();
    }

    @Test
    void recusaCuringa() {
        // Com credencial no cabeçalho, '*' deixaria qualquer site chamar a API.
        assertThatThrownBy(() -> corsDaApi("*"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("não pode ser '*'");
    }

    @Test
    void segredoCurtoContinuaDerrubandoAAplicacao() {
        assertThatThrownBy(() -> new SecurityConfig("curto", "http://localhost:5173"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void corsValeSoParaApiENaoLiberaMetodoInesperado() {
        CorsConfiguration cors = corsDaApi("https://app.vercel.app");

        assertThat(cors.getAllowedMethods()).containsExactly("GET", "POST", "PUT", "DELETE", "OPTIONS");
        assertThat(cors.getAllowedHeaders()).containsExactly("Authorization", "Content-Type");
    }

    // ----------------------------------------------------------------- token (JWT)

    private static SecurityConfig config() {
        return new SecurityConfig(SEGREDO, "http://localhost:5173");
    }

    private static String tokenCom(JwtEncoder encoder, String emissor, Instant expiraEm) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(emissor)
            .issuedAt(expiraEm.minus(1, ChronoUnit.HOURS))
            .expiresAt(expiraEm)
            .subject("supervisor@empresa.com")
            .build();
        return encoder.encode(JwtEncoderParameters.from(
            JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    @Test
    void aceitaOProprioTokenValido() {
        SecurityConfig config = config();
        String token = tokenCom(config.jwtEncoder(), SecurityConfig.EMISSOR,
            Instant.now().plus(1, ChronoUnit.HOURS));

        assertThat(config.jwtDecoder().decode(token).getSubject()).isEqualTo("supervisor@empresa.com");
    }

    @Test
    void recusaTokenDeOutroEmissorAindaQueBemAssinado() {
        // Cenário real: outro sistema reaproveita o mesmo JWT_SECRET. A assinatura confere, mas o
        // token não foi emitido para esta aplicação e não deve abrir as portas dela.
        SecurityConfig config = config();
        String token = tokenCom(config.jwtEncoder(), "outro-sistema",
            Instant.now().plus(1, ChronoUnit.HOURS));

        assertThatThrownBy(() -> config.jwtDecoder().decode(token))
            .isInstanceOf(JwtException.class);
    }

    @Test
    void recusaTokenExpirado() {
        SecurityConfig config = config();
        String token = tokenCom(config.jwtEncoder(), SecurityConfig.EMISSOR,
            Instant.now().minus(1, ChronoUnit.MINUTES));

        assertThatThrownBy(() -> config.jwtDecoder().decode(token))
            .isInstanceOf(JwtException.class);
    }

    @Test
    void recusaTokenAssinadoComOutroSegredo() {
        SecurityConfig outroServidor = new SecurityConfig(
            "outro-segredo-de-teste-com-mais-de-32-caracteres", "http://localhost:5173");
        String token = tokenCom(outroServidor.jwtEncoder(), SecurityConfig.EMISSOR,
            Instant.now().plus(1, ChronoUnit.HOURS));

        assertThatThrownBy(() -> config().jwtDecoder().decode(token))
            .isInstanceOf(JwtException.class);
    }
}
