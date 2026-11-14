package br.com.gestao.operadores.seguranca;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuditoriaTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    /** Os seis parâmetros do INSERT, na ordem em que foram passados. */
    private List<Object> parametrosGravados() {
        ArgumentCaptor<Object> p = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).update(anyString(), p.capture(), p.capture(), p.capture(),
            p.capture(), p.capture(), p.capture());
        return p.getAllValues();
    }

    @Test
    void gravaOsMetadadosDaOperacao() {
        new Auditoria(jdbc).registrar("ana@empresa.com", "DELETE", "/api/colaboradores/7", 204,
            "203.0.113.9", 42);

        assertThat(parametrosGravados())
            .containsExactly("ana@empresa.com", "DELETE", "/api/colaboradores/7", 204, "203.0.113.9", 42);
    }

    @Test
    void bancoForaNaoDerrubaARequisicao() {
        doThrow(new DataAccessResourceFailureException("banco fora"))
            .when(jdbc).update(anyString(), any(), any(), any(), any(), any(), any());

        // Auditoria indisponível é problema de observabilidade, não motivo para negar o trabalho do
        // supervisor. A falha vai para o log da aplicação.
        assertThatCode(() -> new Auditoria(jdbc)
            .registrar("ana@empresa.com", "POST", "/api/colaboradores", 201, "203.0.113.9", 5))
            .doesNotThrowAnyException();
    }

    @Test
    void truncaCaminhoLongoParaCaberNaColuna() {
        String caminhoEnorme = "/api/colaboradores?busca=" + "x".repeat(400);

        new Auditoria(jdbc).registrar(null, "POST", caminhoEnorme, 200, "203.0.113.9", 1);

        // Sem truncar, a própria gravação da auditoria estouraria o varchar(255) e a trilha ficaria
        // cega justo na requisição estranha que valia investigar.
        assertThat((String) parametrosGravados().get(2)).hasSize(255);
    }
}
