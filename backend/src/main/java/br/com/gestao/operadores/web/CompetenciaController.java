package br.com.gestao.operadores.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/competencias")
public class CompetenciaController {

    private final CompetenciaService service;

    public CompetenciaController(CompetenciaService service) {
        this.service = service;
    }

    @GetMapping
    public List<Dtos.CompetenciaResponse> listar(@RequestParam(defaultValue = "false") boolean incluirInativas) {
        return service.listar(incluirInativas).stream().map(Dtos.CompetenciaResponse::de).toList();
    }

    @GetMapping("/{id}")
    public Dtos.CompetenciaResponse buscar(@PathVariable Long id) {
        return Dtos.CompetenciaResponse.de(service.buscar(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Dtos.CompetenciaResponse criar(@Valid @RequestBody Dtos.CompetenciaRequest req) {
        return Dtos.CompetenciaResponse.de(service.criar(req));
    }

    @PutMapping("/{id}")
    public Dtos.CompetenciaResponse atualizar(@PathVariable Long id, @Valid @RequestBody Dtos.CompetenciaRequest req) {
        return Dtos.CompetenciaResponse.de(service.atualizar(id, req));
    }

    @PostMapping("/{id}/desativar")
    public Dtos.CompetenciaResponse desativar(@PathVariable Long id) {
        return Dtos.CompetenciaResponse.de(service.alterarAtiva(id, false));
    }

    @PostMapping("/{id}/reativar")
    public Dtos.CompetenciaResponse reativar(@PathVariable Long id) {
        return Dtos.CompetenciaResponse.de(service.alterarAtiva(id, true));
    }
}
