package br.com.gestao.operadores.web;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Exportação do mapa e do dashboard em CSV, para o supervisor levar os números ao RH.
 *
 * <p>Parte do {@link PainelService}, e não de consultas próprias, de propósito: regra de
 * classificação, escolha da nota mais recente e cálculo de média ficam num só lugar. Export que
 * recalcula por conta própria acaba divergindo da tela, e aí ninguém sabe qual número está certo.
 *
 * <p>O formato é o que o Excel em português abre sem pedir nada: separador {@code ;}, decimal com
 * vírgula e BOM no início do arquivo. Com separador vírgula, o Excel pt-BR joga a linha inteira
 * numa só coluna; sem o BOM, come os acentos. É também o formato que o importador do próprio
 * sistema reconhece, então o arquivo exportado pode voltar para cá.
 */
@Service
public class ExportacaoService {

    /** O Excel só reconhece o arquivo como UTF-8 se ele começar com esta marca. */
    static final String BOM = "\uFEFF";
    private static final char SEPARADOR = ';';
    private static final String FIM_DE_LINHA = "\r\n";

    private final PainelService painel;

    public ExportacaoService(PainelService painel) {
        this.painel = painel;
    }

    public String mapaEmCsv() {
        Dtos.MapaResponse mapa = painel.mapa();
        StringBuilder csv = new StringBuilder(BOM);

        List<String> cabecalho = new java.util.ArrayList<>(
            List.of("Colaborador", "Cargo", "Setor", "Média", "Classificação"));
        for (Dtos.MapaCompetencia c : mapa.competencias()) {
            cabecalho.add(c.nome());
        }
        linha(csv, cabecalho);

        for (Dtos.MapaLinha l : mapa.colaboradores()) {
            List<String> campos = new java.util.ArrayList<>();
            campos.add(l.nome());
            campos.add(l.cargo());
            campos.add(l.setor());
            campos.add(numero(l.media()));
            campos.add(l.classificacao() == null ? "" : l.classificacao().rotulo());
            // Percorre as colunas na ordem do cabeçalho; célula sem nota fica vazia, e não zero —
            // zero seria lido como nota baixa, quando o caso é "ainda não avaliado".
            for (Dtos.MapaCelula celula : l.celulas()) {
                campos.add(celula.nota() == null ? "" : String.valueOf(celula.nota()));
            }
            linha(csv, campos);
        }
        return csv.toString();
    }

    public String dashboardEmCsv() {
        Dtos.DashboardResponse d = painel.dashboard();
        StringBuilder csv = new StringBuilder(BOM);

        linha(csv, List.of("Indicador", "Valor"));
        linha(csv, List.of("Colaboradores ativos", String.valueOf(d.colaboradoresAtivos())));
        linha(csv, List.of("Competências ativas", String.valueOf(d.competenciasAtivas())));
        linha(csv, List.of("Avaliações registradas", String.valueOf(d.totalAvaliacoes())));
        linha(csv, List.of("Colaboradores avaliados", String.valueOf(d.colaboradoresAvaliados())));
        linha(csv, List.of("Média geral", numero(d.mediaGeral())));
        linha(csv, List.of("Ações de PDI abertas", String.valueOf(d.pdiAbertas())));
        linha(csv, List.of("Ações de PDI atrasadas", String.valueOf(d.pdiAtrasadas())));
        linha(csv, List.of("Ações de PDI concluídas", String.valueOf(d.pdiConcluidas())));

        // Linha vazia separa as seções: o Excel mantém cada bloco com o próprio cabeçalho.
        csv.append(FIM_DE_LINHA);
        linha(csv, List.of("Faixa de classificação", "Colaboradores"));
        for (Dtos.FaixaContagem f : d.distribuicao()) {
            linha(csv, List.of(f.classificacao().rotulo(), String.valueOf(f.quantidade())));
        }

        csv.append(FIM_DE_LINHA);
        linha(csv, List.of("Competência mais crítica", "Média"));
        for (Dtos.CompetenciaMedia c : d.competenciasCriticas()) {
            linha(csv, List.of(c.nome(), numero(c.media())));
        }
        return csv.toString();
    }

    private static void linha(StringBuilder csv, List<String> campos) {
        for (int i = 0; i < campos.size(); i++) {
            if (i > 0) {
                csv.append(SEPARADOR);
            }
            csv.append(escapar(campos.get(i)));
        }
        csv.append(FIM_DE_LINHA);
    }

    /**
     * Aspas em volta do campo quando ele contém separador, aspas ou quebra de linha — e aspas
     * internas dobradas, como manda o formato. Sem isso, um cargo como "Operador II; Turno A"
     * deslocaria todas as colunas à direita dele.
     */
    private static String escapar(String valor) {
        if (valor == null) {
            return "";
        }
        boolean precisa = valor.indexOf(SEPARADOR) >= 0 || valor.indexOf('"') >= 0
            || valor.indexOf('\n') >= 0 || valor.indexOf('\r') >= 0;
        if (!precisa) {
            return valor;
        }
        return '"' + valor.replace("\"", "\"\"") + '"';
    }

    /** Decimal com vírgula: é o que o Excel em português entende como número. */
    private static String numero(Double valor) {
        return valor == null ? "" : String.format(Locale.of("pt", "BR"), "%.2f", valor);
    }
}
