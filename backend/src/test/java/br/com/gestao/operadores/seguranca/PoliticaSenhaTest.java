package br.com.gestao.operadores.seguranca;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A política de senha é a única barreira contra a senha fraca que o supervisor escolheria por
 * conveniência. Estes testes fixam o que ela aceita e o que recusa, para que afrouxar a regra exija
 * apagar um teste — e não acontecer por descuido.
 */
class PoliticaSenhaTest {

    // Sem a palavra "supervisor" no e-mail: ela está na lista de trechos previsíveis e a recusa
    // viria por aquele motivo, não pelo e-mail, mascarando o teste da regra do e-mail.
    private static final String EMAIL = "marcos.ferreira@empresa.com.br";

    @Test
    void aceitaSenhaComTamanhoEVariedade() {
        assertThat(PoliticaSenha.aceitavel("Tr3vo!Chuva#Pedra", EMAIL)).isTrue();
    }

    @Test
    void aceitaFraseLongaSemSimboloNemMaiuscula() {
        // Entropia vem do tamanho: exigir símbolo numa frase de 20+ caracteres só piora a memória
        // do usuário (e leva ao papel grudado no monitor), sem ganho real.
        assertThat(PoliticaSenha.aceitavel("o cafe da fabrica esfriou", EMAIL)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"curta123", "Ab!2", ""})
    void recusaSenhaCurta(String senha) {
        assertThat(PoliticaSenha.problema(senha, EMAIL)).isPresent();
    }

    @Test
    void recusaSenhaSoComMinusculaQuandoNaoEFraseLonga() {
        // 14 caracteres, uma única classe: abaixo do limiar que dispensa variedade.
        assertThat(PoliticaSenha.problema("pneumotoraxico", EMAIL))
            .hasValueSatisfying(m -> assertThat(m).contains("tipos de caractere"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "MinhaSenha@2026",      // contém "senha"
        "Password!2026x",       // contém "password"
        "Admin#Fabrica7",       // contém "admin"
        "Gestao@Operador1"})    // contém o nome do próprio sistema
    void recusaTrechosPrevisiveis(String senha) {
        assertThat(PoliticaSenha.problema(senha, EMAIL))
            .hasValueSatisfying(m -> assertThat(m).contains("previsíveis"));
    }

    @Test
    void recusaTrechoPrevisivelMesmoComAcentoOuMaiuscula() {
        // Sem normalizar, "Senhã" passaria pela lista e a regra viraria enfeite.
        assertThat(PoliticaSenha.problema("MinhaSenhã@2026", EMAIL)).isPresent();
    }

    @Test
    void recusaRepeticaoLonga() {
        assertThat(PoliticaSenha.problema("Trevoooo!Chuva", EMAIL))
            .hasValueSatisfying(m -> assertThat(m).contains("vezes seguidas"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Trevo!1234Chuva", "Trevo!4321Chuva", "Trevo!abcdChuva"})
    void recusaSequencias(String senha) {
        assertThat(PoliticaSenha.problema(senha, EMAIL))
            .hasValueSatisfying(m -> assertThat(m).contains("sequências"));
    }

    @Test
    void aceitaSequenciaCurtaDeTresCaracteres() {
        // "123" aparece em senha razoável; só a partir de quatro em sequência vira padrão óbvio.
        assertThat(PoliticaSenha.aceitavel("Trevo!123Chuva", EMAIL)).isTrue();
    }

    @Test
    void recusaSenhaQueContemOEmailDaConta() {
        assertThat(PoliticaSenha.problema("Qta9!marcos.ferreira", EMAIL))
            .hasValueSatisfying(m -> assertThat(m).contains("e-mail"));
    }

    @Test
    void ignoraParteLocalCurtaDoEmail() {
        // Com "aj@gmail.com", exigir que a senha não contenha "aj" recusaria qualquer senha com
        // essas duas letras seguidas — regra inútil que só geraria recusa inexplicável.
        assertThat(PoliticaSenha.aceitavel("Trevo!Chuva#Pedra", "aj@gmail.com")).isTrue();
    }

    @Test
    void naoQuebraQuandoEmailEhNuloOuSemArroba() {
        assertThat(PoliticaSenha.aceitavel("Tr3vo!Chuva#Pedra", null)).isTrue();
        assertThat(PoliticaSenha.aceitavel("Tr3vo!Chuva#Pedra", "sem-arroba")).isTrue();
    }

    @Test
    void recusaSenhaNula() {
        assertThat(PoliticaSenha.problema(null, EMAIL)).isPresent();
    }
}
