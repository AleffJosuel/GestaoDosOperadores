package br.com.gestao.operadores.dominio;

import jakarta.persistence.*;

@Entity
@Table(name = "competencia")
public class Competencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "supervisor_id", nullable = false, updatable = false)
    private Long supervisorId;

    @Column(nullable = false)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoCompetencia tipo;

    @Column(columnDefinition = "text")
    private String descricao;

    @Column(columnDefinition = "text")
    private String criterio;

    // SMALLINT no banco => Short na entidade (o Hibernate valida o tipo da coluna)
    @Column(name = "nivel_esperado", nullable = false)
    private Short nivelEsperado;

    @Column(name = "acao_padrao")
    private String acaoPadrao;

    @Column(nullable = false)
    private boolean ativa = true;

    protected Competencia() {}

    public Competencia(Long supervisorId, String nome, TipoCompetencia tipo, String descricao,
                       String criterio, short nivelEsperado, String acaoPadrao) {
        this.supervisorId = supervisorId;
        atualizar(nome, tipo, descricao, criterio, nivelEsperado, acaoPadrao);
    }

    public void atualizar(String nome, TipoCompetencia tipo, String descricao,
                          String criterio, short nivelEsperado, String acaoPadrao) {
        this.nome = nome;
        this.tipo = tipo;
        this.descricao = descricao;
        this.criterio = criterio;
        this.nivelEsperado = nivelEsperado;
        this.acaoPadrao = acaoPadrao;
    }

    public void definirAtiva(boolean ativa) { this.ativa = ativa; }

    public Long getId() { return id; }
    public Long getSupervisorId() { return supervisorId; }
    public String getNome() { return nome; }
    public TipoCompetencia getTipo() { return tipo; }
    public String getDescricao() { return descricao; }
    public String getCriterio() { return criterio; }
    public short getNivelEsperado() { return nivelEsperado; }
    public String getAcaoPadrao() { return acaoPadrao; }
    public boolean isAtiva() { return ativa; }
}
