package br.com.gestao.operadores.web;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/** Download do mapa e do dashboard em CSV, do supervisor logado. */
@RestController
public class ExportacaoController {

    private final ExportacaoService service;

    public ExportacaoController(ExportacaoService service) {
        this.service = service;
    }

    @GetMapping("/api/mapa/exportar")
    public ResponseEntity<byte[]> mapa() {
        return arquivo(service.mapaEmCsv(), "mapa-de-competencias");
    }

    @GetMapping("/api/dashboard/exportar")
    public ResponseEntity<byte[]> dashboard() {
        return arquivo(service.dashboardEmCsv(), "indicadores");
    }

    /**
     * Devolve bytes, e não String, para o charset ser explícito: deixar o Spring converter abriria
     * espaço para ele escolher outro e desfazer o cuidado com acento e BOM do ExportacaoService.
     */
    private static ResponseEntity<byte[]> arquivo(String csv, String nomeBase) {
        String nome = nomeBase + "-" + LocalDate.now() + ".csv";
        return ResponseEntity.ok()
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            // attachment: o navegador baixa em vez de tentar exibir o CSV como texto na aba.
            // O build() não é opcional: sem ele, o toString() sai do builder e o cabeçalho vira
            // o nome da classe. O navegador então não vê "attachment", tenta exibir o CSV na aba
            // e perde o nome do arquivo.
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(nome, StandardCharsets.UTF_8).build().toString())
            // Contém dados pessoais de desempenho: não deve ficar em cache de navegador ou proxy.
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
