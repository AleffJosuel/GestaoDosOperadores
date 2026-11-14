package br.com.gestao.operadores.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Importação de colaboradores e competências por arquivo CSV. */
@RestController
public class ImportacaoController {

    private final ImportacaoService service;

    public ImportacaoController(ImportacaoService service) {
        this.service = service;
    }

    @PostMapping("/api/colaboradores/importar")
    public Dtos.ImportacaoResultado colaboradores(@RequestParam("arquivo") MultipartFile arquivo) {
        return service.importarColaboradores(arquivo);
    }

    @PostMapping("/api/competencias/importar")
    public Dtos.ImportacaoResultado competencias(@RequestParam("arquivo") MultipartFile arquivo) {
        return service.importarCompetencias(arquivo);
    }
}
