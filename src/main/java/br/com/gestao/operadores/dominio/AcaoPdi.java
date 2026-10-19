package br.com.gestao.operadores.dominio;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Ação de desenvolvimento (PDI) de um colaborador, ligada a uma competência e à avaliação
 * que a originou. O colaborador, a competência e a avaliação de origem são fixados na criação;
 * o restante (descrição, prazo, status, conclusão, evidência...) evolui ao longo do acompanhamento.
 */
@Entity
@Table(name = "acao_pdi")
public class AcaoPdi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "colaborador_id", nullable = false, updatable = false)
    private Long colaboradorId;

    @Column(name = "competencia_id", nullable = false, updatable = false)
    private Long competenciaId;

    @Column(name = "avaliacao_origem_id", nullable = false, updatable = false)
    private Long avaliacaoOrigemId;

    @Column(columnDefinition = "text", nullable = false)
    private String descricao;

    private String responsavel;

    @Column(name = "funcao_area")
    private String funcaoArea;

    @Column(name = "carga_horaria_minutos")
    private Integer cargaHorariaMinutos;

    private LocalDate prazo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusPdi status;

    @Column(name = "data_conclusao")
    private LocalDate dataConclusao;

    @Column(columnDefinition = "text")
    private String evidencia;

    @Column(columnDefinition = "text")
    private String observacao;

    // No INSERT vale o DEFAULT do banco; no UPDATE o valor é definido em aoAtualizar().
    @Column(name = "atualizado_em", insertable = false)
    private Instant atualizadoEm;

    protected AcaoPdi() {}

    public AcaoPdi(Long colaboradorId, Long competenciaId, Long avaliacaoOrigemId,
                   String descricao, String responsavel, String funcaoArea, Integer cargaHorariaMinutos,
                   LocalDate prazo, StatusPdi status, LocalDate dataConclusao, String evidencia,
                   String observacao) {
        this.colaboradorId = colaboradorId;
        this.competenciaId = competenciaId;
        this.avaliacaoOrigemId = avaliacaoOrigemId;
        definir(descricao, responsavel, funcaoArea, cargaHorariaMinutos, prazo, status,
            dataConclusao, evidencia, observacao);
    }

    /** Atualiza os campos de acompanhamento (o colaborador/competência/avaliação de origem não mudam). */
    public void definir(String descricao, String responsavel, String funcaoArea, Integer cargaHorariaMinutos,
                        LocalDate prazo, StatusPdi status, LocalDate dataConclusao, String evidencia,
                        String observacao) {
        this.descricao = descricao;
        this.responsavel = responsavel;
        this.funcaoArea = funcaoArea;
        this.cargaHorariaMinutos = cargaHorariaMinutos;
        this.prazo = prazo;
        this.status = status;
        this.dataConclusao = dataConclusao;
        this.evidencia = evidencia;
        this.observacao = observacao;
    }

    @PreUpdate
    void aoAtualizar() { this.atualizadoEm = Instant.now(); }

    public Long getId() { return id; }
    public Long getColaboradorId() { return colaboradorId; }
    public Long getCompetenciaId() { return competenciaId; }
    public Long getAvaliacaoOrigemId() { return avaliacaoOrigemId; }
    public String getDescricao() { return descricao; }
    public String getResponsavel() { return responsavel; }
    public String getFuncaoArea() { return funcaoArea; }
    public Integer getCargaHorariaMinutos() { return cargaHorariaMinutos; }
    public LocalDate getPrazo() { return prazo; }
    public StatusPdi getStatus() { return status; }
    public LocalDate getDataConclusao() { return dataConclusao; }
    public String getEvidencia() { return evidencia; }
    public String getObservacao() { return observacao; }
}
