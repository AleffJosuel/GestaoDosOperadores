package br.com.gestao.operadores.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Limites (crescentes) que separam as faixas de classificação de um supervisor.
 * Criada junto com o supervisor inicial; os valores padrão são 2,00 / 3,00 / 4,00.
 */
@Entity
@Table(name = "configuracao_classificacao")
public class ConfiguracaoClassificacao {

    @Id
    @Column(name = "supervisor_id")
    private Long supervisorId;

    @Column(name = "limite_critico", nullable = false)
    private BigDecimal limiteCritico;

    @Column(name = "limite_desenvolver", nullable = false)
    private BigDecimal limiteDesenvolver;

    @Column(name = "limite_bom", nullable = false)
    private BigDecimal limiteBom;

    protected ConfiguracaoClassificacao() {}

    /** Padrão usado quando o supervisor ainda não tem configuração persistida. */
    public static ConfiguracaoClassificacao padrao() {
        ConfiguracaoClassificacao c = new ConfiguracaoClassificacao();
        c.limiteCritico = new BigDecimal("2.00");
        c.limiteDesenvolver = new BigDecimal("3.00");
        c.limiteBom = new BigDecimal("4.00");
        return c;
    }

    public Long getSupervisorId() { return supervisorId; }
    public BigDecimal getLimiteCritico() { return limiteCritico; }
    public BigDecimal getLimiteDesenvolver() { return limiteDesenvolver; }
    public BigDecimal getLimiteBom() { return limiteBom; }
}
