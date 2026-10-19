package br.com.gestao.operadores.web;

import br.com.gestao.operadores.dominio.TipoCompetencia;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Importação de colaboradores e competências a partir de CSV. Cada linha é validada
 * individualmente (reaproveitando a validação e o dedup dos serviços); linhas boas são criadas
 * e as ruins entram no relatório com o motivo. Tudo é do supervisor logado (via os serviços).
 */
@Service
public class ImportacaoService {

    private static final int MAX_LINHAS = 2000;
    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/uuuu");

    private final Validator validator;
    private final ColaboradorService colaboradorService;
    private final CompetenciaService competenciaService;

    public ImportacaoService(Validator validator, ColaboradorService colaboradorService,
                             CompetenciaService competenciaService) {
        this.validator = validator;
        this.colaboradorService = colaboradorService;
        this.competenciaService = competenciaService;
    }

    /** Erro de uma linha específica (parsing de campo); não interrompe o lote. */
    private static class CampoInvalido extends RuntimeException {
        CampoInvalido(String msg) { super(msg); }
    }

    public Dtos.ImportacaoResultado importarColaboradores(MultipartFile arquivo) {
        List<CSVRecord> todos = ler(arquivo);
        Map<String, Integer> indice = indice(todos.get(0),
            List.of("matricula", "nome", "cargo", "setor"));

        List<Dtos.LinhaImportacao> linhas = new ArrayList<>();
        int criados = 0;
        for (int i = 1; i < todos.size(); i++) {
            CSVRecord r = todos.get(i);
            long n = r.getRecordNumber();
            try {
                Dtos.ColaboradorRequest req = new Dtos.ColaboradorRequest(
                    valor(r, indice, "matricula"),
                    valor(r, indice, "nome"),
                    valor(r, indice, "cargo"),
                    valor(r, indice, "setor"),
                    data(valor(r, indice, "dataadmissao")),
                    nulo(valor(r, indice, "escolaridade")));
                String erro = validar(req);
                if (erro != null) {
                    linhas.add(new Dtos.LinhaImportacao(n, false, erro));
                    continue;
                }
                colaboradorService.criar(req);
                criados++;
                linhas.add(new Dtos.LinhaImportacao(n, true, "Criado"));
            } catch (CampoInvalido e) {
                linhas.add(new Dtos.LinhaImportacao(n, false, e.getMessage()));
            } catch (ResponseStatusException e) {
                linhas.add(new Dtos.LinhaImportacao(n, false, e.getReason()));
            }
        }
        return resultado(todos.size() - 1, criados, linhas);
    }

    public Dtos.ImportacaoResultado importarCompetencias(MultipartFile arquivo) {
        List<CSVRecord> todos = ler(arquivo);
        Map<String, Integer> indice = indice(todos.get(0), List.of("nome", "tipo"));

        List<Dtos.LinhaImportacao> linhas = new ArrayList<>();
        int criados = 0;
        for (int i = 1; i < todos.size(); i++) {
            CSVRecord r = todos.get(i);
            long n = r.getRecordNumber();
            try {
                Dtos.CompetenciaRequest req = new Dtos.CompetenciaRequest(
                    valor(r, indice, "nome"),
                    tipo(valor(r, indice, "tipo")),
                    nulo(valor(r, indice, "descricao")),
                    nulo(valor(r, indice, "criterio")),
                    nivel(valor(r, indice, "nivelesperado")),
                    nulo(valor(r, indice, "acaopadrao")));
                String erro = validar(req);
                if (erro != null) {
                    linhas.add(new Dtos.LinhaImportacao(n, false, erro));
                    continue;
                }
                competenciaService.criar(req);
                criados++;
                linhas.add(new Dtos.LinhaImportacao(n, true, "Criada"));
            } catch (CampoInvalido e) {
                linhas.add(new Dtos.LinhaImportacao(n, false, e.getMessage()));
            } catch (ResponseStatusException e) {
                linhas.add(new Dtos.LinhaImportacao(n, false, e.getReason()));
            }
        }
        return resultado(todos.size() - 1, criados, linhas);
    }

    // ---- leitura e parsing ----

