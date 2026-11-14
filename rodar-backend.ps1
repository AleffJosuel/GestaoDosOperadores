<#
.SYNOPSIS
    Sobe o backend pelo terminal, lendo o .env.

.DESCRIPTION
    Existe porque três coisas atrapalham rodar o backend direto pelo terminal nesta máquina:

      1. o Spring NÃO lê o .env — quem faz isso é o "envFile" do launch.json, que só vale no
         Run and Debug do VSCode. Pelo terminal, sem carregar as variáveis antes, a aplicação
         nem sobe: para na falta de JWT_SECRET;
      2. o Maven não está no PATH (só existe o binário que a extensão Java do VSCode baixou);
      3. o Maven exige JAVA_HOME mesmo com o java no PATH.

    O script resolve os três e sobe a aplicação em http://localhost:8080 — que, desde que o
    front foi embutido no JAR, serve também o site na mesma porta.

.PARAMETER Jar
    Roda o JAR já compilado em vez de compilar do código-fonte. Sobe em ~6 s em vez de ~40 s,
    mas usa o último `mvn package` — não enxerga alteração de código feita depois dele.
    Use para conferir a entrega; para desenvolver, rode sem este parâmetro.

.EXAMPLE
    .\rodar-backend.ps1
    Compila o código atual e sobe (equivale ao mvn spring-boot:run).

.EXAMPLE
    .\rodar-backend.ps1 -Jar
    Sobe o JAR já construído, sem compilar.

.NOTES
    Ctrl+C encerra. Para depurar com ponto de parada, use o Run and Debug do VSCode —
    o terminal não dá depurador.
#>
[CmdletBinding()]
param(
    [switch]$Jar
)

$ErrorActionPreference = "Stop"
$raiz = $PSScriptRoot

# ------------------------------------------------------------------ 1. o .env
$arquivoEnv = Join-Path $raiz ".env"
if (-not (Test-Path $arquivoEnv)) {
    Write-Host "[ERRO] .env não encontrado em $raiz" -ForegroundColor Red
    Write-Host "       Copie o .env.example para .env e preencha ADMIN_EMAIL, ADMIN_PASSWORD" -ForegroundColor Red
    Write-Host "       e JWT_SECRET. Sem JWT_SECRET a aplicação não sobe." -ForegroundColor Red
    exit 1
}

$carregadas = @()
foreach ($linha in Get-Content $arquivoEnv) {
    # Ignora comentário e linha vazia; aceita valor com '=' dentro (o split é no primeiro).
    if ($linha -match '^\s*([A-Z_][A-Z0-9_]*)\s*=\s*(.*)$') {
        $nome = $Matches[1]
        $valor = $Matches[2].Trim().Trim('"')
        [Environment]::SetEnvironmentVariable($nome, $valor, "Process")
        $carregadas += $nome
    }
}
# Só os nomes: os valores são senhas e o segredo de assinatura do token.
Write-Host "Variáveis carregadas do .env: $($carregadas -join ', ')" -ForegroundColor DarkGray

if (-not $env:JWT_SECRET) {
    Write-Host "[ERRO] JWT_SECRET não está no .env. A aplicação não sobe sem ele." -ForegroundColor Red
    exit 1
}

# -------------------------------------------------------------- 2. o JDK 21
if (-not $env:JAVA_HOME) {
    $jdk = Get-ChildItem "C:\Program Files\*\jdk-21*" -Directory -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if (-not $jdk) {
        Write-Host "[ERRO] JDK 21 não encontrado. Instale-o ou defina JAVA_HOME." -ForegroundColor Red
        Write-Host "       O projeto não roda em versões mais novas (26, por exemplo)." -ForegroundColor Red
        exit 1
    }
    $env:JAVA_HOME = $jdk.FullName
}

# --------------------------------------------------------- 3. a porta livre
# Respeita SERVER_PORT (do ambiente ou do .env, carregado acima): conferir sempre a 8080 faria
# o script recusar justamente quando a pessoa pediu outra porta para escapar do conflito.
$porta = if ($env:SERVER_PORT) { [int]$env:SERVER_PORT } else { 8080 }

$ocupada = Get-NetTCPConnection -LocalPort $porta -State Listen -ErrorAction SilentlyContinue
if ($ocupada) {
    $dono = (Get-Process -Id $ocupada[0].OwningProcess -ErrorAction SilentlyContinue).ProcessName
    Write-Host "[ERRO] A porta $porta já está em uso pelo processo '$dono' (PID $($ocupada[0].OwningProcess))." -ForegroundColor Red
    Write-Host "       Encerre-o antes, ou rode noutra porta:" -ForegroundColor Red
    Write-Host "         `$env:SERVER_PORT=8081; .\rodar-backend.ps1" -ForegroundColor Red
    exit 1
}

# ------------------------------------------------------------------ 4. subir
Write-Host ""
if ($Jar) {
    $caminhoJar = Join-Path $raiz "target\gestao-operadores.jar"
    if (-not (Test-Path $caminhoJar)) {
        Write-Host "[ERRO] $caminhoJar não existe. Rode .\empacotar.ps1 antes," -ForegroundColor Red
        Write-Host "       ou use .\rodar-backend.ps1 sem -Jar para compilar do código." -ForegroundColor Red
        exit 1
    }
    Write-Host "Subindo o JAR já compilado (não reflete alterações posteriores ao build)." -ForegroundColor Cyan
    Write-Host "Site e API em http://localhost:$porta — Ctrl+C encerra." -ForegroundColor Cyan
    Write-Host ""
    & "$env:JAVA_HOME\bin\java.exe" -jar $caminhoJar
}
else {
    $mvn = Get-Command mvn -ErrorAction SilentlyContinue
    if ($mvn) {
        $mvn = $mvn.Source
    }
    else {
        # Binário baixado pela extensão Java do VSCode: o Maven não está no PATH desta máquina.
        $doWrapper = Get-ChildItem "$env:USERPROFILE\.m2\wrapper\dists\*\*\bin\mvn.cmd" -ErrorAction SilentlyContinue |
            Sort-Object FullName -Descending | Select-Object -First 1
        if (-not $doWrapper) {
            Write-Host "[ERRO] Maven não encontrado. Use .\rodar-backend.ps1 -Jar," -ForegroundColor Red
            Write-Host "       ou instale o Maven e adicione o mvn ao PATH." -ForegroundColor Red
            exit 1
        }
        $mvn = $doWrapper.FullName
    }

    Write-Host "Compilando e subindo pelo Maven (a primeira vez demora mais)." -ForegroundColor Cyan
    Write-Host "Site e API em http://localhost:$porta — Ctrl+C encerra." -ForegroundColor Cyan
    Write-Host ""
    # Sem -o (offline): o spring-boot-maven-plugin tem dependências que nunca foram baixadas
    # e, em modo offline, ele quebra com "cannot access central in offline mode".
    & $mvn -f "$raiz\pom.xml" spring-boot:run
}
