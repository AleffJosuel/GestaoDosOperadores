package br.com.gestao.operadores.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Ações de PDI. A criação e a listagem ficam sob o colaborador (dono do recurso);
 * o acesso e a edição de uma ação específica usam o id dela.
 */
@RestController
public class AcaoPdiController {

    private final AcaoPdiService service;

    public AcaoPdiController(AcaoPdiService service) {
        this.service = service;
    }

    @GetMapping("/api/pdi")
    public List<Dtos.PdiItem> listarTodos() {
        return service.listarTodos();
    }

    @GetMapping("/api/colaboradores/{colaboradorId}/pdi")
    public List<Dtos.PdiResponse> listar(@PathVariable Long colaboradorId) {
        return service.listar(colaboradorId).stream().map(Dtos.PdiResponse::de).toList();
    }

    @PostMapping("/api/colaboradores/{colaboradorId}/pdi")
    @ResponseStatus(HttpStatus.CREATED)
    public Dtos.PdiResponse criar(@PathVariable Long colaboradorId,
                                  @Valid @RequestBody Dtos.PdiRequest req) {
        return Dtos.PdiResponse.de(service.criar(colaboradorId, req));
    }

    @GetMapping("/api/pdi/{id}")
    public Dtos.PdiResponse buscar(@PathVariable Long id) {
        return Dtos.PdiResponse.de(service.buscar(id));
    }

    @PutMapping("/api/pdi/{id}")
    public Dtos.PdiResponse atualizar(@PathVariable Long id,
                                      @Valid @RequestBody Dtos.PdiRequest req) {
        return Dtos.PdiResponse.de(service.atualizar(id, req));
    }
}
