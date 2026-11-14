# Gestão de Competências e PDI — API

Back-end (Spring Boot 3 + PostgreSQL) do sistema do supervisor de turno.
**Entrega 1:** autenticação, colaboradores e banco de competências.
**Entrega 2:** avaliações com nota (1–5) por competência e histórico por colaborador.
**Entrega 3:** login com token JWT (substitui o HTTP Basic), mapa de competências e dashboard.
**Entrega 4:** PDI — plano de desenvolvimento individual (ações ligadas a avaliações/competências);
indicadores de PDI no dashboard; ficha do colaborador (impressão/PDF); rate limiting no login e cabeçalhos
de segurança (CSP); e melhorias de UX (toasts, confirmações, busca).
**Entrega 5:** importação de colaboradores e competências por CSV (relatório por linha).
**Entrega 6 (atual):** registros de acompanhamento por colaborador (feedback/conversa/ocorrência, na ficha)
e tela de Configurações (faixas de classificação + rótulos das notas 1–5, editáveis por supervisor).
Há um front-end em `frontend/` (Vite + React + TypeScript).

Organização do repositório: `backend/` (código Java, com o `pom.xml` na raiz) e `frontend/`
(projeto npm). O material de implantação fica numa pasta `implantacao/` **fora do
versionamento** — ver [Publicando](#publicando).

## Status

Projeto em desenvolvimento, entregue por incrementos (as "Entregas" acima). As próximas
frentes são testes automatizados cobrindo mais serviços e autenticação por cookie `HttpOnly`.

Todos os dados do repositório são **fictícios** (ver `exemplos-csv/`). Credenciais e segredos
ficam em variáveis de ambiente, fora do versionamento.

## Stack

Java 21 · Spring Boot 3.3 · Spring Security (JWT) · PostgreSQL 16 · Flyway · Maven ·
React 18 · TypeScript 5 · Vite 5 · React Router

## Telas

> Capturas com **dados fictícios** — nenhum dado real de colaborador aparece aqui.

**Dashboard** — indicadores da equipe, distribuição por faixa (clicar numa fatia filtra o mapa)
e competências mais críticas.

![Dashboard](docs/screenshots/dashboard.png)

**Mapa de competências** — nota mais recente de cada colaborador em cada competência, com a
média e a classificação da linha.

![Mapa de competências](docs/screenshots/mapa.png)

**PDI** — plano de desenvolvimento de toda a equipe, com resumo clicável, filtro por status
e destaque das ações atrasadas.

![PDI](docs/screenshots/pdi.png)

**Ficha do colaborador** — dados, histórico de avaliações, ações de PDI e registros de
acompanhamento, em layout preparado para impressão/PDF.

![Ficha do colaborador](docs/screenshots/ficha.png)

<details>
<summary>Outras telas (colaboradores, competências, configurações e login)</summary>

**Colaboradores** — cadastro, importação por CSV e desativação (sem exclusão, para preservar o histórico).

![Colaboradores](docs/screenshots/colaboradores.png)

**Competências** — catálogo com tipo (comportamental/técnica) e nível esperado.

![Competências](docs/screenshots/competencias.png)

**Configurações** — faixas de classificação e rótulos das notas, editáveis por supervisor.

![Configurações](docs/screenshots/configuracoes.png)

**Login** — único endpoint público; não há cadastro aberto.

![Login](docs/screenshots/login.png)

</details>

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

## Executando (terminal)

```powershell
.\rodar-backend.ps1
```

O script existe porque três coisas atrapalham rodar pelo terminal: o Spring **não lê o `.env`**
(quem faz isso é o `envFile` do `launch.json`, que só vale no *Run and Debug*), o Maven não está
no PATH, e o Maven exige `JAVA_HOME` mesmo com o `java` no PATH. Ele resolve os três, confere se
a porta está livre e sobe em `http://localhost:8080` — que serve **o site e a API juntos**, já
que o front é embutido no JAR.

- `.\rodar-backend.ps1` compila o código atual (equivale a `mvn spring-boot:run`).
- `.\rodar-backend.ps1 -Jar` sobe o último JAR construído: ~6 s em vez de ~40 s, mas não enxerga
  alteração de código feita depois do `package`. Serve para conferir a entrega.
- `$env:SERVER_PORT=8081` antes do comando muda a porta.

Para **depurar com ponto de parada**, continue usando o *Run and Debug* do VSCode — o terminal
não dá depurador.

Os testes pelo terminal: `.\rodar-backend.ps1` não os roda. Use o Test Explorer, ou
`mvn test` com o caminho completo do Maven (ver `rodar-backend.ps1`). O
`SubidaDaAplicacaoTest` exige o banco de teste — a criação está documentada em
`backend/src/test/resources/application-teste.properties`.

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
| `/api/colaboradores` | GET (`?incluirInativos=true`), GET `/{id}`, POST, PUT `/{id}`, POST `/{id}/desativar`, POST `/{id}/reativar`, DELETE `/{id}` (exclusão definitiva — LGPD) |
| `/api/competencias`  | idem (`?incluirInativas=true`) |
| `/api/colaboradores/{id}/avaliacoes` | GET (histórico, mais recente primeiro), POST (nova avaliação) |
| `/api/avaliacoes/{id}` | GET, PUT (corrige a avaliação) |
| `/api/colaboradores/{id}/pdi` | GET (ações do colaborador), POST (nova ação) |
| `/api/pdi/{id}` | GET, PUT (edita a ação: descrição, prazo, status, conclusão, evidência) |
| `/api/colaboradores/{id}/acompanhamentos` | GET (registros, mais recente primeiro), POST (novo registro; apenas-acréscimo) |
| `/api/colaboradores/importar` · `/api/competencias/importar` | POST multipart (campo `arquivo`, CSV → relatório por linha) |
| `/api/mapa` | GET (colaboradores × competências com a nota atual e a classificação) |
| `/api/dashboard` | GET (indicadores: médias, distribuição por faixa, competências mais críticas) |
| `/api/mapa/exportar` · `/api/dashboard/exportar` | GET (CSV para levar ao RH: separador `;`, decimal com vírgula e BOM, como o Excel pt-BR espera) |
| `/api/configuracao` | GET, PUT (faixas de classificação e rótulos das notas 1–5 do supervisor) |
| `/actuator/health` | GET (saúde para o monitoramento: `UP`/`DOWN`, sem detalhe algum) |

Exceto `POST /api/sessao` e `/actuator/health`, toda chamada exige
`Authorization: Bearer <token>`. Nenhum outro endereço de `/actuator` está exposto, e se algum
vier a ser, continua exigindo token (ver `SecurityConfig`).

A exportação e a consulta à ficha de um colaborador ficam na trilha de auditoria — a primeira
porque é quando os dados saem do sistema, a segunda porque é a pergunta que uma apuração de LGPD
faz. Leitura de conjunto (lista, mapa, dashboard) não é registrada.

O caminho normal é **desativar**, não excluir: colaboradores e competências são desativados para
preservar o histórico, e avaliações são corrigidas por `PUT`.

A única exceção é `DELETE /api/colaboradores/{id}`, que existe para atender pedido de eliminação de
dados do titular (LGPD, art. 18, VI): apaga o colaborador e todo o histórico dele — avaliações,
notas, PDI e acompanhamentos — sem volta. Na tela, o botão **Apagar** só aparece para colaborador já
desativado, para que apagar seja sempre um segundo passo deliberado.

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
mapa de competências, dashboard, PDI, ficha com impressão/PDF e registros de acompanhamento,
importação CSV, configurações e troca de senha). As cores ficam todas em
`frontend/src/theme.css` — paleta extraída da logo oficial do cliente
(vermelho `#cf242a` + preto + branco).

