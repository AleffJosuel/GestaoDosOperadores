package br.com.gestao.operadores.dominio;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "colaborador")
public class Colaborador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "supervisor_id", nullable = false, updatable = false)
    private Long supervisorId;

    @Column(nullable = false)
    private String matricula;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private String cargo;

    @Column(nullable = false)
    private String setor;

    @Column(name = "data_admissao")
    private LocalDate dataAdmissao;

    private String escolaridade;

    @Column(nullable = false)
    private boolean ativo = true;

    // No INSERT vale o DEFAULT do banco; no UPDATE o valor é definido em aoAtualizar().
    @Column(name = "atualizado_em", insertable = false)
    private Instant atualizadoEm;

    protected Colaborador() {}

    public Colaborador(Long supervisorId, String matricula, String nome, String cargo,
                       String setor, LocalDate dataAdmissao, String escolaridade) {
        this.supervisorId = supervisorId;
        atualizar(matricula, nome, cargo, setor, dataAdmissao, escolaridade);
    }

    public void atualizar(String matricula, String nome, String cargo,
                          String setor, LocalDate dataAdmissao, String escolaridade) {
        this.matricula = matricula;
        this.nome = nome;
        this.cargo = cargo;
        this.setor = setor;
        this.dataAdmissao = dataAdmissao;
        this.escolaridade = escolaridade;
    }

    public void definirAtivo(boolean ativo) { this.ativo = ativo; }

    @PreUpdate
    void aoAtualizar() { this.atualizadoEm = Instant.now(); }

    public Long getId() { return id; }
    public Long getSupervisorId() { return supervisorId; }
    public String getMatricula() { return matricula; }
    public String getNome() { return nome; }
    public String getCargo() { return cargo; }
    public String getSetor() { return setor; }
    public LocalDate getDataAdmissao() { return dataAdmissao; }
    public String getEscolaridade() { return escolaridade; }
    public boolean isAtivo() { return ativo; }
}