    private List<CSVRecord> ler(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Envie um arquivo CSV.");
        }
        String conteudo;
        try {
            conteudo = new String(arquivo.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Falha ao ler o arquivo.");
        }
        conteudo = semBom(conteudo);
        CSVFormat formato = CSVFormat.DEFAULT.builder()
            .setDelimiter(delimitador(conteudo))
            .setIgnoreEmptyLines(true)
            .setTrim(true)
            .build();
        List<CSVRecord> todos;
        try (CSVParser parser = CSVParser.parse(conteudo, formato)) {
            todos = parser.getRecords();
        } catch (IOException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "CSV inválido.");
        }
        if (todos.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Arquivo vazio.");
        }
        if (todos.size() - 1 > MAX_LINHAS) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "No máximo " + MAX_LINHAS + " linhas por arquivo.");
        }
        return todos;
    }

    private static final char BOM = 0xFEFF;

    /** Remove o BOM UTF-8 que o Excel costuma colocar no início do arquivo. */
    private static String semBom(String conteudo) {
        if (!conteudo.isEmpty() && conteudo.charAt(0) == BOM) {
            return conteudo.substring(1);
        }
        return conteudo;
    }

    private static char delimitador(String conteudo) {
        int fim = conteudo.indexOf('\n');
        String primeira = fim >= 0 ? conteudo.substring(0, fim) : conteudo;
        long pontoVirgula = primeira.chars().filter(c -> c == ';').count();
        long virgula = primeira.chars().filter(c -> c == ',').count();
        return pontoVirgula > virgula ? ';' : ',';
    }

    private static Map<String, Integer> indice(CSVRecord cabecalho, List<String> obrigatorias) {
        Map<String, Integer> indice = new HashMap<>();
        for (int i = 0; i < cabecalho.size(); i++) {
            indice.putIfAbsent(normalizar(cabecalho.get(i)), i);
        }
        List<String> faltando = obrigatorias.stream().filter(c -> !indice.containsKey(c)).toList();
        if (!faltando.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Colunas obrigatórias ausentes no cabeçalho: " + String.join(", ", faltando));
        }
        return indice;
    }

    /** minúsculas, sem acentos e sem separadores: "Data Admissão" e "data_admissao" viram "dataadmissao". */
    private static String normalizar(String s) {
        if (s == null) return "";
        String semAcento = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String valor(CSVRecord r, Map<String, Integer> indice, String chave) {
        int idx = indice.getOrDefault(chave, -1);
        if (idx < 0 || idx >= r.size()) return "";
        return r.get(idx).strip();
    }

    private static String nulo(String v) {
        return (v == null || v.isBlank()) ? null : v;
    }

    private static LocalDate data(String v) {
        if (v == null || v.isBlank()) return null;
        try {
            return LocalDate.parse(v); // ISO yyyy-MM-dd
        } catch (DateTimeParseException ignorado) {
            try {
                return LocalDate.parse(v, BR); // dd/MM/yyyy
            } catch (DateTimeParseException e) {
                throw new CampoInvalido("data de admissão inválida: '" + v + "' (use AAAA-MM-DD ou DD/MM/AAAA)");
            }
        }
    }

    private static TipoCompetencia tipo(String v) {
        String n = normalizar(v);
        if (n.isEmpty()) throw new CampoInvalido("tipo é obrigatório (COMPORTAMENTAL ou TECNICA)");
        if (n.equals("comportamental")) return TipoCompetencia.COMPORTAMENTAL;
        if (n.equals("tecnica")) return TipoCompetencia.TECNICA;
        throw new CampoInvalido("tipo inválido: '" + v + "' (use COMPORTAMENTAL ou TECNICA)");
    }

    private static Integer nivel(String v) {
        if (v == null || v.isBlank()) return null;
        try {
            return Integer.valueOf(v.strip());
        } catch (NumberFormatException e) {
            throw new CampoInvalido("nível esperado inválido: '" + v + "' (use um número de 1 a 5)");
        }
    }

    private String validar(Object o) {
        Set<ConstraintViolation<Object>> violacoes = validator.validate(o);
        if (violacoes.isEmpty()) return null;
        return violacoes.stream()
            .map(v -> v.getPropertyPath() + ": " + v.getMessage())
            .sorted()
            .collect(Collectors.joining("; "));
    }

    private static Dtos.ImportacaoResultado resultado(int total, int criados,
                                                      List<Dtos.LinhaImportacao> linhas) {
        return new Dtos.ImportacaoResultado(total, criados, total - criados, linhas);
    }
}
