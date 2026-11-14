package br.com.gestao.operadores.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Visões agregadas: mapa de competências e dashboard do supervisor logado. */
@RestController
public class PainelController {

    private final PainelService service;

    public PainelController(PainelService service) {
        this.service = service;
    }

    @GetMapping("/api/mapa")
    public Dtos.MapaResponse mapa() {
        return service.mapa();
    }

    @GetMapping("/api/dashboard")
    public Dtos.DashboardResponse dashboard() {
        return service.dashboard();
    }
}
