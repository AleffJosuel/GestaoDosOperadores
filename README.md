# Gestão de Competências e PDI — API

Back-end (Spring Boot 3 + PostgreSQL) do sistema do supervisor de turno.
**Entrega 1:** autenticação, colaboradores e banco de competências.
**Entrega 2:** avaliações com nota (1–5) por competência e histórico por colaborador.
**Entrega 3:** login com token JWT (substitui o HTTP Basic), mapa de competências e dashboard.
**Entrega 4 (atual):** PDI — plano de desenvolvimento individual (ações ligadas a avaliações/competências);
indicadores de PDI no dashboard; ficha do colaborador (impressão/PDF); rate limiting no login e cabeçalhos
de segurança (CSP); e melhorias de UX (toasts, confirmações, busca).
Há um front-end em `frontend/` (Vite + React + TypeScript). A importação virá depois.

## Requisitos
- JDK 21 (o projeto não é compatível com versões mais novas, como o 26)
- PostgreSQL 16+ (instalado no Windows) **ou** Docker (`docker compose up -d`)
- VSCode com a **Extension Pack for Java** (opcionalmente a **Spring Boot Extension Pack**)

## Banco (PostgreSQL instalado)
No SQL Shell (psql), como usuário `postgres`:

    CREATE USER gestao WITH PASSWORD 'gestao_dev';
    CREATE DATABASE gestao_operadores OWNER gestao;

As tabelas são criadas pelo Flyway na primeira execução.

## Executando (VSCode)
1. Abra a pasta que contém o `pom.xml`. A extensão Java importa as dependências sozinha
   (não é preciso Maven instalado; o build e os testes rodam pela própria extensão).
2. Garanta que o projeto usa o **JDK 21** (JDK 26 não é compatível): instale o JDK 21 e
   selecione-o em *Java: Configure Java Runtime* ou na barra inferior do editor.
3. Copie `.env.example` para `.env` e preencha `ADMIN_EMAIL`, `ADMIN_PASSWORD`
   (senha com 12+ caracteres) e `JWT_SECRET` (32+ caracteres aleatórios — a app não sobe sem ele).
   O `.env` já é ignorado pelo git — não versione esses valores.
4. Rode pela aba *Run and Debug* a configuração `GestaoCompetenciasApplication`
   (ela lê o `.env`), ou use o *Spring Boot Dashboard*.
5. O log deve terminar com `Started GestaoCompetenciasApplication` e `Conta de supervisor criada`.

Para rodar os testes: abra o *Testing* (Test Explorer) e execute, ou clique em *Run Test*
na classe `AvaliacaoServiceTest`.

## Contas e login (não há cadastro público)

Só existem as contas que você provisiona pelo `.env` — de propósito, para ninguém mais se
cadastrar. Ao subir, a aplicação **cria a conta de `ADMIN_EMAIL`/`ADMIN_PASSWORD` se aquele
e-mail ainda não existir** (idempotente). Para ter duas contas:

1. Preencha o `.env` com **seus** dados de teste e suba a aplicação → cria sua conta.
2. Troque o `.env` para o **e-mail do supervisor** com uma **senha temporária** e suba de novo
   → cria a conta dele. (Reiniciar depois não recria nem sobrescreve nada.)
3. No primeiro acesso, cada um usa **Trocar senha** (na barra lateral) para definir a própria
   senha. Assim o supervisor é o único que sabe a senha dele, e você pode apagar a temporária
   do seu `.env`.

Trocar a senha não invalida tokens já emitidos; eles expiram pelo tempo normal (`JWT_EXP_MIN`).

## Testando (PowerShell)

Primeiro faça login para obter o token (JWT) e monte o cabeçalho `Bearer`:

    $login = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/sessao `
      -ContentType "application/json; charset=utf-8" `
      -Body '{"email":"seu@email.com","senha":"sua-senha"}'
    $h = @{ Authorization = "Bearer $($login.token)" }

