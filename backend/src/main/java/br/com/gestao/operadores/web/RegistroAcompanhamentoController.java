package br.com.gestao.operadores.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Registros de acompanhamento de um colaborador. Ficam sob o colaborador (dono do recurso)
 * e são apenas-acréscimo: há listagem e criação, sem edição nem exclusão.
 */
@RestController
public class RegistroAcompanhamentoController {

    private final RegistroAcompanhamentoService service;

    public RegistroAcompanhamentoController(RegistroAcompanhamentoService service) {
        this.service = service;
    }

    @GetMapping("/api/colaboradores/{colaboradorId}/acompanhamentos")
    public List<Dtos.RegistroResponse> listar(@PathVariable Long colaboradorId) {
        return service.listar(colaboradorId).stream().map(Dtos.RegistroResponse::de).toList();
    }

    @PostMapping("/api/colaboradores/{colaboradorId}/acompanhamentos")
    @ResponseStatus(HttpStatus.CREATED)
    public Dtos.RegistroResponse criar(@PathVariable Long colaboradorId,
                                       @Valid @RequestBody Dtos.RegistroRequest req) {
        return Dtos.RegistroResponse.de(service.criar(colaboradorId, req));
    }
}
