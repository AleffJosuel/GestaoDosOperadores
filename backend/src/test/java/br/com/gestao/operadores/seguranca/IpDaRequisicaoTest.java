package br.com.gestao.operadores.seguranca;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * De qual IP veio a requisição. Parece detalhe, mas define se o freio contra força bruta funciona
 * (em produção, atrás de proxy) ou se pode ser burlado (em acesso direto, com cabeçalho forjado).
 */
class IpDaRequisicaoTest {

    private static MockHttpServletRequest requisicao(String remoto, String encaminhado) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr(remoto);
        if (encaminhado != null) {
            req.addHeader("X-Forwarded-For", encaminhado);
        }
        return req;
    }

    @Test
    void semProxyUsaOEnderecoDaConexao() {
        IpDaRequisicao ips = new IpDaRequisicao(false);

        assertThat(ips.de(requisicao("203.0.113.9", null))).isEqualTo("203.0.113.9");
    }

    @Test
    void semProxyIgnoraCabecalhoForjado() {
        IpDaRequisicao ips = new IpDaRequisicao(false);

        // Sem proxy, confiar no cabeçalho deixaria o atacante inventar um IP novo por tentativa e
        // nunca bater no limite de login.
        assertThat(ips.de(requisicao("203.0.113.9", "1.2.3.4"))).isEqualTo("203.0.113.9");
    }

    @Test
    void comProxyUsaOPrimeiroDaCadeia() {
        IpDaRequisicao ips = new IpDaRequisicao(true);

        // "cliente, proxy1, proxy2": o cliente real é o primeiro.
        assertThat(ips.de(requisicao("10.0.0.1", "198.51.100.7, 10.0.0.2, 10.0.0.1")))
            .isEqualTo("198.51.100.7");
    }

    @Test
    void comProxySemCabecalhoCaiNoEnderecoDaConexao() {
        IpDaRequisicao ips = new IpDaRequisicao(true);

        assertThat(ips.de(requisicao("10.0.0.1", null))).isEqualTo("10.0.0.1");
    }

    @Test
    void comProxyECabecalhoVazioCaiNoEnderecoDaConexao() {
        IpDaRequisicao ips = new IpDaRequisicao(true);

        assertThat(ips.de(requisicao("10.0.0.1", "   "))).isEqualTo("10.0.0.1");
    }

    @Test
    void nuncaDevolveNulo() {
        // Chave nula explodiria o limitador e, com ele, o login inteiro.
        assertThat(new IpDaRequisicao(false).de(requisicao(null, null))).isEqualTo("desconhecido");
    }
}
