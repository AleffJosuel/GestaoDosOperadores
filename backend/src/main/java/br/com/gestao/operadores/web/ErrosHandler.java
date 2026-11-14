package br.com.gestao.operadores.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Converte exceção em resposta JSON. Além de padronizar o corpo (<code>{"erro": "..."}</code>), este
 * é o ponto que decide <b>o que o cliente pode saber</b> sobre uma falha.
 *
 * <p>Erros previstos levam mensagem escrita para o usuário. Erro não previsto leva mensagem genérica
 * mais um código de ocorrência: o detalhe técnico (classe, mensagem, pilha) vai só para o log do
 * servidor. Mensagem de exceção costuma carregar nome de tabela, SQL, caminho de arquivo e versão de
 * biblioteca — mapa pronto para quem está sondando a aplicação.
 */
@RestControllerAdvice
public class ErrosHandler {

    private static final Logger log = LoggerFactory.getLogger(ErrosHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validacao(MethodArgumentNotValidException ex) {
        Map<String, String> campos = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
            .forEach(e -> campos.putIfAbsent(e.getField(), e.getDefaultMessage()));
        return ResponseEntity.badRequest().body(Map.of("erro", "Dados inválidos", "campos", campos));
    }

    /** Login com credenciais inválidas: 401 genérico, sem distinguir e-mail de senha. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, String>> autenticacao(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(Map.of("erro", "Credenciais inválidas"));
    }

    /**
     * Erros que o próprio código levanta (404, 409, 422, 429...). O {@code reason} é texto escrito
     * para o usuário, então pode ir no corpo; o que não vai é a mensagem de exceção de terceiros.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> status(ResponseStatusException ex) {
        String mensagem = ex.getReason() == null ? "Não foi possível concluir a operação." : ex.getReason();
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("erro", mensagem));
    }

    /** Rede de segurança para corridas contra as constraints do banco (duplicidade, referência inválida). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> integridade(DataIntegrityViolationException ex) {
        // A mensagem original traz nome de constraint, tabela e coluna: fica no log, não na resposta.
        log.warn("Violação de integridade: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Map.of("erro", "A operação viola uma regra de integridade dos dados (registro duplicado ou referência inválida)"));
    }

    /** JSON malformado ou tipo incompatível no corpo. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> corpoInvalido(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
            .body(Map.of("erro", "Corpo da requisição inválido."));
    }

    /** Id não numérico na URL, por exemplo /api/colaboradores/abc. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> parametroInvalido(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest()
            .body(Map.of("erro", "Parâmetro inválido: " + ex.getName()));
    }

    /** Upload acima do limite do multipart (2 MB): 413 em vez de 500. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> uploadGrande(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(Map.of("erro", "Arquivo acima do limite permitido (2 MB)."));
    }

    /**
     * Erros que o próprio Spring MVC levanta: caminho inexistente (404), método errado (405),
     * conteúdo não suportado (415), parte do multipart ausente (400).
     *
     * <p>Precisa existir por causa do {@link #inesperado(Exception)} abaixo: um handler de
     * {@code Exception} tem prioridade sobre o tratamento padrão do framework e transformaria um
     * simples 404 de URL errada em 500 — mentindo sobre a saúde da aplicação e enchendo o log de
     * alarme falso. {@link ResponseStatusException} não cai aqui: é subclasse de
     * {@code ErrorResponseException} e tem handler próprio, mais específico, logo acima.
     */
    @ExceptionHandler({
        ErrorResponseException.class,
        NoResourceFoundException.class,
        NoHandlerFoundException.class,
        HttpRequestMethodNotSupportedException.class,
        HttpMediaTypeNotSupportedException.class,
        HttpMediaTypeNotAcceptableException.class,
        ServletRequestBindingException.class,
        MissingServletRequestPartException.class})
    public ResponseEntity<Map<String, String>> erroDeProtocolo(Exception ex) {
        HttpStatus status = statusDe(ex);
        return ResponseEntity.status(status).body(Map.of("erro", mensagemDe(status)));
    }

    private static HttpStatus statusDe(Exception ex) {
        if (ex instanceof ErrorResponse resposta) {
            HttpStatus convertido = HttpStatus.resolve(resposta.getStatusCode().value());
            if (convertido != null) {
                return convertido;
            }
        }
        return HttpStatus.BAD_REQUEST;
    }

    private static String mensagemDe(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "Recurso não encontrado.";
            case METHOD_NOT_ALLOWED -> "Método não permitido para este endereço.";
            case UNSUPPORTED_MEDIA_TYPE -> "Formato de conteúdo não suportado.";
            case NOT_ACCEPTABLE -> "Formato de resposta não suportado.";
            default -> "Requisição inválida.";
        };
    }

    /**
     * Qualquer falha não prevista. O código de ocorrência devolvido ao usuário é o mesmo que vai no
     * log, então o supervisor pode informá-lo e a causa é localizada sem que a resposta HTTP tenha
     * revelado nada sobre as entranhas da aplicação.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> inesperado(Exception ex) {
        String ocorrencia = UUID.randomUUID().toString().substring(0, 8);
        log.error("Falha inesperada [ocorrência {}]", ocorrencia, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
            "erro", "Erro interno no servidor. Informe o código " + ocorrencia + " ao suporte.",
            "ocorrencia", ocorrencia));
    }
}
