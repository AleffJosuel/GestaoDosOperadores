import type {
  AcaoPdi,
  Avaliacao,
  AvaliacaoInput,
  Colaborador,
  ColaboradorInput,
  Competencia,
  CompetenciaInput,
  Configuracao,
  DashboardResponse,
  ImportacaoResultado,
  MapaResponse,
  PdiInput,
  PdiItem,
  Registro,
  RegistroInput,
  TokenResponse,
} from "./types";

/**
 * Raiz da API. Vazio = mesma origem do site, que é o caso da instalação no servidor da empresa:
 * o JAR serve a interface e a API na mesma porta, então `/api/...` já chega no lugar certo.
 * No desenvolvimento o Vite roda em :5173 e o backend em :8080, e aí o `.env` aponta pra lá.
 */
const BASE = (import.meta.env.VITE_API_URL ?? "").replace(/\/$/, "");

let token: string | null = null;
let aoDeslogar: (() => void) | null = null;

export function definirToken(novo: string | null) {
  token = novo;
}

export function aoReceber401(callback: () => void) {
  aoDeslogar = callback;
}

/** Erro de API com status e, quando houver, erros por campo vindos do backend. */
export class ApiError extends Error {
  status: number;
  campos?: Record<string, string>;
  constructor(status: number, mensagem: string, campos?: Record<string, string>) {
    super(mensagem);
    this.status = status;
    this.campos = campos;
  }
}

/** Mensagem para quando o corpo do erro não traz uma (ex.: página HTML de um proxy). */
function mensagemPadrao(status: number): string {
  if (status === 403) return "Você não tem permissão para esta ação.";
  if (status === 404) return "Registro não encontrado.";
  if (status >= 502 && status <= 504) return "O servidor está indisponível no momento. Tente novamente.";
  if (status >= 500) return "Erro interno no servidor. Tente novamente.";
  return `Erro ${status} na requisição`;
}

/**
 * Lê a resposta como JSON. Corpo de erro que não seja JSON vira a mensagem padrão do status
 * (antes estourava no JSON.parse e aparecia só "Erro inesperado").
 */
async function lerResposta<T>(resp: Response): Promise<T> {
  const texto = await resp.text();
  let dados: { erro?: string; message?: string; campos?: Record<string, string> } | undefined;
  try {
    dados = texto ? JSON.parse(texto) : undefined;
  } catch {
    if (resp.ok) throw new ApiError(resp.status, "Resposta inválida do servidor.");
    throw new ApiError(resp.status, mensagemPadrao(resp.status));
  }
  if (!resp.ok) {
    const mensagem = (dados && (dados.erro || dados.message)) || mensagemPadrao(resp.status);
    throw new ApiError(resp.status, mensagem, dados?.campos);
  }
  return dados as T;
}

async function request<T>(metodo: string, caminho: string, corpo?: unknown): Promise<T> {
  const headers: Record<string, string> = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (corpo !== undefined) headers["Content-Type"] = "application/json";

  let resp: Response;
  try {
    resp = await fetch(`${BASE}${caminho}`, {
      method: metodo,
      headers,
      body: corpo !== undefined ? JSON.stringify(corpo) : undefined,
    });
  } catch {
    throw new ApiError(0, "Não foi possível falar com o servidor. Ele está no ar?");
  }

  if (resp.status === 401) {
    if (token) {
      // Havia sessão: o token expirou ou foi invalidado → desloga.
      if (aoDeslogar) aoDeslogar();
      throw new ApiError(401, "Sessão expirada. Entre novamente.");
    }
    // Sem token (ex.: login): usa a mensagem do servidor (credenciais inválidas).
    const txt = await resp.text().catch(() => "");
    let msg = "Credenciais inválidas";
    try {
      const j = txt ? JSON.parse(txt) : null;
      if (j && (j.erro || j.message)) msg = j.erro || j.message;
    } catch {
      /* corpo não-JSON: mantém a mensagem padrão */
    }
    throw new ApiError(401, msg);
  }

  if (resp.status === 204) return undefined as T;
  return lerResposta<T>(resp);
}

/** Envio multipart (upload de arquivo). Mesmo tratamento de erro do request(). */
async function upload<T>(caminho: string, arquivo: File): Promise<T> {
  const headers: Record<string, string> = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  // Não definimos Content-Type: o navegador adiciona o boundary do multipart.
  const form = new FormData();
  form.append("arquivo", arquivo);

  let resp: Response;
  try {
    resp = await fetch(`${BASE}${caminho}`, { method: "POST", headers, body: form });
  } catch {
    throw new ApiError(0, "Não foi possível falar com o servidor. Ele está no ar?");
  }

  if (resp.status === 401) {
    if (token && aoDeslogar) aoDeslogar();
    throw new ApiError(401, "Sessão expirada. Entre novamente.");
  }
  return lerResposta<T>(resp);
}

/** Arquivo vindo da API, com o nome que o servidor escolheu. */
export type ArquivoBaixado = { blob: Blob; nome: string };

/**
 * Baixa um arquivo gerado pela API.
 *
 * <p>Um `<a href>` apontando direto para o endpoint não serve: o navegador não manda o cabeçalho
 * Authorization ao seguir um link, e a resposta seria 401. Por isso a requisição passa pelo fetch,
 * como as outras, e o resultado vira um arquivo no disco pelo `salvarArquivo`.
 */
