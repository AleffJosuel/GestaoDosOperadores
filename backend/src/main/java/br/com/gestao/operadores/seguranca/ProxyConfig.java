package br.com.gestao.operadores.seguranca;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ForwardedHeaderFilter;

/**
 * Faz a aplicação enxergar a requisição como o cliente a enviou quando há um proxy na frente
 * terminando o TLS. Sem isto, {@code request.isSecure()} é falso mesmo em acesso por https — e o
 * Spring Security, que só emite HSTS em requisição segura, deixaria de mandar o cabeçalho
 * justamente em produção, onde ele importa.
 *
 * <p>Só existe quando {@code ATRAS_DE_PROXY=true}. Ligado por padrão seria uma brecha: os
 * cabeçalhos {@code X-Forwarded-*} vêm do cliente, e num acesso direto qualquer um poderia dizer
 * "minha requisição é https e meu IP é outro".
 */
@Configuration
@ConditionalOnProperty(name = "app.proxy.confiavel", havingValue = "true")
public class ProxyConfig {

    @Bean
    ForwardedHeaderFilter forwardedHeaderFilter() {
        return new ForwardedHeaderFilter();
    }
}
