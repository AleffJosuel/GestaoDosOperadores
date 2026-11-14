package br.com.gestao.operadores.web;

import br.com.gestao.operadores.dominio.TipoCompetencia;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Exportação em CSV. O que se testa aqui é o que decide se o arquivo é utilizável: separador e
 * decimal que o Excel em português entende, acento preservado, célula sem nota vazia (e não zero)
 * e escape de campo que contém o próprio separador.
 */
@ExtendWith(MockitoExtension.class)
class ExportacaoServiceTest {

    @Mock PainelService painel;

    private static final Dtos.ClassificacaoDto BOM_ = new Dtos.ClassificacaoDto("BOM", "Bom");

    private ExportacaoService service() {
        return new ExportacaoService(painel);
    }

    private static Dtos.MapaResponse mapaDeExemplo() {
        return new Dtos.MapaResponse(
            List.of(new Dtos.MapaCompetencia(1L, "Comunicação", TipoCompetencia.COMPORTAMENTAL, 5, 3.5),
                    new Dtos.MapaCompetencia(2L, "Operação de Injetora", TipoCompetencia.TECNICA, 4, 4.0)),
            List.of(new Dtos.MapaLinha(10L, "José da Silva", "Operador II; Turno A", "Injeção",
                        4.0, BOM_,
                        // Segunda competência sem nota: o colaborador ainda não foi avaliado nela.
                        List.of(new Dtos.MapaCelula(1L, 4, BOM_),
                                new Dtos.MapaCelula(2L, null, null)))));
    }

    // ------------------------------------------------------------------------- mapa

    @Test
    void mapaSaiComSeparadorPontoEVirgulaEDecimalComVirgula() {
        when(painel.mapa()).thenReturn(mapaDeExemplo());

        String csv = service().mapaEmCsv();

        // Com separador vírgula, o Excel pt-BR joga a linha toda numa única coluna.
        assertThat(csv).contains("Colaborador;Cargo;Setor;Média;Classificação;Comunicação;Operação de Injetora");
        // E lê "4.00" como texto, não como número.
        assertThat(csv).contains("4,00");
        assertThat(csv).doesNotContain("4.00");
    }

    @Test
    void mapaComecaComBomParaOExcelNaoComerOsAcentos() {
        when(painel.mapa()).thenReturn(mapaDeExemplo());

        String csv = service().mapaEmCsv();

        assertThat(csv).startsWith("\uFEFF");
        assertThat(csv).contains("Comunicação", "Injeção", "José da Silva");
    }

    @Test
    void campoComOSeparadorDentroVaiEntreAspas() {
        when(painel.mapa()).thenReturn(mapaDeExemplo());

        String csv = service().mapaEmCsv();

        // Sem as aspas, "Operador II; Turno A" empurraria todas as colunas à direita e o arquivo
        // ficaria desalinhado a partir dali — erro silencioso, que só se nota conferindo à mão.
        assertThat(csv).contains("\"Operador II; Turno A\"");
    }

    @Test
    void celulaSemNotaFicaVaziaENaoZero() {
        when(painel.mapa()).thenReturn(mapaDeExemplo());

        String csv = service().mapaEmCsv();
        String linhaDoJose = csv.lines().filter(l -> l.startsWith("José")).findFirst().orElseThrow();

        // Zero seria lido como nota baixíssima; o caso real é "ainda não avaliado".
        assertThat(linhaDoJose).endsWith(";4;");
        assertThat(linhaDoJose).doesNotContain(";0");
    }

    @Test
    void mapaSemColaboradoresSaiSoComOCabecalho() {
        when(painel.mapa()).thenReturn(new Dtos.MapaResponse(List.of(), List.of()));

        String csv = service().mapaEmCsv();

        assertThat(csv.lines()).hasSize(1);
        assertThat(csv).contains("Colaborador;Cargo;Setor;Média;Classificação");
    }

    // -------------------------------------------------------------------- dashboard

    @Test
    void dashboardTrazIndicadoresDistribuicaoECriticas() {
        when(painel.dashboard()).thenReturn(new Dtos.DashboardResponse(
            12, 8, 34L, 10, 3.45,
            List.of(new Dtos.FaixaContagem(BOM_, 7L)),
            List.of(new Dtos.CompetenciaMedia(1L, "Comunicação", 2.3)),
            5L, 2L, 7L));

        String csv = service().dashboardEmCsv();

        assertThat(csv).contains("Colaboradores ativos;12");
        assertThat(csv).contains("Média geral;3,45");
        assertThat(csv).contains("Ações de PDI atrasadas;2");
        assertThat(csv).contains("Faixa de classificação;Colaboradores");
        assertThat(csv).contains("Bom;7");
        assertThat(csv).contains("Competência mais crítica;Média");
        assertThat(csv).contains("Comunicação;2,30");
    }

    @Test
    void dashboardSemDadosNaoQuebra() {
        when(painel.dashboard()).thenReturn(new Dtos.DashboardResponse(
            0, 0, 0L, 0, null, List.of(), List.of(), 0L, 0L, 0L));

        String csv = service().dashboardEmCsv();

        // Média nula vira campo vazio, não "null" nem "0,00" — que diria algo falso sobre a equipe.
        assertThat(csv).contains("Média geral;\r\n");
        assertThat(csv).doesNotContain("null");
    }
}
