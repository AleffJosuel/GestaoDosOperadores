# Segurança e privacidade — Gestão de Competências e PDI

Documento para a conversa com RH e TI. Descreve **o que o sistema faz hoje**, com o arquivo e o
teste que sustentam cada afirmação, e **o que ainda não faz**. A última seção é o que precisa ser
decidido ou providenciado antes de rodar com dados reais da empresa.

Nada aqui é certificação nem laudo de conformidade: é a descrição honesta de uma aplicação feita
por uma pessoa, com os controles que couberam no tamanho do projeto.

- Última revisão: **4 de novembro de 2026**
- Versão do código: branch `main`
- Testes automatizados: **122 no backend** (`mvn test`) e **18 no front** (`npm test`)

---

## 1. O que o sistema é

API REST em Java 21 / Spring Boot 3 com banco PostgreSQL, e um front em React servido como site
estático. Substitui a planilha em que o supervisor controlava competências, avaliações e PDI dos
operadores.

Quem usa: **supervisores de produção**, com conta criada manualmente. Não existe cadastro público —
nenhuma tela do sistema cria conta.

---

## 2. Dados pessoais tratados

| Dado | De quem | Por quê | Sensível? |
|---|---|---|---|
| Nome, matrícula, cargo, setor | Operador | Identificar quem está sendo avaliado | Não |
| Data de admissão, escolaridade | Operador | Contexto da avaliação e do PDI | Não |
| Notas por competência, média, classificação | Operador | Finalidade do sistema | **Sim — avaliação de desempenho** |
| Ações de PDI, prazos, evidências | Operador | Plano de desenvolvimento | **Sim** |
| Registros de acompanhamento (feedback, ocorrência) | Operador | Histórico da gestão | **Sim** |
| E-mail e hash de senha | Supervisor | Login | Credencial |
| IP e horário das operações | Supervisor | Trilha de auditoria | Sim (dado pessoal) |

**Não há** CPF, RG, endereço, telefone, dado bancário, dado de saúde, biometria ou foto.

Avaliação de desempenho é dado pessoal e seu uso indevido afeta a vida profissional da pessoa. Pela
LGPD, isso exige finalidade definida, acesso restrito e prazo de guarda — os três pontos tratados nas
seções 4 e 7.

---

## 3. Controles implementados

Mapeados contra o OWASP Top 10. A coluna "onde" aponta o arquivo; a coluna "prova" aponta o teste
que falha se alguém desfizer o controle.

### Autenticação e sessão

| Controle | Onde | Prova |
|---|---|---|
| Senha guardada só como hash BCrypt — nunca em texto | `seguranca/SecurityConfig.java` | — |
| Token de sessão JWT assinado (HMAC-SHA256), validade de 8 h | `web/SessaoController.java` | `SecurityConfigTest` |
| Segredo do token com 32+ caracteres; **a aplicação não sobe sem ele** | `SecurityConfig.java` | `segredoCurtoContinuaDerrubandoAAplicacao` |
| Token de outro emissor, expirado ou assinado com outra chave é recusado | `SecurityConfig.jwtDecoder` | 4 testes em `SecurityConfigTest` |
| Erro de login não revela se o e-mail existe (401 genérico) | `SessaoController`, `ErrosHandler` | — |
| Política de senha forte: 12+ caracteres com variedade, ou frase de 20+; recusa senha óbvia, sequência, repetição e senha que contenha o e-mail | `seguranca/PoliticaSenha.java` | 20 testes em `PoliticaSenhaTest` |
| Troca de senha exige a senha atual | `web/ContaService.java` | `ContaServiceTest` |
| Front encerra a sessão sozinho quando o token vence, sem esperar erro | `frontend/src/auth/AuthContext.tsx` | — |

### Autorização — um supervisor nunca vê a equipe do outro

É a regra mais importante do sistema e a mais fácil de quebrar sem perceber. Todo acesso a dado de
colaborador passa por `ColaboradorService.buscar`, que consulta **por id + supervisor logado**.
Registro de outro supervisor responde **404, não 403** — 403 confirmaria que aquele registro existe.

