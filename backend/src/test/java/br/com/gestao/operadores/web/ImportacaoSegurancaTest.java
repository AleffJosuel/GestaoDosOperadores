package br.com.gestao.operadores.web;

import br.com.gestao.operadores.dominio.Colaborador;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Importação de CSV pelo lado da segurança: o arquivo vem de fora, de um supervisor que recebeu a
 * planilha de alguém. Dois riscos tratados aqui — arquivo que não é CSV e célula que o Excel
 * executaria como fórmula ao reabrir uma exportação futura.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImportacaoSegurancaTest {

    @Mock ColaboradorService colaboradorService;
    @Mock CompetenciaService competenciaService;

    private ImportacaoService importacao;

    @BeforeEach
    void montar() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        importacao = new ImportacaoService(validator, colaboradorService, competenciaService);
        when(colaboradorService.criar(any())).thenReturn(
            new Colaborador(1L, "1001", "Fulano", "OPERADOR", "Injeção", null, null));
    }

    private static MockMultipartFile arquivo(String nome, String tipo, String conteudo) {
        return new MockMultipartFile("arquivo", nome, tipo, conteudo.getBytes(StandardCharsets.UTF_8));
    }

    private static final String CABECALHO = "matricula,nome,cargo,setor\n";

    @Test
    void aceitaCsvNormal() {
        var resultado = importacao.importarColaboradores(
            arquivo("equipe.csv", "text/csv", CABECALHO + "1001,Fulano,OPERADOR,Injeção\n"));

        assertThat(resultado.criados()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"equipe.xlsx", "equipe.xls", "equipe.zip", "script.js", "sem-extensao"})
    void recusaArquivoQueNaoECsv(String nome) {
        assertThatThrownBy(() -> importacao.importarColaboradores(
                arquivo(nome, "text/csv", CABECALHO)))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));

        verify(colaboradorService, never()).criar(any());
    }

    @Test
    void recusaTipoDeConteudoIncompativel() {
        assertThatThrownBy(() -> importacao.importarColaboradores(
                arquivo("equipe.csv", "application/zip", CABECALHO)))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
    }

    /**
     * O nome abaixo é inofensivo no banco e na tela. O estrago aparece quando alguém exporta a
     * lista e abre no Excel: o programa executa a fórmula na máquina de quem abriu.
     */
    @ParameterizedTest
    @ValueSource(strings = {"=1+1", "=HYPERLINK(A1)", "+1+1", "@SUM(A1:A9)", "-2+3"})
    void recusaLinhaComCelulaDeFormula(String nomeMalicioso) {
        var resultado = importacao.importarColaboradores(arquivo("equipe.csv", "text/csv",
            CABECALHO + "1001," + nomeMalicioso + ",OPERADOR,Injeção\n"));

        assertThat(resultado.criados()).isZero();
        assertThat(resultado.falhas()).isEqualTo(1);
        assertThat(resultado.linhas().get(0).mensagem()).contains("fórmula");
        verify(colaboradorService, never()).criar(any());
    }

    @Test
    void recusaApenasALinhaRuimEImportaOResto() {
        var resultado = importacao.importarColaboradores(arquivo("equipe.csv", "text/csv", CABECALHO
            + "1001,=cmd|' /C calc'!A0,OPERADOR,Injeção\n"
            + "1002,Ciclano,OPERADOR,Injeção\n"));

        // Uma planilha com uma célula envenenada não deve inviabilizar a importação inteira.
        assertThat(resultado.criados()).isEqualTo(1);
        assertThat(resultado.falhas()).isEqualTo(1);
    }

    @Test
    void naoConfundeNomeLegitimoComFormula() {
        var resultado = importacao.importarColaboradores(arquivo("equipe.csv", "text/csv",
            CABECALHO + "1001,Maria D'Ávila,OPERADOR,Injeção\n"));

        assertThat(resultado.criados()).isEqualTo(1);
    }
}
