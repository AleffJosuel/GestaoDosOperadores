package br.com.gestao.operadores.seguranca;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Regra única de senha forte, usada tanto na criação da conta ({@link SupervisorInicial}) quanto na
 * troca pelo supervisor. Ficar num lugar só evita o caso clássico de a troca exigir mais (ou menos)
 * que o cadastro.
 *
 * <p>O desenho segue a recomendação atual do NIST SP 800-63B: o que mais importa é <b>tamanho</b>, e
 * comprimento grande dispensa exigir caracteres especiais. Por isso:
 * <ul>
 *   <li>12 caracteres já valem se a senha tiver variedade (3 das 4 classes);</li>
 *   <li>a partir de 20 caracteres, a frase passa sem exigência de classes — "a nota da prova de
 *       matematica" é mais forte que "Senha@123" e não deve ser recusada;</li>
 *   <li>senhas óbvias e repetições/sequências são barradas sempre;</li>
 *   <li>a senha não pode conter o e-mail da conta (nem o contrário).</li>
 * </ul>
 *
 * <p>Não há consulta a listas de senhas vazadas: seria o próximo passo natural, mas exige serviço
 * externo e não está implementado.
 */
public final class PoliticaSenha {

    public static final int TAMANHO_MINIMO = 12;
    public static final int TAMANHO_MAXIMO = 200;
    /** Acima disto, frase longa dispensa a exigência de variedade de classes. */
    private static final int TAMANHO_QUE_DISPENSA_CLASSES = 20;
    private static final int CLASSES_EXIGIDAS = 3;
    private static final int MAX_REPETICOES_SEGUIDAS = 3;
    private static final int MAX_SEQUENCIA = 4;

    /** Trechos que, sozinhos, já entregam a senha. Lista curta e local, sem pretensão de ser completa. */
    private static final List<String> TRECHOS_OBVIOS = List.of(
        "senha", "password", "123456", "qwerty", "admin", "gestao", "operador", "supervisor",
        "iloveyou", "abcdef", "letmein", "mudar123", "trocar");

    private PoliticaSenha() {}

    /**
     * Devolve a mensagem do primeiro problema encontrado, ou vazio se a senha é aceitável.
     * Mensagem em português, pronta para ir ao usuário: ela diz o que corrigir e não revela regra
     * nenhuma que ajude a atacar outra conta.
     */
    public static Optional<String> problema(String senha, String email) {
        if (senha == null || senha.isBlank()) {
            return Optional.of("Informe uma senha.");
        }
        if (senha.length() < TAMANHO_MINIMO) {
            return Optional.of("A senha precisa ter ao menos " + TAMANHO_MINIMO + " caracteres.");
        }
        if (senha.length() > TAMANHO_MAXIMO) {
            return Optional.of("A senha pode ter no máximo " + TAMANHO_MAXIMO + " caracteres.");
        }
        String normalizada = semAcento(senha).toLowerCase(Locale.ROOT);
        for (String obvio : TRECHOS_OBVIOS) {
            if (normalizada.contains(obvio)) {
                return Optional.of("A senha não pode conter trechos previsíveis como \"" + obvio + "\".");
            }
        }
        if (temRepeticaoLonga(senha)) {
            return Optional.of("A senha não pode repetir o mesmo caractere "
                + (MAX_REPETICOES_SEGUIDAS + 1) + " vezes seguidas.");
        }
        if (temSequencia(normalizada)) {
            return Optional.of("A senha não pode conter sequências como \"1234\" ou \"abcd\".");
        }
        Optional<String> conflito = conflitoComEmail(normalizada, email);
        if (conflito.isPresent()) {
            return conflito;
        }
        if (senha.length() < TAMANHO_QUE_DISPENSA_CLASSES && classes(senha) < CLASSES_EXIGIDAS) {
            return Optional.of("Combine ao menos " + CLASSES_EXIGIDAS
                + " tipos de caractere (maiúscula, minúscula, número, símbolo) — ou use uma frase com "
                + TAMANHO_QUE_DISPENSA_CLASSES + " caracteres ou mais.");
        }
        return Optional.empty();
    }

    /** Conveniência para quem só quer saber se passa. */
    public static boolean aceitavel(String senha, String email) {
        return problema(senha, email).isEmpty();
    }

    private static Optional<String> conflitoComEmail(String senhaNormalizada, String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        String parteLocal = semAcento(email).toLowerCase(Locale.ROOT).split("@")[0];
        // Partes curtas ("aj@...") apareceriam em qualquer senha e tornariam a regra inútil.
        if (parteLocal.length() < 4) {
            return Optional.empty();
        }
        if (senhaNormalizada.contains(parteLocal)) {
            return Optional.of("A senha não pode conter o seu e-mail.");
        }
        return Optional.empty();
    }

    private static int classes(String senha) {
        boolean minuscula = false, maiuscula = false, digito = false, simbolo = false;
        for (char c : senha.toCharArray()) {
            if (Character.isLowerCase(c)) minuscula = true;
            else if (Character.isUpperCase(c)) maiuscula = true;
            else if (Character.isDigit(c)) digito = true;
            else simbolo = true;
        }
        return (minuscula ? 1 : 0) + (maiuscula ? 1 : 0) + (digito ? 1 : 0) + (simbolo ? 1 : 0);
    }

    private static boolean temRepeticaoLonga(String senha) {
        int seguidos = 1;
        for (int i = 1; i < senha.length(); i++) {
            seguidos = senha.charAt(i) == senha.charAt(i - 1) ? seguidos + 1 : 1;
            if (seguidos > MAX_REPETICOES_SEGUIDAS) {
                return true;
            }
        }
        return false;
    }

    /** Sequências crescentes ou decrescentes de letras/números ("1234", "4321", "abcd"). */
    private static boolean temSequencia(String senha) {
        int passo = 0;
        int tamanho = 1;
        for (int i = 1; i < senha.length(); i++) {
            char anterior = senha.charAt(i - 1);
            char atual = senha.charAt(i);
            if (!alfanumerico(anterior) || !alfanumerico(atual)) {
                passo = 0;
                tamanho = 1;
                continue;
            }
            int diferenca = atual - anterior;
            if ((diferenca == 1 || diferenca == -1) && (passo == 0 || passo == diferenca)) {
                passo = diferenca;
                tamanho++;
                if (tamanho >= MAX_SEQUENCIA) {
                    return true;
                }
            } else {
                passo = (diferenca == 1 || diferenca == -1) ? diferenca : 0;
                tamanho = passo == 0 ? 1 : 2;
            }
        }
        return false;
    }

    private static boolean alfanumerico(char c) {
        return Character.isLetterOrDigit(c);
    }

    private static String semAcento(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