| Controle | Onde | Prova |
|---|---|---|
| Isolamento por supervisor em colaborador, competência, avaliação, PDI e acompanhamento | `web/*Service.java` | **22 testes** em `IsolamentoPorSupervisorTest` |
| Autorização sempre no servidor — esconder botão no front não é controle | `SecurityConfig` | — |
| Exclusão definitiva também isolada (apagar a equipe de outro é o pior caso possível) | `ColaboradorService.excluirDefinitivamente` | `colaborador_excluirDefinitivamente_deOutroSupervisor_retorna404ENaoApagaNada` |

### Abuso e força bruta

| Controle | Onde | Prova |
|---|---|---|
| Login: 10 tentativas/min por IP e 20/15 min por conta | `seguranca/LimitadorTentativas.java` | 5 testes em `LimitadorTentativasTest` |
| Troca de senha: 5 tentativas/15 min (defesa para o caso de token roubado) | `ContaService` | `ContaServiceTest` |
| IP real identificado atrás de proxy, sem confiar em cabeçalho forjável em acesso direto | `seguranca/IpDaRequisicao.java` | 6 testes em `IpDaRequisicaoTest` |
| O próprio limitador tem teto de memória, para não virar o alvo do ataque | `LimitadorTentativas` | `naoCresceSemLimiteESeguePunindoAChaveAtivaNoMeioDaRajada` |
| Upload limitado a 2 MB e 2000 linhas; formulário e cabeçalhos com teto de tamanho | `application.properties`, `ImportacaoService` | — |

O bloqueio por conta é por **janela curta**, não travamento permanente: com travamento, qualquer um
derrubaria o acesso do supervisor só chutando senhas no e-mail dele.

### Entrada e saída de dados

| Controle | Onde | Prova |
|---|---|---|
| Consultas sempre parametrizadas (JPA/JdbcTemplate) — sem SQL montado com texto | `dominio/*Repository.java` | — |
| Validação declarativa de todo corpo de requisição (tipo, tamanho, faixa) | `web/Dtos.java` | — |
| Importação aceita só CSV, recusando `.xlsx`/`.zip`/outros | `web/ImportacaoService.java` | `ImportacaoSegurancaTest` |
| Célula de CSV iniciada por `= + - @` é recusada (fórmula que o Excel executaria numa exportação futura) | `ImportacaoService.recusarFormula` | 14 testes em `ImportacaoSegurancaTest` |
| XSS: o React escapa todo texto; não há `innerHTML` em nenhum ponto do front | `frontend/src` | — |

### Erros, cabeçalhos e transporte

| Controle | Onde | Prova |
|---|---|---|
| Resposta de erro nunca traz mensagem de exceção, pilha, SQL ou nome de constraint | `web/ErrosHandler.java` | 8 testes em `ErrosHandlerTest` |
| Falha inesperada devolve código de ocorrência; o detalhe fica só no log | `ErrosHandler.inesperado` | `falhaInesperadaNaoVazaAExcecaoEDevolveCodigoDeOcorrencia` |
| CORS restrito às origens configuradas; `*` é **recusado** no boot | `SecurityConfig` | 6 testes em `SecurityConfigTest` |
| Cabeçalhos da API: CSP trancada, `nosniff`, frame `DENY`, `no-referrer`, HSTS, Permissions-Policy | `SecurityConfig` | — |
| Cabeçalhos do site: CSP, HSTS, `nosniff`, frame `DENY`, Permissions-Policy | `frontend/vercel.json` | — |
| Build do front falha se a CSP não apontar para o endereço real da API | `frontend/scripts/checar-vercel.mjs` | — |
| Sem cookie de sessão: a credencial vai no cabeçalho, logo não há superfície para CSRF | `SecurityConfig` | — |

### Segredos