Depois use o `$h` nas demais chamadas:

    Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/competencias -Headers $h `
      -ContentType "application/json; charset=utf-8" `
      -Body '{"nome":"Comunicação","tipo":"COMPORTAMENTAL","acaoPadrao":"Treinamento de Comunicação"}'

    Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/colaboradores -Headers $h `
      -ContentType "application/json; charset=utf-8" `
      -Body '{"matricula":"1001","nome":"Colaborador Teste","cargo":"OPERADOR","setor":"Injeção"}'

    Invoke-RestMethod -Uri http://localhost:8080/api/colaboradores -Headers $h
    Invoke-RestMethod -Uri http://localhost:8080/api/mapa -Headers $h
    Invoke-RestMethod -Uri http://localhost:8080/api/dashboard -Headers $h

Use apenas dados fictícios até a empresa aprovar o uso de dados reais.

## Endpoints
| Recurso | Operações |
|---|---|
| `/api/sessao` | POST (login com e-mail e senha → token JWT). Único endpoint público. |
| `/api/sessao/senha` | POST (troca a senha do supervisor logado: `senhaAtual` + `novaSenha`) |
| `/api/colaboradores` | GET (`?incluirInativos=true`), GET `/{id}`, POST, PUT `/{id}`, POST `/{id}/desativar`, POST `/{id}/reativar` |
| `/api/competencias`  | idem (`?incluirInativas=true`) |
| `/api/colaboradores/{id}/avaliacoes` | GET (histórico, mais recente primeiro), POST (nova avaliação) |
| `/api/avaliacoes/{id}` | GET, PUT (corrige a avaliação) |
| `/api/colaboradores/{id}/pdi` | GET (ações do colaborador), POST (nova ação) |
| `/api/pdi/{id}` | GET, PUT (edita a ação: descrição, prazo, status, conclusão, evidência) |
| `/api/mapa` | GET (colaboradores × competências com a nota atual e a classificação) |
| `/api/dashboard` | GET (indicadores: médias, distribuição por faixa, competências mais críticas) |

Exceto `/api/sessao`, toda chamada exige `Authorization: Bearer <token>`.

Não há exclusão: colaboradores e competências são desativados, para preservar o histórico.
Avaliações também não são excluídas; correções são feitas por `PUT`.

### Regras das avaliações
- Uma nota (1–5) por competência; a competência precisa ser do supervisor logado.
- Uma avaliação por colaborador por data (`data_avaliacao`).
- Avaliação é parcial: não é obrigatório pontuar todas as competências.
- Não se cria avaliação para colaborador inativo.

Exemplo (PowerShell, continuando do bloco anterior; `1` é o id do colaborador):

    Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/colaboradores/1/avaliacoes -Headers $h `
      -ContentType "application/json; charset=utf-8" `
      -Body '{"dataAvaliacao":"2026-01-15","observacao":"Primeira avaliação","notas":[{"competenciaId":1,"nota":4}]}'

    Invoke-RestMethod -Uri http://localhost:8080/api/colaboradores/1/avaliacoes -Headers $h

## Front-end (`frontend/`)

Interface web em Vite + React + TypeScript (login, colaboradores, competências, avaliações,
mapa de competências, dashboard e troca de senha). As cores ficam todas em
`frontend/src/theme.css` — paleta extraída da logo oficial do cliente
(vermelho `#cf242a` + preto + branco).

Requisitos: Node 18+ (testado no 24). Com o backend no ar em `http://localhost:8080`:

    cd frontend
    cp .env.example .env      # ajuste VITE_API_URL se o backend não estiver em :8080
    npm install
    npm run dev               # abre em http://localhost:5173

Para gerar o pacote de produção: `npm run build` (saída em `frontend/dist/`).
O CORS do backend libera a origem `http://localhost:5173`.

O token JWT é guardado em `sessionStorage` (sai ao fechar a aba). É uma escolha de
desenvolvimento: um token nesse local fica exposto a XSS. Para produção, migrar para
cookie `HttpOnly` + proteção CSRF.
