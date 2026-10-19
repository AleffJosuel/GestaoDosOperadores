package br.com.gestao.operadores.seguranca;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Freio simples contra força bruta no login: no máximo {@value #MAX_TENTATIVAS} tentativas por
 * chave (IP) dentro de uma janela de {@value #JANELA_SEGUNDOS}s. É em memória — reinicia junto com
 * a aplicação e não é compartilhado entre instâncias; suficiente para a escala deste projeto.
 */
@Component
public class LimitadorLogin {

    private static final int MAX_TENTATIVAS = 10;
    private static final long JANELA_SEGUNDOS = 60;
    private static final Duration JANELA = Duration.ofSeconds(JANELA_SEGUNDOS);

    private final Map<String, Deque<Instant>> porChave = new ConcurrentHashMap<>();

    /** Registra uma tentativa e informa se ela é permitida (dentro do limite da janela). */
    public boolean registrarEPermitir(String chave) {
        Instant agora = Instant.now();
        Deque<Instant> janela = porChave.computeIfAbsent(chave, k -> new ArrayDeque<>());
        synchronized (janela) {
            Instant limite = agora.minus(JANELA);
            while (!janela.isEmpty() && janela.peekFirst().isBefore(limite)) {
                janela.pollFirst();
            }
            if (janela.size() >= MAX_TENTATIVAS) {
                return false;
            }
            janela.addLast(agora);
            return true;
        }
    }
}