Nenhuma senha, chave ou segredo no código ou no histórico do Git. Tudo vem de variável de ambiente
(`JWT_SECRET`, `DB_PASSWORD`, `ADMIN_PASSWORD`); `.env` está no `.gitignore` e nunca foi versionado.
A aplicação **não sobe** sem `JWT_SECRET` válido, para não rodar com um segredo padrão esquecido.

O `.gitignore` também bloqueia `*.xlsx`, `*.xls` e `*.csv`: a planilha real da empresa não sobe por
acidente. Os CSVs de exemplo no repositório têm dados fictícios.

### Dependências

- Front, dependências de produção: **0 vulnerabilidades** (`npm run seguranca`).
- Front, dependências de desenvolvimento: 12 alertas moderados em `eslint`, `vite` e `vitest`.
  Não vão para o site publicado — rodam só na máquina de quem desenvolve. Resolver exige subir
  versão principal dessas ferramentas; está na lista de pendências.
- Backend: Spring Boot 3.3.5, com as versões administradas pelo projeto pai.

---

## 4. Trilha de auditoria

É registrado na tabela `auditoria` — com data e hora, e-mail do autor, método, caminho, status da
resposta, IP e duração:

1. **Toda operação que altera dados**, incluindo login (recusado ou aceito).
2. **Toda consulta à ficha de um colaborador**: `/api/colaboradores/{id}` e as sub-listas de
   avaliações, PDI e acompanhamentos.
3. **Toda exportação em CSV** do mapa e do dashboard — o momento em que os dados saem do sistema.

- Onde: `seguranca/FiltroAuditoria.java`, `seguranca/Auditoria.java`, migração `V2__trilha_auditoria.sql`
- Prova: 11 testes em `FiltroAuditoriaTest`, 3 em `AuditoriaTest` e 3 de ponta a ponta em
  `SubidaDaAplicacaoTest`

Três decisões que valem explicar:

1. **O corpo da requisição nunca é gravado.** Guardá-lo colocaria a senha (no login) e a avaliação
   de desempenho dentro da tabela de auditoria. Grava-se o metadado, não o conteúdo.
2. **A tentativa que falhou também é registrada** — inclusive login recusado. É o que permite ver um
   ataque: o mesmo IP com vários 401 seguidos.
3. **O autor é identificado por e-mail, sem chave estrangeira.** A trilha continua legível depois que
   a conta for removida; auditoria que desaparece junto com o autor não serve de auditoria.

Consultas úteis:

```sql
-- quem alterou algo nas últimas 24 horas
SELECT ocorrido_em, autor_email, metodo, caminho, status, ip
FROM auditoria WHERE ocorrido_em > now() - interval '24 hours'
ORDER BY ocorrido_em DESC;

-- tentativas de login recusadas por IP (indício de ataque)
SELECT ip, count(*) FROM auditoria
WHERE caminho = '/api/sessao' AND status = 401 AND ocorrido_em > now() - interval '7 days'
GROUP BY ip ORDER BY 2 DESC;

-- quem consultou a ficha do colaborador 42 (pedido típico numa apuração de LGPD)
SELECT ocorrido_em, autor_email, caminho, ip FROM auditoria
WHERE caminho LIKE '/api/colaboradores/42%' AND metodo = 'GET'
ORDER BY ocorrido_em DESC;

-- quem exportou dados, e quando
SELECT ocorrido_em, autor_email, caminho, ip FROM auditoria
WHERE caminho LIKE '/api/%/exportar' ORDER BY ocorrido_em DESC;
```

**Leitura de conjunto continua fora da trilha**: a lista de colaboradores, o mapa e o dashboard são
abertos a cada navegação na interface, e registrá-los afogaria a tabela sem dizer nada sobre
interesse num indivíduo. A linha foi traçada no que identifica **uma pessoa** — é o que uma
apuração investiga.

---

## 5. Quem acessa o quê

Existe um único perfil: **supervisor**. Cada um vê apenas a própria equipe; não há perfil de RH, de
gerente ou de auditor que veja tudo.

