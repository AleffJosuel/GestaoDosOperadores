// Espelham os records do backend (br.com.gestao.operadores.web.Dtos).

export type TipoCompetencia = "COMPORTAMENTAL" | "TECNICA";

export interface Colaborador {
  id: number;
  matricula: string;
  nome: string;
  cargo: string;
  setor: string;
  dataAdmissao: string | null;
  escolaridade: string | null;
  ativo: boolean;
}

export interface ColaboradorInput {
  matricula: string;
  nome: string;
  cargo: string;
  setor: string;
  dataAdmissao?: string | null;
  escolaridade?: string | null;
}

export interface Competencia {
  id: number;
  nome: string;
  tipo: TipoCompetencia;
  descricao: string | null;
  criterio: string | null;
  nivelEsperado: number;
  acaoPadrao: string | null;
  ativa: boolean;
}

export interface CompetenciaInput {
  nome: string;
  tipo: TipoCompetencia;
  descricao?: string | null;
  criterio?: string | null;
  nivelEsperado?: number | null;
  acaoPadrao?: string | null;
}

export interface NotaInput {
  competenciaId: number;
  nota: number;
}

export interface AvaliacaoInput {
  dataAvaliacao: string;
  observacao?: string | null;
  notas: NotaInput[];
}

export interface Avaliacao {
  id: number;
  colaboradorId: number;
  dataAvaliacao: string;
  observacao: string | null;
  notas: { competenciaId: number; nota: number }[];
}

export interface ClassificacaoDto {
  codigo: "CRITICO" | "A_DESENVOLVER" | "ADEQUADO" | "BOM";
  rotulo: string;
}

export interface MapaCompetencia {
  id: number;
  nome: string;
  tipo: TipoCompetencia;
  nivelEsperado: number;
  media: number | null;
}

export interface MapaCelula {
  competenciaId: number;
  nota: number | null;
  classificacao: ClassificacaoDto | null;
}

export interface MapaLinha {
  colaboradorId: number;
  nome: string;
  cargo: string;
  setor: string;
  media: number | null;
  classificacao: ClassificacaoDto | null;
  celulas: MapaCelula[];
}

export interface MapaResponse {
  competencias: MapaCompetencia[];
  colaboradores: MapaLinha[];
}

export interface FaixaContagem {
  classificacao: ClassificacaoDto;
  quantidade: number;
}

export interface CompetenciaMedia {
  id: number;
  nome: string;
  media: number | null;
}

export interface DashboardResponse {
  colaboradoresAtivos: number;
  competenciasAtivas: number;
  totalAvaliacoes: number;
  colaboradoresAvaliados: number;
  mediaGeral: number | null;
  distribuicao: FaixaContagem[];
  competenciasCriticas: CompetenciaMedia[];
  pdiAbertas: number;
  pdiAtrasadas: number;
  pdiConcluidas: number;
}

export interface TokenResponse {
  token: string;
  expiraEmSegundos: number;
}

export interface RotuloNota {
  nota: number;
  rotulo: string;
}

export interface Configuracao {
  limiteCritico: number;
  limiteDesenvolver: number;
  limiteBom: number;
  rotulos: RotuloNota[];
}

export type TipoRegistro = "FEEDBACK" | "CONVERSA" | "OCORRENCIA" | "OUTRO";

export interface Registro {
  id: number;
  colaboradorId: number;
  tipo: TipoRegistro;
  dataEvento: string;
  descricao: string;
  criadoEm: string;
}

export interface RegistroInput {
  tipo: TipoRegistro;
  dataEvento: string;
  descricao: string;
}

export interface LinhaImportacao {
  linha: number;
  ok: boolean;
  mensagem: string;
}

export interface ImportacaoResultado {
  total: number;
  criados: number;
  falhas: number;
  linhas: LinhaImportacao[];
}

export type StatusPdi = "NAO_INICIADO" | "PLANEJADO" | "EM_ANDAMENTO" | "CONCLUIDO";

export interface AcaoPdi {
  id: number;
  colaboradorId: number;
  competenciaId: number;
  avaliacaoOrigemId: number;
  descricao: string;
  responsavel: string | null;
  funcaoArea: string | null;
  cargaHorariaMinutos: number | null;
  prazo: string | null;
  status: StatusPdi;
  dataConclusao: string | null;
  evidencia: string | null;
  observacao: string | null;
}

export interface PdiInput {
  competenciaId?: number | null;
  avaliacaoOrigemId?: number | null;
  descricao: string;
  responsavel?: string | null;
  funcaoArea?: string | null;
  cargaHorariaMinutos?: number | null;
  prazo?: string | null;
  status?: StatusPdi | null;
  dataConclusao?: string | null;
  evidencia?: string | null;
  observacao?: string | null;
}
