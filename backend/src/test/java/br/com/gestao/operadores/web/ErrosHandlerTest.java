package br.com.gestao.operadores.web;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O que o cliente pode saber quando algo falha.
 *
 * <p>Mensagem de exceção costuma trazer nome de tabela, SQL, caminho de arquivo e versão de
 * biblioteca — informação que não ajuda o supervisor e ajuda muito quem está sondando a API. Estes
 * testes fixam a fronteira: texto escrito por nós vai para o usuário; detalhe técnico, só para o
 * log.
 */
class ErrosHandlerTest {

    private final ErrosHandler handler = new ErrosHandler();

    private static String erroDe(ResponseEntity<Map<String, String>> resposta) {
        return resposta.getBody().get("erro");
    }

    @Test
    void erroDeNegocioMantemAMensagemEscritaParaOUsuario() {
        ResponseEntity<Map<String, String>> resposta = handler.status(
            new ResponseStatusException(HttpStatus.CONFLICT, "Matrícula já cadastrada"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(erroDe(resposta)).isEqualTo("Matrícula já cadastrada");
    }

    @Test
    void erroDeNegocioSemMotivoAindaRespondeAlgoLegivel() {
        ResponseEntity<Map<String, String>> resposta =
            handler.status(new ResponseStatusException(HttpStatus.BAD_REQUEST));

        assertThat(erroDe(resposta)).isNotBlank();
    }

    @Test
    void violacaoDeIntegridadeNaoVazaNomeDeConstraintNemSql() {
        DataIntegrityViolationException original = new DataIntegrityViolationException(
            "ERROR: duplicate key value violates unique constraint \"uq_colaborador_matricula\"");

        ResponseEntity<Map<String, String>> resposta = handler.integridade(original);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(erroDe(resposta))
            .doesNotContain("constraint")
            .doesNotContain("uq_colaborador_matricula");
    }

    @Test
    void falhaInesperadaNaoVazaAExcecaoEDevolveCodigoDeOcorrencia() {
        RuntimeException causa = new IllegalStateException(
            "Connection refused: jdbc:postgresql://10.0.0.5:5432/gestao_operadores");

        ResponseEntity<Map<String, String>> resposta = handler.inesperado(causa);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(erroDe(resposta))
            .doesNotContain("jdbc")
            .doesNotContain("10.0.0.5")
            .doesNotContain("Connection refused");
        // O código devolvido é o mesmo que foi para o log: permite achar a causa sem expô-la.
        assertThat(resposta.getBody().get("ocorrencia")).isNotBlank();
        assertThat(erroDe(resposta)).contains(resposta.getBody().get("ocorrencia"));
    }

    @Test
    void corpoMalformadoViraErroDoCliente() {
        ResponseEntity<Map<String, String>> resposta = handler.corpoInvalido(
            new HttpMessageNotReadableException("Unexpected character at line 3",
                new MockHttpInputMessage(new byte[0])));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(erroDe(resposta)).doesNotContain("line 3");
    }

    @Test
    void uploadAcimaDoLimiteRespondePayloadTooLarge() {
        ResponseEntity<Map<String, String>> resposta =
            handler.uploadGrande(new MaxUploadSizeExceededException(2_097_152L));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    }

    /**
     * Sem este handler, o tratamento de {@code Exception} transformaria o 404 de uma URL errada em
     * 500 — mentindo sobre a saúde da aplicação e enchendo o log de alarme falso.
     */
    @Test
    void caminhoInexistenteContinua404() {
        ResponseEntity<Map<String, String>> resposta = handler.erroDeProtocolo(
            new NoResourceFoundException(HttpMethod.GET, "/api/inexistente"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(erroDe(resposta)).isEqualTo("Recurso não encontrado.");
    }

    @Test
    void metodoErradoContinua405() {
        ResponseEntity<Map<String, String>> resposta =
            handler.erroDeProtocolo(new HttpRequestMethodNotSupportedException("PATCH"));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }
}
