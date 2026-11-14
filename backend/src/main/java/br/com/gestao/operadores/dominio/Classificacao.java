package br.com.gestao.operadores.dominio;

import java.math.BigDecimal;

/**
 * Faixa de classificação de uma média (1–5), definida pelos limites de
 * {@link ConfiguracaoClassificacao}. "Excelente" (nota 5) não é uma faixa de média:
 * fica reservada à nota individual.
 */
public enum Classificacao {
    CRITICO("Crítico"),
    A_DESENVOLVER("A Desenvolver"),
    ADEQUADO("Adequado"),
    BOM("Bom");

    private final String rotulo;

    Classificacao(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }

    /** Classifica uma média segundo os limites do supervisor (limites crescentes). */
    public static Classificacao de(double media, ConfiguracaoClassificacao cfg) {
        if (menor(media, cfg.getLimiteCritico())) return CRITICO;
        if (menor(media, cfg.getLimiteDesenvolver())) return A_DESENVOLVER;
        if (menor(media, cfg.getLimiteBom())) return ADEQUADO;
        return BOM;
    }

    private static boolean menor(double media, BigDecimal limite) {
        return media < limite.doubleValue();
    }
}
