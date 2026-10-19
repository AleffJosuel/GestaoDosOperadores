package br.com.gestao.operadores.web;

import br.com.gestao.operadores.dominio.AcaoPdi;
import br.com.gestao.operadores.dominio.Avaliacao;
import br.com.gestao.operadores.dominio.Classificacao;
import br.com.gestao.operadores.dominio.Colaborador;
import br.com.gestao.operadores.dominio.Competencia;
import br.com.gestao.operadores.dominio.NotaCompetencia;
import br.com.gestao.operadores.dominio.StatusPdi;
import br.com.gestao.operadores.dominio.TipoCompetencia;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

public final class Dtos {
    private Dtos() {}

    static String t(String s) { return s == null ? null : s.strip(); }

    public record ColaboradorRequest(
        @NotBlank @Size(max = 30) String matricula,
        @NotBlank @Size(max = 150) String nome,
        @NotBlank @Size(max = 80) String cargo,
        @NotBlank @Size(max = 80) String setor,
        @PastOrPresent LocalDate dataAdmissao,
        @Size(max = 60) String escolaridade) {
        public ColaboradorRequest {
            matricula = t(matricula); nome = t(nome); cargo = t(cargo);
            setor = t(setor); escolaridade = t(escolaridade);
        }
    }

    public record ColaboradorResponse(Long id, String matricula, String nome, String cargo, String setor,
                                      LocalDate dataAdmissao, String escolaridade, boolean ativo) {
        public static ColaboradorResponse de(Colaborador c) {
            return new ColaboradorResponse(c.getId(), c.getMatricula(), c.getNome(), c.getCargo(),
                c.getSetor(), c.getDataAdmissao(), c.getEscolaridade(), c.isAtivo());
        }
    }

    public record CompetenciaRequest(
        @NotBlank @Size(max = 120) String nome,
        @NotNull TipoCompetencia tipo,
        String descricao,
        String criterio,
        @Min(1) @Max(5) Integer nivelEsperado,   // opcional; padrão 5
        @Size(max = 200) String acaoPadrao) {
        public CompetenciaRequest {
            nome = t(nome); descricao = t(descricao); criterio = t(criterio); acaoPadrao = t(acaoPadrao);
        }
    }

    public record CompetenciaResponse(Long id, String nome, TipoCompetencia tipo, String descricao,
                                      String criterio, int nivelEsperado, String acaoPadrao, boolean ativa) {
        public static CompetenciaResponse de(Competencia c) {
            return new CompetenciaResponse(c.getId(), c.getNome(), c.getTipo(), c.getDescricao(),
                c.getCriterio(), c.getNivelEsperado(), c.getAcaoPadrao(), c.isAtiva());
        }
    }

