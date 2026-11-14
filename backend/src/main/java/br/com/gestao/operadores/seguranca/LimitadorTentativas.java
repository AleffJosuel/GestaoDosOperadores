package br.com.gestao.operadores.seguranca;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Freio contra abuso de endpoints sensíveis (login e troca de senha): no máximo N tentativas por
 * chave dentro de uma janela de tempo. É em memória — reinicia junto com a aplicação e não é
 * compartilhado entre instâncias; suficiente para a escala deste projeto.
 *
 * <p>Os limites ficam em {@link Limites}, não espalhados pelos controllers, para que a pergunta
 * "quanto a aplicação tolera antes de bloquear?" tenha uma resposta num só lugar.
 */
@Component
public class LimitadorTentativas {

    /** Limites de cada endpoint, num lugar só para ficarem fáceis de auditar. */
    public enum Limites {
        /** Login por IP: segura a rajada de uma origem sem travar a empresa toda. */
        LOGIN_POR_IP(10, Duration.ofMinutes(1)),
        /**
         * Login por conta: freia credential stuffing vindo de muitos IPs. Janela curta e de
         * propósito generosa — um bloqueio longo por conta deixaria qualquer um trancar o
         * supervisor de fora só chutando senhas no e-mail dele.
         */
        LOGIN_POR_CONTA(20, Duration.ofMinutes(15)),
        /** Troca de senha: já exige sessão válida, então o alvo é adivinhar a senha atual. */
        TROCA_SENHA(5, Duration.ofMinutes(15));

        private final int maxTentativas;
        private final Duration janela;

        Limites(int maxTentativas, Duration janela) {
            this.maxTentativas = maxTentativas;
            this.janela = janela;
        }

        public int maxTentativas() {
            return maxTentativas;
        }

        public Duration janela() {
            return janela;
        }
    }

    /**
     * Teto de chaves vivas. Sem ele, um atacante que troca de IP a cada tentativa faria o mapa
     * crescer sem limite até derrubar a aplicação por falta de memória — o limitador viraria a
     * própria vulnerabilidade.
     */
    private static final int MAX_CHAVES = 10_000;

    /**
     * {@link LinkedHashMap} em modo de ordem de acesso: ao estourar o teto, descarta a chave usada
     * há mais tempo, em tempo constante e sem varrer o mapa.
     *
     * <p>A ordem de acesso é o que protege quem está sob ataque: a chave que acabou de tentar vai
     * para o fim da fila, então a enxurrada de IPs novos descarta a si mesma, não a contagem de
     * quem interessa. Mesmo assim o descarte é um compromisso aceito — em ataque muito distribuído
     * uma contagem pode se perder; perder precisão no limite é melhor que perder a aplicação.
     */
    private final Map<String, Deque<Instant>> porChave = Collections.synchronizedMap(
        new LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Deque<Instant>> maisAntiga) {
                return size() > MAX_CHAVES;
            }
        });

    /** Registra uma tentativa e informa se ela é permitida (dentro do limite da janela). */
    public boolean registrarEPermitir(Limites limite, String chave) {
        Instant agora = Instant.now();
        String chaveCompleta = limite.name() + ":" + chave;
        // Um lock só para o mapa inteiro: estes endpoints são de baixa frequência (login e troca
        // de senha), e a simplicidade aqui vale mais que a concorrência fina.
        synchronized (porChave) {
            Deque<Instant> janela = porChave.computeIfAbsent(chaveCompleta, k -> new ArrayDeque<>());
            Instant inicio = agora.minus(limite.janela());
            while (!janela.isEmpty() && janela.peekFirst().isBefore(inicio)) {
                janela.pollFirst();
            }
            if (janela.size() >= limite.maxTentativas()) {
                return false;
            }
            janela.addLast(agora);
            return true;
        }
    }

    /** Quantas chaves estão vivas em memória. Existe para o teste provar que o teto é real. */
    int chavesVivas() {
        synchronized (porChave) {
            return porChave.size();
        }
    }
}
