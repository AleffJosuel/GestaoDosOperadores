package br.com.gestao.operadores.web;

import br.com.gestao.operadores.dominio.AcaoPdiRepository;
import br.com.gestao.operadores.dominio.AvaliacaoRepository;
import br.com.gestao.operadores.dominio.Colaborador;
import br.com.gestao.operadores.dominio.ColaboradorRepository;
import br.com.gestao.operadores.dominio.RegistroAcompanhamentoRepository;
import br.com.gestao.operadores.seguranca.SupervisorLogado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(ColaboradorService.class);

    private final ColaboradorRepository colaboradores;
    private final AvaliacaoRepository avaliacoes;
    private final AcaoPdiRepository acoesPdi;
    private final RegistroAcompanhamentoRepository registros;
    private final SupervisorLogado logado;

    public ColaboradorService(ColaboradorRepository colaboradores, AvaliacaoRepository avaliacoes,
                              AcaoPdiRepository acoesPdi, RegistroAcompanhamentoRepository registros,
                              SupervisorLogado logado) {
        this.colaboradores = colaboradores;
        this.avaliacoes = avaliacoes;
        this.acoesPdi = acoesPdi;
        this.registros = registros;
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

    /** Desativar preserva o histórico e é o caminho normal para quem saiu da empresa. */
    @Transactional
    public Colaborador alterarAtivo(Long id, boolean ativo) {
        Colaborador c = buscar(id);
        c.definirAtivo(ativo);
        return c;
    }

    /**
     * Apaga o colaborador e tudo que pende dele: avaliações (com as notas), ações de PDI e
     * registros de acompanhamento. Irreversível.
     *
     * <p>Existe para atender ao direito de eliminação do titular (LGPD, art. 18, VI) — a pergunta
     * que RH faz como "e quando a pessoa pedir para apagar os dados dela?". Desativar não serve
     * para isso: preserva o histórico de propósito, que é o oposto do pedido.
     *
     * <p>A ordem importa: PDI e acompanhamentos referenciam o colaborador, e PDI referencia a
     * avaliação de origem — apagar o colaborador antes dos filhos violaria a chave estrangeira.
     *
     * <p>Só o que pertence ao supervisor logado pode ser apagado: o {@link #buscar} na primeira
     * linha garante isso, e colaborador de outro supervisor responde 404 como em todo o resto.
     * A operação fica na trilha de auditoria (DELETE /api/colaboradores/{id}); o nome do
     * colaborador não vai para o log, só o id — apagar o dado e deixar cópia no log seria
     * contraditório.
     */
    @Transactional
    public void excluirDefinitivamente(Long id) {
        Colaborador c = buscar(id);
        acoesPdi.deleteByColaboradorId(c.getId());
        registros.deleteByColaboradorId(c.getId());
        avaliacoes.deleteByColaboradorId(c.getId());
        colaboradores.delete(c);
        log.info("Exclusão definitiva do colaborador id={} (supervisor id={})", id, c.getSupervisorId());
    }
}
