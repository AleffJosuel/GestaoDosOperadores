package br.com.gestao.operadores.web;

import br.com.gestao.operadores.dominio.RegistroAcompanhamento;
import br.com.gestao.operadores.dominio.RegistroAcompanhamentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Registros de acompanhamento (feedback, conversa, ocorrência...). Tudo é filtrado pelo supervisor
 * logado pela cadeia registro → colaborador → supervisor: o colaborador é carregado por
 * {@link ColaboradorService#buscar}, que responde 404 a quem não é o dono. É apenas-acréscimo.
 */
@Service
public class RegistroAcompanhamentoService {

    private final RegistroAcompanhamentoRepository registros;
    private final ColaboradorService colaboradorService;

    public RegistroAcompanhamentoService(RegistroAcompanhamentoRepository registros,
                                         ColaboradorService colaboradorService) {
        this.registros = registros;
        this.colaboradorService = colaboradorService;
    }

    @Transactional(readOnly = true)
    public List<RegistroAcompanhamento> listar(Long colaboradorId) {
        colaboradorService.buscar(colaboradorId); // garante que o colaborador é do supervisor logado
        return registros.findByColaboradorIdOrderByDataEventoDescIdDesc(colaboradorId);
    }

    @Transactional
    public RegistroAcompanhamento criar(Long colaboradorId, Dtos.RegistroRequest r) {
        colaboradorService.buscar(colaboradorId);
        return registros.save(new RegistroAcompanhamento(
            colaboradorId, r.tipo(), r.dataEvento(), r.descricao()));
    }
}
