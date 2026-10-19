package br.com.gestao.operadores.web;

import br.com.gestao.operadores.dominio.Colaborador;
import br.com.gestao.operadores.dominio.ColaboradorRepository;
import br.com.gestao.operadores.seguranca.SupervisorLogado;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Todo acesso a colaborador passa por aqui e é filtrado pelo supervisor logado.
 * Colaborador de outro supervisor responde 404 (e não 403), para não revelar que existe.
 */
@Service
public class ColaboradorService {

    private final ColaboradorRepository colaboradores;
    private final SupervisorLogado logado;

    public ColaboradorService(ColaboradorRepository colaboradores, SupervisorLogado logado) {
        this.colaboradores = colaboradores;
        this.logado = logado;
    }

    public List<Colaborador> listar(boolean incluirInativos) {
        Long supervisorId = logado.id();
        return incluirInativos
            ? colaboradores.findBySupervisorIdOrderByNomeAsc(supervisorId)
            : colaboradores.findBySupervisorIdAndAtivoTrueOrderByNomeAsc(supervisorId);
    }

    public Colaborador buscar(Long id) {
        return colaboradores.findByIdAndSupervisorId(id, logado.id())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Colaborador não encontrado"));
    }

    @Transactional
    public Colaborador criar(Dtos.ColaboradorRequest r) {
        Long supervisorId = logado.id();
        if (colaboradores.existsBySupervisorIdAndMatricula(supervisorId, r.matricula())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Matrícula já cadastrada");
        }
        return colaboradores.save(new Colaborador(supervisorId, r.matricula(), r.nome(), r.cargo(),
            r.setor(), r.dataAdmissao(), r.escolaridade()));
    }

    @Transactional
    public Colaborador atualizar(Long id, Dtos.ColaboradorRequest r) {
        Colaborador c = buscar(id);
        if (colaboradores.existsBySupervisorIdAndMatriculaAndIdNot(c.getSupervisorId(), r.matricula(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Matrícula já cadastrada");
        }
        c.atualizar(r.matricula(), r.nome(), r.cargo(), r.setor(), r.dataAdmissao(), r.escolaridade());
        return c;
    }

    /** Desativar preserva o histórico; não existe exclusão de colaborador. */
    @Transactional
    public Colaborador alterarAtivo(Long id, boolean ativo) {
        Colaborador c = buscar(id);
        c.definirAtivo(ativo);
        return c;
    }
}