Isso tem uma consequência prática que vale decidir na reunião: se o RH quiser consultar o sistema,
hoje só há dois caminhos — receber a conta de um supervisor (o que dá acesso de escrita e confunde a
auditoria, já que as ações ficariam no nome do supervisor) ou alguém consultar o banco direto. Um
perfil somente-leitura é a solução correta e **não está implementado**.

Há um paliativo que cobre o caso mais comum: o supervisor **exporta** o mapa e os indicadores em
CSV (botão *Exportar CSV* nas telas de mapa e dashboard) e entrega o arquivo ao RH. Resolve "o RH
quer ver os números" sem criar conta, e a exportação fica registrada na auditoria (seção 4). Não
resolve "o RH quer consultar quando quiser, sozinho" — para isso o perfil somente-leitura continua
sendo o caminho certo.

---

## 6. Como as contas são criadas

Não há cadastro público nem recuperação de senha por e-mail. Para criar uma conta, define-se
`ADMIN_EMAIL` e `ADMIN_PASSWORD` e sobe-se a aplicação uma vez; a conta só é criada se aquele e-mail
ainda não existir, e a senha passa pela mesma política da troca de senha. Depois disso, o próprio
supervisor troca a senha pela tela.

Esquecer a senha **exige a TI**, mas não mais mexer no banco: com `RESET_SENHA=true` no arquivo de
configuração, a subida redefine a senha de uma conta existente para o valor de `ADMIN_PASSWORD`. A
senha temporária passa pela política de senha normal, o reset fica na trilha de auditoria, e o
sinalizador tem de ser removido depois — enquanto ficar ligado, cada reinício desfaz a troca de
senha que o supervisor fizer.

- Onde: `seguranca/SupervisorInicial.java`
- Prova: 10 testes em `SupervisorInicialTest`, incluindo o caso de `RESET_SENHA=true` com senha em
  branco (não apaga a senha de ninguém) e com senha fraca (não altera nada)

Continua **não sendo autoatendimento**: depende de quem tem acesso ao servidor, e tokens já
emitidos seguem válidos até expirarem. Com dois ou três supervisores é tranquilo; com vinte, um
fluxo de "esqueci minha senha" por e-mail passa a valer o esforço.

---

## 7. LGPD — pontos que o RH costuma perguntar

**Finalidade.** Acompanhar competências, avaliações e PDI dos operadores. O sistema não guarda nada
que não sirva a isso — sem CPF, endereço, telefone ou dado bancário.

**Quem decide o uso.** A empresa é a controladora dos dados. Este sistema é a ferramenta.

**Direito de eliminação (art. 18, VI).** `DELETE /api/colaboradores/{id}` apaga o colaborador e todo
o histórico dele — avaliações, notas, PDI e acompanhamentos — sem volta. Na tela, o botão **Apagar**
aparece só para colaborador já desativado, para que apagar seja sempre um segundo passo deliberado.
O caminho normal de quem saiu da empresa continua sendo **Desativar**, que preserva o histórico.

**Acesso e portabilidade.** Dá para gerar o que um titular pediu a partir das telas e do banco, mas
**não existe um relatório pronto** de "tudo o que o sistema sabe sobre esta pessoa". Pendência.

**Dados de quem saiu.** Colaborador desativado não aparece em dashboard, mapa nem indicadores, e não
aceita avaliação nem PDI novo. O histórico fica guardado — decisão consciente, porque desativar não é
apagar. **Não existe prazo automático de descarte**: hoje a exclusão é sempre manual. Definir esse
prazo (por exemplo, apagar o histórico de quem saiu há mais de X anos) é uma decisão do RH, não
técnica, e sem ela o sistema guarda para sempre.

**Retenção da auditoria.** A tabela `auditoria` contém IP, que é dado pessoal. Sugestão: guardar
12 meses. Não há limpeza automática; por enquanto é manual:

```sql
DELETE FROM auditoria WHERE ocorrido_em < now() - interval '12 months';
```

