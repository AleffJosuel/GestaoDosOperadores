package br.com.gestao.operadores.seguranca;

import br.com.gestao.operadores.seguranca.LimitadorTentativas.Limites;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LimitadorTentativasTest {

    @Test
    void permiteAteOLimiteEBloqueiaDepois() {
        LimitadorTentativas limitador = new LimitadorTentativas();
        int limite = Limites.LOGIN_POR_IP.maxTentativas();

        for (int i = 1; i <= limite; i++) {
            assertThat(limitador.registrarEPermitir(Limites.LOGIN_POR_IP, "10.0.0.1"))
                .as("tentativa %d de %d", i, limite)
                .isTrue();
        }
        assertThat(limitador.registrarEPermitir(Limites.LOGIN_POR_IP, "10.0.0.1")).isFalse();
    }

    @Test
    void contaSeparadoPorChave() {
        LimitadorTentativas limitador = new LimitadorTentativas();
        for (int i = 0; i < Limites.LOGIN_POR_IP.maxTentativas(); i++) {
            limitador.registrarEPermitir(Limites.LOGIN_POR_IP, "10.0.0.1");
        }

        // Um IP estourado não pode bloquear o login de outro — era o efeito de usar o IP do proxy
        // como chave única, que travaria a empresa inteira no primeiro ataque.
        assertThat(limitador.registrarEPermitir(Limites.LOGIN_POR_IP, "10.0.0.2")).isTrue();
    }

    @Test
    void contaSeparadoPorTipoDeLimite() {
        LimitadorTentativas limitador = new LimitadorTentativas();
        for (int i = 0; i < Limites.TROCA_SENHA.maxTentativas(); i++) {
            limitador.registrarEPermitir(Limites.TROCA_SENHA, "ana@empresa.com");
        }

        assertThat(limitador.registrarEPermitir(Limites.TROCA_SENHA, "ana@empresa.com")).isFalse();
        // Mesma chave, outro limite: a troca de senha bloqueada não impede a pessoa de entrar.
        assertThat(limitador.registrarEPermitir(Limites.LOGIN_POR_CONTA, "ana@empresa.com")).isTrue();
    }

    @Test
    void trocaSenhaEhMaisRestritaQueLogin() {
        // Login tem de tolerar o erro de digitação do dia a dia; a troca de senha, não: ali o
        // atacante já tem um token e está tentando adivinhar a senha atual.
        assertThat(Limites.TROCA_SENHA.maxTentativas())
            .isLessThan(Limites.LOGIN_POR_IP.maxTentativas());
    }

    /**
     * Cenário do ataque real: milhares de IPs diferentes batendo no login, com um deles (ou o
     * usuário legítimo) tentando no meio. Duas garantias ao mesmo tempo — a memória para de
     * crescer, e a contagem de quem está ativo no meio da enxurrada não se perde.
     */
    @Test
    void naoCresceSemLimiteESeguePunindoAChaveAtivaNoMeioDaRajada() {
        LimitadorTentativas limitador = new LimitadorTentativas();
        String chaveAtiva = "198.51.100.7";
        int limite = Limites.LOGIN_POR_IP.maxTentativas();
        int rajada = 30_000;
        int intervalo = rajada / limite;
        int tentativasDaChaveAtiva = 0;

        for (int i = 0; i < rajada; i++) {
            limitador.registrarEPermitir(Limites.LOGIN_POR_IP, "203.0.113." + i);
            if (i % intervalo == 0) {
                tentativasDaChaveAtiva++;
                assertThat(limitador.registrarEPermitir(Limites.LOGIN_POR_IP, chaveAtiva))
                    .as("tentativa %d da chave ativa", tentativasDaChaveAtiva)
                    .isTrue();
            }
        }

        assertThat(tentativasDaChaveAtiva).isEqualTo(limite);
        assertThat(limitador.chavesVivas())
            .as("o mapa precisa parar de crescer, senão a rajada derruba a aplicação")
            .isLessThanOrEqualTo(10_000);
        // A chave usada no meio da rajada não foi descartada: estourou o limite e está bloqueada.
        assertThat(limitador.registrarEPermitir(Limites.LOGIN_POR_IP, chaveAtiva)).isFalse();
    }
}
