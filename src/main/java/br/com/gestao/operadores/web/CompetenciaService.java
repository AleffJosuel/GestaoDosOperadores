package br.com.gestao.operadores.web;

import br.com.gestao.operadores.dominio.Competencia;
import br.com.gestao.operadores.dominio.CompetenciaRepository;
import br.com.gestao.operadores.seguranca.SupervisorLogado;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class CompetenciaService {

    private static final short NIVEL_ESPERADO_PADRAO = 5;

    private final CompetenciaRepository competencias;
    private final SupervisorLogado logado;

    public CompetenciaService(CompetenciaRepository competencias, SupervisorLogado logado) {
        this.competencias = competencias;
        this.logado = logado;
    }

    public List<Competencia> listar(boolean incluirInativas) {
        Long supervisorId = logado.id();
        return incluirInativas
            ? competencias.findBySupervisorIdOrderByNomeAsc(supervisorId)
            : competencias.findBySupervisorIdAndAtivaTrueOrderByNomeAsc(supervisorId);
    }

    public Competencia buscar(Long id) {
        return competencias.findByIdAndSupervisorId(id, logado.id())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Competência não encontrada"));
    }

    @Transactional
    public Competencia criar(Dtos.CompetenciaRequest r) {
        Long supervisorId = logado.id();
        if (competencias.existsBySupervisorIdAndNomeIgnoreCase(supervisorId, r.nome())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe uma competência com este nome");
        }
        return competencias.save(new Competencia(supervisorId, r.nome(), r.tipo(), r.descricao(),
            r.criterio(), nivel(r), r.acaoPadrao()));
    }

    @Transactional
    public Competencia atualizar(Long id, Dtos.CompetenciaRequest r) {
        Competencia c = buscar(id);
        if (competencias.existsBySupervisorIdAndNomeIgnoreCaseAndIdNot(c.getSupervisorId(), r.nome(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe uma competência com este nome");
        }
        c.atualizar(r.nome(), r.tipo(), r.descricao(), r.criterio(), nivel(r), r.acaoPadrao());
        return c;
    }

    /** Competência usada em avaliações antigas nunca é apagada: apenas desativada. */
    @Transactional
    public Competencia alterarAtiva(Long id, boolean ativa) {
        Competencia c = buscar(id);
        c.definirAtiva(ativa);
        return c;
    }

    private static short nivel(Dtos.CompetenciaRequest r) {
        return r.nivelEsperado() == null ? NIVEL_ESPERADO_PADRAO : r.nivelEsperado().shortValue();
    }
}