**Dados saindo da empresa.** Se o sistema for publicado (front na Vercel, banco e API em outro
provedor), avaliação de desempenho de funcionário passa a ficar em servidor de terceiro, muito
provavelmente fora do Brasil. Isso precisa de ciência e aprovação do RH e da TI **antes** de subir
dados reais. Rodando na rede interna, essa questão não existe.

---

## 8. O que NÃO está implementado

Lista honesta, para ninguém sair da reunião com a impressão errada.

| Falta | Impacto | Dificuldade |
|---|---|---|
| Autenticação em dois fatores (MFA) | Senha vazada = acesso completo | Média |
| Perfil somente-leitura (para RH/auditoria) | Hoje RH não tem como consultar sem conta de supervisor | Média |
| Recuperação de senha pelo próprio usuário | Esquecer a senha exige a TI redefinir pelo arquivo de configuração (`RESET_SENHA`); não é autoatendimento, e exige acesso ao servidor | Média |
| Revogação de token antes do vencimento | Token roubado vale até 8 h; trocar ou redefinir a senha não o invalida | Média |
| Descarte automático por prazo de retenção | Dado fica guardado até alguém apagar à mão | Baixa |
| Relatório "todos os dados deste titular" | Atender pedido de acesso é trabalho manual | Baixa |
| Backup automático e teste de restauração | **Depende de quem hospedar o banco** | Depende |
| Criptografia em repouso | Idem — é configuração do provedor ou do servidor | Depende |
| Monitoramento com alerta (SIEM) | Ataque aparece na auditoria, mas ninguém é avisado | Alta |
| Teste de invasão por terceiro | Nenhuma revisão externa foi feita | Externa |
| Varredura automática de dependências no CI | Hoje é comando manual (`npm run seguranca`) | Baixa |
| Teto de tamanho para corpo JSON | Upload e formulário têm limite; JSON não. Exige sessão válida para explorar | Baixa |
| Alertas de dev do front (12 moderados) | Não afetam o site publicado | Baixa |

Também não houve revisão jurídica: as afirmações de LGPD aqui são de engenharia, não de advogado.

---

## 9. Antes de rodar com dados reais

Checklist. Os três primeiros itens são obrigatórios.

- [ ] **`JWT_SECRET` novo**, gerado para produção (`openssl rand -base64 48`). Nunca o de desenvolvimento.
- [ ] **`DB_PASSWORD` forte**, diferente de qualquer senha de desenvolvimento.
- [ ] **`ADMIN_PASSWORD` trocado** pelo supervisor no primeiro acesso.
- [ ] `CORS_ORIGENS` apontando só para o domínio real do front.
- [ ] `ATRAS_DE_PROXY=true` se houver proxy terminando o TLS (Render, Railway, nginx) — sem isso o
      limite de login conta todo mundo junto e o HSTS não é emitido. Em acesso direto, **false**.
- [ ] `https` ativo de ponta a ponta; sem ele, HSTS e token no cabeçalho perdem sentido.
- [ ] No `frontend/vercel.json`, trocar `https://SUA-API.exemplo.com` pelo endereço real da API
      (o `npm run build` barra se esquecer).
- [ ] Usuário do banco com privilégio mínimo — só o schema da aplicação, sem `SUPERUSER`.
- [ ] Backup definido e **restauração testada** uma vez.
- [ ] Prazo de retenção acordado com o RH (seção 7).
- [ ] Ciência e aprovação do RH/TI sobre onde os dados vão ficar hospedados.

---

## 10. Como verificar o que está escrito aqui

Nada neste documento depende de confiar na palavra de quem escreveu:

```bash
mvn test
```

```bash
npm --prefix frontend test
```

```bash
npm --prefix frontend run seguranca
```

Cada controle da seção 3 aponta o arquivo e o teste correspondente. Para ver se um controle é real,
o caminho mais rápido é apagá-lo e rodar os testes: o que estiver protegido quebra na hora.
