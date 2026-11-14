package br.com.gestao.operadores.dominio;

import jakarta.persistence.*;

@Entity
@Table(name = "supervisor")
public class Supervisor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "senha_hash", nullable = false)
    private String senhaHash;

    protected Supervisor() {}

    public Supervisor(String email, String senhaHash) {
        this.email = email;
        this.senhaHash = senhaHash;
    }

    /** Recebe o hash já calculado (nunca a senha em texto puro). */
    public void alterarSenha(String novoHash) {
        this.senhaHash = novoHash;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getSenhaHash() { return senhaHash; }
}
