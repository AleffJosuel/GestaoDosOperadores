package br.com.gestao.operadores.seguranca;

import br.com.gestao.operadores.dominio.Supervisor;
import br.com.gestao.operadores.dominio.SupervisorRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Provisionamento de conta no boot: criação idempotente e o reset de senha por RESET_SENHA.
 *
 * <p>O reset é o socorro para senha esquecida, e por isso mesmo merece teste: é o único caminho
 * no sistema que troca a senha de alguém <em>sem</em> conferir a senha anterior.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SupervisorInicialTest {

    private static final String EMAIL = "marcos.ferreira@empresa.com.br";
    private static final String SENHA_FORTE = "Tr3vo!Chuva#Pedra";
    private static final String SENHA_NOVA = "Qu4tro!Ventos#Sul";

    @Mock SupervisorRepository supervisores;
    @Mock JdbcTemplate jdbc;
    @Mock Auditoria auditoria;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    private SupervisorInicial com(String email, String senha, boolean reset) {
        return new SupervisorInicial(supervisores, encoder, jdbc, auditoria, email, senha, reset);
    }

    private Supervisor contaExistenteCom(String senha) {
        Supervisor s = new Supervisor(EMAIL, encoder.encode(senha));
        when(supervisores.findByEmail(EMAIL)).thenReturn(Optional.of(s));
        return s;
    }

    // --------------------------------------------------------------------- criação

    @Test
    void criaAContaComASenhaEmHashNuncaEmTextoPuro() {
        when(supervisores.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(supervisores.save(any())).thenAnswer(chamada -> chamada.getArgument(0));

        com(EMAIL, SENHA_FORTE, false).run();

        ArgumentCaptor<Supervisor> gravado = ArgumentCaptor.forClass(Supervisor.class);
        verify(supervisores).save(gravado.capture());
        assertThat(gravado.getValue().getEmail()).isEqualTo(EMAIL);
        assertThat(gravado.getValue().getSenhaHash()).isNotEqualTo(SENHA_FORTE);
        assertThat(encoder.matches(SENHA_FORTE, gravado.getValue().getSenhaHash())).isTrue();
    }

    @Test
    void naoRecriaContaQueJaExiste() {
        Supervisor existente = contaExistenteCom(SENHA_FORTE);
        String hashOriginal = existente.getSenhaHash();

        com(EMAIL, SENHA_NOVA, false).run();

        // Sem RESET_SENHA, reiniciar com outra senha no arquivo não mexe na conta.
        verify(supervisores, never()).save(any());
        assertThat(existente.getSenhaHash()).isEqualTo(hashOriginal);
    }

    @Test
    void naoCriaContaComSenhaFraca() {
        when(supervisores.findByEmail(EMAIL)).thenReturn(Optional.empty());

        com(EMAIL, "123456", false).run();

        verify(supervisores, never()).save(any());
    }

    @Test
    void semEmailOuSenhaNaoFazNada() {
        com("", SENHA_FORTE, false).run();
        com(EMAIL, "", false).run();

        verify(supervisores, never()).save(any());
        verify(supervisores, never()).findByEmail(anyString());
    }

    // --------------------------------------------------------------------- reset

    @Test
    void resetRedefineASenhaDeContaExistente() {
        Supervisor existente = contaExistenteCom(SENHA_FORTE);

        com(EMAIL, SENHA_NOVA, true).run();

        assertThat(encoder.matches(SENHA_NOVA, existente.getSenhaHash())).isTrue();
        assertThat(encoder.matches(SENHA_FORTE, existente.getSenhaHash())).isFalse();
    }

    @Test
    void resetFicaNaTrilhaDeAuditoria() {
        contaExistenteCom(SENHA_FORTE);

        com(EMAIL, SENHA_NOVA, true).run();

        // É uma troca de senha sem conferir a anterior: tem de deixar rastro de quem foi afetado.
        verify(auditoria).registrar(EMAIL, "RESET", "/sistema/reset-senha", 200, "boot", 0);
    }

    @Test
    void resetNaoAceitaSenhaFraca() {
        Supervisor existente = contaExistenteCom(SENHA_FORTE);
        String hashOriginal = existente.getSenhaHash();

        com(EMAIL, "senha123", true).run();

        // Socorro operacional não é motivo para abrir exceção na política de senha.
        assertThat(existente.getSenhaHash()).isEqualTo(hashOriginal);
        verify(auditoria, never()).registrar(anyString(), anyString(), anyString(), anyInt(), anyString(), anyLong());
    }

    @Test
    void resetRepetidoComAMesmaSenhaNaoRegravaNemAudita() {
        // Cenário do sinalizador esquecido no arquivo: o segundo reinício não deve gerar rastro
        // novo nem reescrever o hash, só o aviso no log.
        Supervisor existente = contaExistenteCom(SENHA_FORTE);
        String hashOriginal = existente.getSenhaHash();

        com(EMAIL, SENHA_FORTE, true).run();

        assertThat(existente.getSenhaHash()).isEqualTo(hashOriginal);
        verify(auditoria, never()).registrar(anyString(), anyString(), anyString(), anyInt(), anyString(), anyLong());
    }

    @Test
    void resetSemContaCorrespondenteCriaAConta() {
        when(supervisores.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(supervisores.save(any())).thenAnswer(chamada -> chamada.getArgument(0));

        com(EMAIL, SENHA_FORTE, true).run();

        // Erro de digitação no e-mail não pode travar a subida: cria, e o log avisa.
        verify(supervisores).save(any());
    }

    @Test
    void resetSemSenhaNoArquivoNaoApagaASenhaDeNinguem() {
        // O pior caso possível: RESET_SENHA=true com ADMIN_PASSWORD em branco. Se isso zerasse a
        // senha, a conta ficaria inacessível — ou pior, acessível com senha vazia.
        com(EMAIL, "", true).run();

        verify(supervisores, never()).findByEmail(anyString());
        verify(supervisores, never()).save(any());
        verify(auditoria, never()).registrar(anyString(), anyString(), anyString(), anyInt(), anyString(), anyLong());
    }
}
