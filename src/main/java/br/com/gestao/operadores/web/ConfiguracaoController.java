package br.com.gestao.operadores.web;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** Configuração do supervisor logado: faixas de classificação e rótulos das notas. */
@RestController
public class ConfiguracaoController {

    private final ConfiguracaoService service;

    public ConfiguracaoController(ConfiguracaoService service) {
        this.service = service;
    }

    @GetMapping("/api/configuracao")
    public Dtos.ConfiguracaoResponse obter() {
        return service.obter();
    }

    @PutMapping("/api/configuracao")
    public Dtos.ConfiguracaoResponse salvar(@Valid @RequestBody Dtos.ConfiguracaoRequest req) {
        return service.salvar(req);
    }
}
