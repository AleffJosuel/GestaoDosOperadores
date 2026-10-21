package br.com.gestao.operadores.seguranca;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Origens de CORS vindas do ambiente (CORS_ORIGENS). O valor deixou de ser fixo em
 * localhost para a API poder ser publicada com o front em outro domínio.
 */
class SecurityConfigTest {

    private static final String SEGREDO = "segredo-de-teste-com-mais-de-32-caracteres";

    private static CorsConfiguration corsDaApi(String origens) {
        UrlBasedCorsConfigurationSource fonte =
            (UrlBasedCorsConfigurationSource) new SecurityConfig(SEGREDO, origens).corsConfigurationSource();
        return fonte.getCorsConfigurations().get("/api/**");
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

    @Test
    void naoSobeSemNenhumaOrigem() {
        assertThatThrownBy(() -> corsDaApi("   "))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("CORS_ORIGENS");
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
}
