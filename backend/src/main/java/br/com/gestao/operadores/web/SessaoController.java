package br.com.gestao.operadores.web;

import br.com.gestao.operadores.seguranca.IpDaRequisicao;
import br.com.gestao.operadores.seguranca.LimitadorTentativas;
import br.com.gestao.operadores.seguranca.SecurityConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;

/**
 * Login: valida e-mail e senha e devolve um JWT de curta duração. Credenciais erradas
 * resultam em 401 genérico (tratado no {@link ErrosHandler}), sem revelar se o e-mail existe.
 */
@RestController
public class SessaoController {

    private static final Logger log = LoggerFactory.getLogger(SessaoController.class);

    private final AuthenticationManager autenticador;
    private final JwtEncoder encoder;
    private final LimitadorTentativas limitador;
    private final IpDaRequisicao ips;
    private final long expiracaoMinutos;

    public SessaoController(AuthenticationManager autenticador, JwtEncoder encoder,
                            LimitadorTentativas limitador, IpDaRequisicao ips,
                            @Value("${app.jwt.expiracao-minutos}") long expiracaoMinutos) {
        this.autenticador = autenticador;
        this.encoder = encoder;
        this.limitador = limitador;
        this.ips = ips;
        this.expiracaoMinutos = expiracaoMinutos;
    }

    @PostMapping("/api/sessao")
    public Dtos.TokenResponse login(@Valid @RequestBody Dtos.LoginRequest req,
                                    HttpServletRequest http) {
        // Dois freios: por origem (rajada de um IP) e por conta (muitos IPs contra o mesmo e-mail).
        boolean dentroDoLimite =
            limitador.registrarEPermitir(LimitadorTentativas.Limites.LOGIN_POR_IP, ips.de(http))
            & limitador.registrarEPermitir(LimitadorTentativas.Limites.LOGIN_POR_CONTA,
                                           req.email().toLowerCase(Locale.ROOT));
        if (!dentroDoLimite) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "Muitas tentativas de login. Aguarde um instante e tente novamente.");
        }
        Authentication auth;
        try {
            auth = autenticador.authenticate(
                new UsernamePasswordAuthenticationToken(req.email(), req.senha()));
        } catch (AuthenticationException e) {
            // Credenciais inválidas: 401 genérico, sem revelar se o e-mail existe. No log vai o
            // e-mail mascarado — basta para investigar um ataque e não espalha dado pessoal
            // legível por arquivo de log e agregador de terceiro.
            log.warn("Falha de login para '{}': {}", mascarar(req.email()), e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais inválidas");
        }

        Instant agora = Instant.now();
        long expiraEmSegundos = expiracaoMinutos * 60;
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(SecurityConfig.EMISSOR)
            .issuedAt(agora)
            .expiresAt(agora.plus(expiracaoMinutos, ChronoUnit.MINUTES))
            .subject(auth.getName())
            .id(UUID.randomUUID().toString()) // jti: identifica a sessão na trilha de auditoria
            .claim("scope", "SUPERVISOR")
            .build();

        // Precisa declarar HS256 explicitamente: a chave é simétrica (HMAC), senão o Nimbus
        // tenta RS256 por padrão e falha com "Failed to select a JWK signing key".
        JwsHeader cabecalho = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(cabecalho, claims)).getTokenValue();
        return new Dtos.TokenResponse(token, expiraEmSegundos);
    }

    /** "supervisor@empresa.com" vira "s***@empresa.com": identifica sem expor. */
    static String mascarar(String email) {
        if (email == null || email.isBlank()) {
            return "(vazio)";
        }
        int arroba = email.indexOf('@');
        if (arroba <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(arroba);
    }
}
