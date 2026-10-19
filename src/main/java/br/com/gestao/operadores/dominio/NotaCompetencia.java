package br.com.gestao.operadores.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.Objects;

/**
 * Nota de uma competência dentro de uma avaliação. Mapeia a tabela nota_competencia,
 * cuja chave primária é (avaliacao_id, competencia_id) — por isso a identidade deste
 * valor é apenas a competência: dois registros da mesma competência não coexistem.
 */
@Embeddable
public class NotaCompetencia {

    @Column(name = "competencia_id", nullable = false)
    private Long competenciaId;

    // SMALLINT no banco => Short na entidade (o Hibernate confere o tipo da coluna)
    @Column(nullable = false)
    private Short nota;

    protected NotaCompetencia() {}

    public NotaCompetencia(Long competenciaId, short nota) {
        this.competenciaId = competenciaId;
        this.nota = nota;
    }

    public Long getCompetenciaId() { return competenciaId; }
    public short getNota() { return nota; }

    // Identidade pela competência: garante o mesmo comportamento da PK no Set de notas.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NotaCompetencia outra)) return false;
        return Objects.equals(competenciaId, outra.competenciaId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(competenciaId);
    }
}
