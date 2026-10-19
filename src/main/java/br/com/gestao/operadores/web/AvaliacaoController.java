package br.com.gestao.operadores.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Avaliações de um colaborador. A criação e a listagem ficam sob o colaborador
 * (o dono do recurso); o acesso e a edição de uma avaliação específica usam o id dela.
 */
@RestController
public class AvaliacaoController {

    private final AvaliacaoService service;

    public AvaliacaoController(AvaliacaoService service) {
        this.service = service;
    }

    @GetMapping("/api/colaboradores/{colaboradorId}/avaliacoes")
    public List<Dtos.AvaliacaoResponse> listar(@PathVariable Long colaboradorId) {
        return service.listar(colaboradorId).stream().map(Dtos.AvaliacaoResponse::de).toList();
    }

    @PostMapping("/api/colaboradores/{colaboradorId}/avaliacoes")
    @ResponseStatus(HttpStatus.CREATED)
    public Dtos.AvaliacaoResponse criar(@PathVariable Long colaboradorId,
                                        @Valid @RequestBody Dtos.AvaliacaoRequest req) {
        return Dtos.AvaliacaoResponse.de(service.criar(colaboradorId, req));
    }

    @GetMapping("/api/avaliacoes/{id}")
    public Dtos.AvaliacaoResponse buscar(@PathVariable Long id) {
        return Dtos.AvaliacaoResponse.de(service.buscar(id));
    }

    @PutMapping("/api/avaliacoes/{id}")
    public Dtos.AvaliacaoResponse atualizar(@PathVariable Long id,
                                            @Valid @RequestBody Dtos.AvaliacaoRequest req) {
        return Dtos.AvaliacaoResponse.de(service.atualizar(id, req));
    }
}
