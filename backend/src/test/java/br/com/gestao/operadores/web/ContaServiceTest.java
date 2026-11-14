package br.com.gestao.operadores.web;

import br.com.gestao.operadores.dominio.Supervisor;
import br.com.gestao.operadores.dominio.SupervisorRepository;
import br.com.gestao.operadores.seguranca.LimitadorTentativas;
import br.com.gestao.operadores.seguranca.SupervisorLogado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** Troca de senha: confere a atual, aplica a política e tem freio próprio contra tentativa e erro. */
@ExtendWith(MockitoExtension.class)
class ContaServiceTest {

    private static final Long ID = 1L;
    private static final String EMAIL = "marcos.ferreira@empresa.com.br";
    private static final String SENHA_ATUAL = "Tr3vo!Chuva#Pedra";
    private static final String SENHA_NOVA = "Qu4tro!Ventos#Sul";

    @Mock SupervisorRepository supervisores;
    @Mock SupervisorLogado logado;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();
    private ContaService conta;
    private Supervisor supervisor;

    @BeforeEach
    void montar() {
        supervisor = new Supervisor(EMAIL, encoder.encode(SENHA_ATUAL));
        lenient().when(logado.id()).thenReturn(ID);
        lenient().when(supervisores.findById(ID)).thenReturn(Optional.of(supervisor));
        conta = new ContaService(supervisores, encoder, logado, new LimitadorTentativas());
    }

    private HttpStatus statusDe(Throwable t) {
        return HttpStatus.valueOf(((ResponseStatusException) t).getStatusCode().value());
    }

    @Test
    void trocaSenhaForte() {
        conta.trocarSenha(new Dtos.TrocarSenhaRequest(SENHA_ATUAL, SENHA_NOVA));

        assertThat(encoder.matches(SENHA_NOVA, supervisor.getSenhaHash())).isTrue();
    }

    @Test
    void recusaSenhaAtualErrada() {
        assertThatThrownBy(() -> conta.trocarSenha(
                new Dtos.TrocarSenhaRequest("Qu4lquer!Outra#Coisa", SENHA_NOVA)))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(statusDe(e)).isEqualTo(HttpStatus.BAD_REQUEST));

        assertThat(encoder.matches(SENHA_ATUAL, supervisor.getSenhaHash())).isTrue();
    }

    @Test
    void recusaSenhaNovaFracaMesmoComASenhaAtualCorreta() {
        // Sem política, 12 caracteres de "MinhaSenha12" bastariam — e é exatamente o que alguém
        // escolhe quando o sistema deixa.
        assertThatThrownBy(() -> conta.trocarSenha(
                new Dtos.TrocarSenhaRequest(SENHA_ATUAL, "MinhaSenha12")))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(statusDe(e)).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));

        assertThat(encoder.matches(SENHA_ATUAL, supervisor.getSenhaHash()))
            .as("a senha não pode ter sido trocada")
            .isTrue();
    }

    @Test
    void recusaSenhaNovaIgualAAtual() {
        assertThatThrownBy(() -> conta.trocarSenha(
                new Dtos.TrocarSenhaRequest(SENHA_ATUAL, SENHA_ATUAL)))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(statusDe(e)).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    /**
     * Com um token roubado, o atacante poderia descobrir a senha atual por tentativa e erro aqui —
     * e com ela trocar a senha e tomar a conta de vez. O freio responde 429 antes disso.
     */
    @Test
    void bloqueiaDepoisDeVariasTentativasComSenhaAtualErrada() {
        int limite = LimitadorTentativas.Limites.TROCA_SENHA.maxTentativas();
        for (int i = 0; i < limite; i++) {
            assertThatThrownBy(() -> conta.trocarSenha(
                    new Dtos.TrocarSenhaRequest("chute" + System.nanoTime(), SENHA_NOVA)))
                .isInstanceOf(ResponseStatusException.class);
        }

        assertThatThrownBy(() -> conta.trocarSenha(new Dtos.TrocarSenhaRequest(SENHA_ATUAL, SENHA_NOVA)))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(statusDe(e)).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void sessaoSemSupervisorCorrespondenteNaoTrocaSenha() {
        when(supervisores.findById(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> conta.trocarSenha(new Dtos.TrocarSenhaRequest(SENHA_ATUAL, SENHA_NOVA)))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(statusDe(e)).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void naoGastaOFreioDeOutraConta() {
        LimitadorTentativas limitador = new LimitadorTentativas();
        ContaService servico = new ContaService(supervisores, encoder, logado, limitador);
        for (int i = 0; i < LimitadorTentativas.Limites.TROCA_SENHA.maxTentativas(); i++) {
            String chute = "errada-" + i;
            assertThatThrownBy(() -> servico.trocarSenha(new Dtos.TrocarSenhaRequest(chute, SENHA_NOVA)))
                .isInstanceOf(ResponseStatusException.class);
        }

        // Outro supervisor, outra chave: o bloqueio de um não pode respingar no outro.
        Supervisor outro = new Supervisor("ana@empresa.com", encoder.encode(SENHA_ATUAL));
        when(supervisores.findById(ID)).thenReturn(Optional.of(outro));

        assertThatCode(() -> servico.trocarSenha(new Dtos.TrocarSenhaRequest(SENHA_ATUAL, SENHA_NOVA)))
            .doesNotThrowAnyException();
    }
}