    public record LoginRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank String senha) {
        public LoginRequest {
            email = t(email); // só normaliza espaços; o e-mail é comparado como foi cadastrado
        }
    }

    /** expiraEmSegundos ajuda o front a saber quando renovar/pedir novo login. */
    public record TokenResponse(String token, long expiraEmSegundos) {}

    public record TrocarSenhaRequest(
        @NotBlank String senhaAtual,
        @NotBlank @Size(min = 12, max = 200) String novaSenha) {
        // Senhas não são aparadas: espaços podem ser intencionais.
    }

    public record NotaRequest(
        @NotNull Long competenciaId,
        @NotNull @Min(1) @Max(5) Integer nota) {
    }

    public record AvaliacaoRequest(
        @NotNull @PastOrPresent LocalDate dataAvaliacao,
        String observacao,
        @NotEmpty @Valid List<NotaRequest> notas) {
        public AvaliacaoRequest {
            observacao = t(observacao);
        }
    }

    public record NotaResponse(Long competenciaId, int nota) {
        public static NotaResponse de(NotaCompetencia n) {
            return new NotaResponse(n.getCompetenciaId(), n.getNota());
        }
    }

    public record AvaliacaoResponse(Long id, Long colaboradorId, LocalDate dataAvaliacao,
                                    String observacao, List<NotaResponse> notas) {
        public static AvaliacaoResponse de(Avaliacao a) {
            List<NotaResponse> notas = a.getNotas().stream()
                .map(NotaResponse::de)
                .sorted(Comparator.comparing(NotaResponse::competenciaId))
                .toList();
            return new AvaliacaoResponse(a.getId(), a.getColaboradorId(), a.getDataAvaliacao(),
                a.getObservacao(), notas);
        }
    }

    // ----- Mapa de competências e dashboard -----

    public record ClassificacaoDto(String codigo, String rotulo) {
        public static ClassificacaoDto de(Classificacao c) {
            return c == null ? null : new ClassificacaoDto(c.name(), c.getRotulo());
        }
    }

    public record MapaCompetencia(Long id, String nome, TipoCompetencia tipo,
                                  int nivelEsperado, Double media) {}

    /** nota e classificacao são nulos quando o colaborador ainda não tem nota nessa competência. */
    public record MapaCelula(Long competenciaId, Integer nota, ClassificacaoDto classificacao) {}

    public record MapaLinha(Long colaboradorId, String nome, String cargo, String setor,
                            Double media, ClassificacaoDto classificacao, List<MapaCelula> celulas) {}

    public record MapaResponse(List<MapaCompetencia> competencias, List<MapaLinha> colaboradores) {}

    public record FaixaContagem(ClassificacaoDto classificacao, long quantidade) {}

    public record CompetenciaMedia(Long id, String nome, Double media) {}

    public record DashboardResponse(int colaboradoresAtivos, int competenciasAtivas,
                                    long totalAvaliacoes, int colaboradoresAvaliados,
                                    Double mediaGeral, List<FaixaContagem> distribuicao,
                                    List<CompetenciaMedia> competenciasCriticas,
                                    long pdiAbertas, long pdiAtrasadas, long pdiConcluidas) {}

    // ----- PDI (plano de desenvolvimento individual) -----

    /**
     * Usado para criar e editar. Na criação, competenciaId e avaliacaoOrigemId são obrigatórios
     * (validado no serviço); na edição eles são ignorados (não mudam depois de criados).
     */
    public record PdiRequest(
        Long competenciaId,
        Long avaliacaoOrigemId,
        @NotBlank String descricao,
        @Size(max = 150) String responsavel,
        @Size(max = 80) String funcaoArea,
        @Positive Integer cargaHorariaMinutos,
        LocalDate prazo,
        StatusPdi status,
        LocalDate dataConclusao,
        String evidencia,
        String observacao) {
        public PdiRequest {
            descricao = t(descricao);
            responsavel = t(responsavel);
            funcaoArea = t(funcaoArea);
            evidencia = t(evidencia);
            observacao = t(observacao);
        }
    }

    // ----- Importação (CSV) -----

    public record LinhaImportacao(long linha, boolean ok, String mensagem) {}

    public record ImportacaoResultado(int total, int criados, int falhas,
                                      List<LinhaImportacao> linhas) {}

    public record PdiResponse(Long id, Long colaboradorId, Long competenciaId, Long avaliacaoOrigemId,
                              String descricao, String responsavel, String funcaoArea,
                              Integer cargaHorariaMinutos, LocalDate prazo, StatusPdi status,
                              LocalDate dataConclusao, String evidencia, String observacao) {
        public static PdiResponse de(AcaoPdi a) {
            return new PdiResponse(a.getId(), a.getColaboradorId(), a.getCompetenciaId(),
                a.getAvaliacaoOrigemId(), a.getDescricao(), a.getResponsavel(), a.getFuncaoArea(),
                a.getCargaHorariaMinutos(), a.getPrazo(), a.getStatus(), a.getDataConclusao(),
                a.getEvidencia(), a.getObservacao());
        }
    }
}
