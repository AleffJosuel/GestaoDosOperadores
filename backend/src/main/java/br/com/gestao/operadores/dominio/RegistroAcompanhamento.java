package br.com.gestao.operadores.dominio;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Registro de acompanhamento de um colaborador (feedback, conversa, ocorrência ou outro).
 * É um histórico apenas-acréscimo: uma vez criado, não é editado nem excluído, para
 * preservar o registro do que aconteceu (mesma filosofia do soft-delete do resto do sistema).
 */
@Entity
@Table(name = "registro_acompanhamento")
public class RegistroAcompanhamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "colaborador_id", nullable = false, updatable = false)
    private Long colaboradorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private TipoRegistro tipo;

    @Column(name = "data_evento", nullable = false, updatable = false)
    private LocalDate dataEvento;

    @Column(columnDefinition = "text", nullable = false, updatable = false)
    private String descricao;

    // No INSERT vale o DEFAULT do banco (now()); o campo nunca é atualizado.
    @Column(name = "criado_em", insertable = false, updatable = false)
    private Instant criadoEm;

    protected RegistroAcompanhamento() {}

    public RegistroAcompanhamento(Long colaboradorId, TipoRegistro tipo, LocalDate dataEvento,
                                  String descricao) {
        this.colaboradorId = colaboradorId;
        this.tipo = tipo;
        this.dataEvento = dataEvento;
        this.descricao = descricao;
    }

    public Long getId() { return id; }
    public Long getColaboradorId() { return colaboradorId; }
    public TipoRegistro getTipo() { return tipo; }
    public LocalDate getDataEvento() { return dataEvento; }
    public String getDescricao() { return descricao; }
    public Instant getCriadoEm() { return criadoEm; }
}