async function baixar(caminho: string): Promise<ArquivoBaixado> {
  const headers: Record<string, string> = {};
  if (token) headers.Authorization = `Bearer ${token}`;

  let resp: Response;
  try {
    resp = await fetch(`${BASE}${caminho}`, { headers });
  } catch {
    throw new ApiError(0, "Não foi possível falar com o servidor. Ele está no ar?");
  }

  if (resp.status === 401) {
    if (aoDeslogar) aoDeslogar();
    throw new ApiError(401, "Sessão expirada. Entre novamente.");
  }
  if (!resp.ok) {
    throw new ApiError(resp.status, mensagemPadrao(resp.status));
  }
  return { blob: await resp.blob(), nome: nomeDoArquivo(resp) };
}

/** Nome do arquivo vindo do Content-Disposition; o servidor é quem decide, inclusive a data. */
function nomeDoArquivo(resp: Response): string {
  const disposicao = resp.headers.get("Content-Disposition") ?? "";
  // filename*=UTF-8'' vem primeiro de propósito: é a forma que preserva acento.
  const comAcento = /filename\*=UTF-8''([^;]+)/i.exec(disposicao);
  if (comAcento) {
    try {
      return decodeURIComponent(comAcento[1]);
    } catch {
      /* percentagem malformada: cai no formato simples abaixo */
    }
  }
  const simples = /filename="?([^";]+)"?/i.exec(disposicao);
  return simples ? simples[1] : "exportacao.csv";
}

export const api = {
  login: (email: string, senha: string) =>
    request<TokenResponse>("POST", "/api/sessao", { email, senha }),
  trocarSenha: (senhaAtual: string, novaSenha: string) =>
    request<void>("POST", "/api/sessao/senha", { senhaAtual, novaSenha }),

  // Colaboradores
  listarColaboradores: (incluirInativos = false) =>
    request<Colaborador[]>("GET", `/api/colaboradores?incluirInativos=${incluirInativos}`),
  buscarColaborador: (id: number) => request<Colaborador>("GET", `/api/colaboradores/${id}`),
  criarColaborador: (dados: ColaboradorInput) =>
    request<Colaborador>("POST", "/api/colaboradores", dados),
  atualizarColaborador: (id: number, dados: ColaboradorInput) =>
    request<Colaborador>("PUT", `/api/colaboradores/${id}`, dados),
  desativarColaborador: (id: number) =>
    request<Colaborador>("POST", `/api/colaboradores/${id}/desativar`),
  reativarColaborador: (id: number) =>
    request<Colaborador>("POST", `/api/colaboradores/${id}/reativar`),
  /** Exclusão definitiva (LGPD): apaga o colaborador e todo o histórico dele. Irreversível. */
  excluirColaborador: (id: number) => request<void>("DELETE", `/api/colaboradores/${id}`),

  // Competências
  listarCompetencias: (incluirInativas = false) =>
    request<Competencia[]>("GET", `/api/competencias?incluirInativas=${incluirInativas}`),
  criarCompetencia: (dados: CompetenciaInput) =>
    request<Competencia>("POST", "/api/competencias", dados),
  atualizarCompetencia: (id: number, dados: CompetenciaInput) =>
    request<Competencia>("PUT", `/api/competencias/${id}`, dados),
  desativarCompetencia: (id: number) =>
    request<Competencia>("POST", `/api/competencias/${id}/desativar`),
  reativarCompetencia: (id: number) =>
    request<Competencia>("POST", `/api/competencias/${id}/reativar`),

  // Importação CSV
  importarColaboradores: (arquivo: File) =>
    upload<ImportacaoResultado>("/api/colaboradores/importar", arquivo),
  importarCompetencias: (arquivo: File) =>
    upload<ImportacaoResultado>("/api/competencias/importar", arquivo),

  // Avaliações
  listarAvaliacoes: (colaboradorId: number) =>
    request<Avaliacao[]>("GET", `/api/colaboradores/${colaboradorId}/avaliacoes`),
  criarAvaliacao: (colaboradorId: number, dados: AvaliacaoInput) =>
    request<Avaliacao>("POST", `/api/colaboradores/${colaboradorId}/avaliacoes`, dados),

  // PDI
  listarTodosPdi: () => request<PdiItem[]>("GET", "/api/pdi"),
  listarPdi: (colaboradorId: number) =>
    request<AcaoPdi[]>("GET", `/api/colaboradores/${colaboradorId}/pdi`),
  criarPdi: (colaboradorId: number, dados: PdiInput) =>
    request<AcaoPdi>("POST", `/api/colaboradores/${colaboradorId}/pdi`, dados),
  atualizarPdi: (id: number, dados: PdiInput) =>
    request<AcaoPdi>("PUT", `/api/pdi/${id}`, dados),

  // Registros de acompanhamento
  listarAcompanhamentos: (colaboradorId: number) =>
    request<Registro[]>("GET", `/api/colaboradores/${colaboradorId}/acompanhamentos`),
  criarAcompanhamento: (colaboradorId: number, dados: RegistroInput) =>
    request<Registro>("POST", `/api/colaboradores/${colaboradorId}/acompanhamentos`, dados),

  // Painel
  mapa: () => request<MapaResponse>("GET", "/api/mapa"),
  dashboard: () => request<DashboardResponse>("GET", "/api/dashboard"),

  // Exportação em CSV (o servidor monta o arquivo e escolhe o nome)
  exportarMapa: () => baixar("/api/mapa/exportar"),
  exportarDashboard: () => baixar("/api/dashboard/exportar"),

  // Configuração (faixas + rótulos das notas)
  obterConfiguracao: () => request<Configuracao>("GET", "/api/configuracao"),
  salvarConfiguracao: (dados: Configuracao) =>
    request<Configuracao>("PUT", "/api/configuracao", dados),
};
