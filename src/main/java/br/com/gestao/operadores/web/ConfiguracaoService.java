package br.com.gestao.operadores.web;

import br.com.gestao.operadores.dominio.ConfiguracaoClassificacao;
import br.com.gestao.operadores.dominio.ConfiguracaoClassificacaoRepository;
import br.com.gestao.operadores.seguranca.SupervisorLogado;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Configuração do supervisor logado: faixas de classificação (limites crescentes) e os rótulos
 * das notas 1–5. Leitura devolve o que está salvo (ou os padrões); a gravação valida e faz upsert.
 */
@Service
public class ConfiguracaoService {

    private static final List<Dtos.RotuloNotaDto> ROTULOS_PADRAO = List.of(
        new Dtos.RotuloNotaDto(1, "Crítico"),
        new Dtos.RotuloNotaDto(2, "A Desenvolver"),
        new Dtos.RotuloNotaDto(3, "Adequado"),
        new Dtos.RotuloNotaDto(4, "Bom"),
        new Dtos.RotuloNotaDto(5, "Excelente"));

    private final ConfiguracaoClassificacaoRepository configuracoes;
    private final JdbcTemplate jdbc;
    private final SupervisorLogado logado;

    public ConfiguracaoService(ConfiguracaoClassificacaoRepository configuracoes, JdbcTemplate jdbc,
                               SupervisorLogado logado) {
        this.configuracoes = configuracoes;
        this.jdbc = jdbc;
        this.logado = logado;
    }

    @Transactional(readOnly = true)
    public Dtos.ConfiguracaoResponse obter() {
        Long supervisor = logado.id();
        ConfiguracaoClassificacao cfg = configuracoes.findById(supervisor)
            .orElseGet(ConfiguracaoClassificacao::padrao);
        List<Dtos.RotuloNotaDto> rotulos = jdbc.query(
            "SELECT nota, rotulo FROM rotulo_nota WHERE supervisor_id = ? ORDER BY nota",
            (rs, i) -> new Dtos.RotuloNotaDto(rs.getInt("nota"), rs.getString("rotulo")),
            supervisor);
        if (rotulos.isEmpty()) rotulos = ROTULOS_PADRAO;
        return new Dtos.ConfiguracaoResponse(cfg.getLimiteCritico(), cfg.getLimiteDesenvolver(),
            cfg.getLimiteBom(), rotulos);
    }

    @Transactional
    public Dtos.ConfiguracaoResponse salvar(Dtos.ConfiguracaoRequest r) {
        boolean crescentes = r.limiteCritico().compareTo(r.limiteDesenvolver()) < 0
            && r.limiteDesenvolver().compareTo(r.limiteBom()) < 0;
        if (!crescentes) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Os limites precisam ser crescentes: crítico < a desenvolver < bom.");
        }
        Set<Integer> notas = r.rotulos().stream()
            .map(Dtos.RotuloNotaDto::nota).collect(Collectors.toSet());
        if (!notas.equals(Set.of(1, 2, 3, 4, 5))) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Informe um rótulo para cada nota de 1 a 5, sem repetir.");
        }

        Long supervisor = logado.id();
        jdbc.update("""
            INSERT INTO configuracao_classificacao
                (supervisor_id, limite_critico, limite_desenvolver, limite_bom)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (supervisor_id) DO UPDATE SET
                limite_critico = EXCLUDED.limite_critico,
                limite_desenvolver = EXCLUDED.limite_desenvolver,
                limite_bom = EXCLUDED.limite_bom
            """, supervisor, r.limiteCritico(), r.limiteDesenvolver(), r.limiteBom());

        for (Dtos.RotuloNotaDto rn : r.rotulos()) {
            jdbc.update("""
                INSERT INTO rotulo_nota (supervisor_id, nota, rotulo)
                VALUES (?, ?, ?)
                ON CONFLICT (supervisor_id, nota) DO UPDATE SET rotulo = EXCLUDED.rotulo
                """, supervisor, rn.nota(), rn.rotulo());
        }
        return obter();
    }
}
