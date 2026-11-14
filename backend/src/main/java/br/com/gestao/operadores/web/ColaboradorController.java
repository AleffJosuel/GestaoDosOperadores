package br.com.gestao.operadores.web;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/colaboradores")
public class ColaboradorController {

    private final ColaboradorService service;

    public ColaboradorController(ColaboradorService service) {
        this.service = service;
    }

    @GetMapping
    public List<Dtos.ColaboradorResponse> listar(@RequestParam(defaultValue = "false") boolean incluirInativos) {
        return service.listar(incluirInativos).stream().map(Dtos.ColaboradorResponse::de).toList();
    }

    @GetMapping("/{id}")
    public Dtos.ColaboradorResponse buscar(@PathVariable Long id) {
        return Dtos.ColaboradorResponse.de(service.buscar(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Dtos.ColaboradorResponse criar(@Valid @RequestBody Dtos.ColaboradorRequest req) {
        return Dtos.ColaboradorResponse.de(service.criar(req));
    }

    @PutMapping("/{id}")
    public Dtos.ColaboradorResponse atualizar(@PathVariable Long id, @Valid @RequestBody Dtos.ColaboradorRequest req) {
        return Dtos.ColaboradorResponse.de(service.atualizar(id, req));
    }

    @PostMapping("/{id}/desativar")
    public Dtos.ColaboradorResponse desativar(@PathVariable Long id) {
        return Dtos.ColaboradorResponse.de(service.alterarAtivo(id, false));
    }

    @PostMapping("/{id}/reativar")
    public Dtos.ColaboradorResponse reativar(@PathVariable Long id) {
        return Dtos.ColaboradorResponse.de(service.alterarAtivo(id, true));
    }

    /**
     * Exclusão definitiva, para atender pedido de eliminação de dados do titular (LGPD).
     * Apaga avaliações, PDI e acompanhamentos junto, sem volta. O caminho do dia a dia para quem
     * saiu da empresa continua sendo {@code /desativar}, que preserva o histórico.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long id) {
        service.excluirDefinitivamente(id);
    }
}
