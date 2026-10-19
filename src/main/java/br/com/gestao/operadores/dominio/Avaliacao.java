package br.com.gestao.operadores.dominio;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Avaliação de um colaborador em uma data, com uma nota (1–5) por competência.
 * O dono (supervisor) é alcançado por colaborador: o filtro de acesso vive no serviço.
 */
@Entity
@Table(name = "avaliacao")
public class Avaliacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "colaborador_id", nullable = false, updatable = false)
    private Long colaboradorId;

    @Column(name = "data_avaliacao", nullable = false)
    private LocalDate dataAvaliacao;

    @Column(columnDefinition = "text")
    private String observacao;

    // No INSERT vale o DEFAULT do banco; no UPDATE o valor é definido em aoAtualizar().
    @Column(name = "atualizado_em", insertable = false)
    private Instant atualizadoEm;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "nota_competencia", joinColumns = @JoinColumn(name = "avaliacao_id"))
    private Set<NotaCompetencia> notas = new LinkedHashSet<>();

    protected Avaliacao() {}

    public Avaliacao(Long colaboradorId, LocalDate dataAvaliacao, String observacao,
                     Collection<NotaCompetencia> notas) {
        this.colaboradorId = colaboradorId;
        definir(dataAvaliacao, observacao, notas);
    }

    public void definir(LocalDate dataAvaliacao, String observacao, Collection<NotaCompetencia> notas) {
        this.dataAvaliacao = dataAvaliacao;
        this.observacao = observacao;
        this.notas.clear();
        this.notas.addAll(notas);
    }

    @PreUpdate
    void aoAtualizar() { this.atualizadoEm = Instant.now(); }

    public Long getId() { return id; }
    public Long getColaboradorId() { return colaboradorId; }
    public LocalDate getDataAvaliacao() { return dataAvaliacao; }
    public String getObservacao() { return observacao; }
    public Set<NotaCompetencia> getNotas() { return notas; }
}