Requisitos: Node 20+ (testado no 24). Com o backend no ar em `http://localhost:8080`:

    cd frontend
    cp .env.example .env      # ajuste VITE_API_URL se o backend não estiver em :8080
    npm install
    npm run dev               # abre em http://localhost:5173

Para gerar o pacote de produção: `npm run build` (saída em `frontend/dist/`). O build usa
`frontend/.env.production`, que deixa `VITE_API_URL` **vazia** de propósito: aí o front chama
a API por caminho relativo, na mesma origem — é o que permite o JAR servir site e API juntos
(ver [Publicando](#publicando)) sem que a porta ou o nome do servidor entrem no bundle.

Para servir o build localmente: `npm run preview` (porta 5173, já com os cabeçalhos de
segurança abaixo). Como o build chama `/api` relativo, o preview repassa essas chamadas para
`http://localhost:8080` por proxy — assim o build de produção é conferível sem empacotar.

Testes do front (Vitest): `npm test` (ou `npm run test:watch`). Rodam com
`TZ=America/Sao_Paulo` fixado no `vite.config.ts`, porque as regras de data são em
horário local — ver `src/datas.ts`.

Lint: `npm run lint` (ESLint com regras de React Hooks e de acessibilidade `jsx-a11y`).

### Cabeçalhos de segurança em produção

O `npm run preview` já envia estes cabeçalhos (definidos em `frontend/vite.config.ts`).
Quem hospedar o `dist/` em outro servidor (nginx, IIS, CDN…) deve configurar os mesmos,
trocando `https://api.exemplo.com` pela origem real da API (`VITE_API_URL`):

    Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self';
      font-src 'self'; connect-src 'self' https://api.exemplo.com; object-src 'none';
      base-uri 'self'; form-action 'self'; frame-ancestors 'none'
    X-Content-Type-Options: nosniff
    X-Frame-Options: DENY
    Referrer-Policy: no-referrer
    Permissions-Policy: camera=(), microphone=(), geolocation=()

O build não tem script nem estilo inline, por isso a CSP não precisa de `'unsafe-inline'`.
Esses cabeçalhos não valem no `npm run dev`: o recarregamento do Vite precisa de script
inline e WebSocket.

O token JWT é guardado em `sessionStorage` (sai ao fechar a aba). É uma escolha de
desenvolvimento: um token nesse local fica exposto a XSS. Para produção, migrar para
cookie `HttpOnly` + proteção CSRF.

## Publicando

Há dois caminhos, e o primeiro é o que está em uso.

### No servidor da empresa (JAR único) — caminho atual

O `mvn package` embute o build do front dentro do JAR (em `classpath:/static`), e a
aplicação serve a interface e a API **na mesma porta e na mesma origem**. O servidor não
precisa de Node, nginx nem IIS, e o CORS deixa de existir. Quem faz o roteamento das rotas
do React Router é o `SiteConfig`, para que recarregar a página em `/mapa` não dê 404.

Para gerar o pacote de entrega:

```powershell
.\empacotar.ps1
```

O script roda os testes, faz o build do front, empacota o JAR, confere que o site entrou
mesmo nele e gera `dist-entrega/gestao-operadores-<versão>.zip` com o JAR, o modelo de
configuração, os scripts de inicialização (Windows e Linux), os scripts de banco e o guia
de instalação. **A ordem importa**: rodar o Maven sozinho depois de mexer no front gera um
JAR com a interface antiga — o script existe para garantir a sequência.

Os arquivos que vão no ZIP ficam em `implantacao/`, que **não está neste repositório**:
modelo de configuração, scripts de inicialização para Windows e Linux, scripts de banco,
exemplos de proxy reverso com HTTPS e o `LEIA-ME.md` que a TI recebe (requisitos, criação do
banco, configuração, firewall, backup, atualização e tabela de problemas comuns).

Ficou fora do versionamento por decisão: este repositório é público, e o guia descreve a
infraestrutura interna — portas, procedimento de firewall, como as contas são criadas e o que
o sistema ainda não protege. Nada ali é segredo isoladamente, mas junto vira um mapa.

**Consequência para quem clonar:** sem essa pasta o `empacotar.ps1` para e avisa, porque o ZIP
sairia sem configuração nem instruções. Peça os arquivos a quem mantém o projeto. Quem os tem
precisa guardá-los fora do git — eles não têm backup pelo repositório.

Na instalação local, `CORS_ORIGENS` fica **vazio** — não existe outra origem a liberar, e
vazio é mais restritivo que qualquer valor. `JWT_SECRET` precisa ser novo: nunca o de
desenvolvimento.

### Em nuvem (front e API separados)

O front é estático e vai bem na Vercel; o back-end é um JAR de processo longo com
PostgreSQL, então precisa de um serviço que rode container (Railway, Render, Fly.io) e de
um Postgres gerenciado (Neon, Supabase ou o add-on do próprio serviço). A Vercel não roda
Java, e o Firebase não substitui o Postgres: o schema depende de `UNIQUE`, `CHECK`,
`ON CONFLICT`, índice parcial e migrations do Flyway.

Neste caminho, `VITE_API_URL` e `CORS_ORIGENS` precisam ser preenchidos — é para ele que
valem as instruções abaixo.

**Back-end.** Variáveis de ambiente obrigatórias no serviço escolhido:

| Variável | Observação |
|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | do Postgres gerenciado |
| `JWT_SECRET` | **novo**, 32+ caracteres; nunca o de desenvolvimento |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | conta inicial; a senha passa pela política de senha (ver `PoliticaSenha`) |
| `CORS_ORIGENS` | domínio do front, ex.: `https://seu-app.vercel.app` |
| `ATRAS_DE_PROXY` | `true` em Render/Railway/nginx; `false` em acesso direto |

`ATRAS_DE_PROXY=true` é o que faz a aplicação enxergar o IP real do cliente e saber que a requisição
chegou por https. Sem isso, atrás de um proxy: o limite de login passa a contar **todas** as
tentativas numa única chave (o IP do proxy) e trava o login de todos os supervisores no primeiro
ataque, e o HSTS deixa de ser enviado. Em acesso direto tem de ficar `false`, porque aí o cabeçalho
`X-Forwarded-For` vem do cliente e qualquer um poderia forjar o próprio IP.

`CORS_ORIGENS` aceita vários domínios separados por vírgula e recusa `*` — com a credencial
indo no cabeçalho, curinga deixaria qualquer site chamar a API em nome de quem está logado.
Vazio significa "nenhuma origem externa liberada", o certo quando o front vem do mesmo JAR.
O Flyway cria o schema na primeira subida.

**Front-end (Vercel).** Em *Settings → General*, defina **Root Directory** como `frontend`;
o `vercel.json` ali já traz o fallback de SPA (sem ele, recarregar `/mapa` dá 404), os
cabeçalhos de segurança e o cache dos assets com hash. Em *Settings → Environment Variables*,
defina `VITE_API_URL` com a URL pública do back-end.

Falta um passo manual: `vercel.json` é lido pela Vercel **antes** do build, então não aceita
variável de ambiente. Troque `https://SUA-API.exemplo.com` pelo endereço real da API, na
diretiva `connect-src` da CSP. O `npm run build` confere isso (`scripts/checar-vercel.mjs`) e
falha avisando se o endereço de exemplo continuar lá ou não bater com `VITE_API_URL` — a
checagem é pulada quando a API é local.

Antes de publicar com dados reais, confirme com a empresa se as avaliações podem sair para
um provedor externo: são dados pessoais de desempenho, sujeitos à LGPD.

## Segurança e privacidade

[**SEGURANCA.md**](SEGURANCA.md) é o documento para a conversa com RH e TI: quais dados pessoais o
sistema trata, os controles implementados (com o arquivo e o teste que provam cada um), a trilha de
auditoria, as respostas de LGPD, **o que ainda não existe** e o checklist antes de rodar com dados
reais.

Resumo do que o código garante hoje: senha só em hash BCrypt com política de senha forte; token JWT
assinado, validado e de curta duração; isolamento por supervisor coberto por 22 testes; limite de
tentativas no login e na troca de senha; trilha de auditoria de toda alteração, da consulta à ficha
de um colaborador e da exportação (sem gravar o corpo da requisição); respostas de erro que não
vazam detalhe técnico; CORS restrito; cabeçalhos de segurança na API e no site; e nenhum segredo no
repositório.
