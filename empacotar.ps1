<#
.SYNOPSIS
    Monta o pacote de entrega para a equipe de TI.

.DESCRIPTION
    Faz o build do front, embute o resultado no JAR, junta os scripts de
    implantação e gera um ZIP único em dist-entrega/.

    A ordem importa: o `mvn package` copia frontend/dist para dentro do JAR,
    então o build do front tem de vir antes. É justamente o que este script
    garante — rodar o Maven sozinho depois de mexer no front gera um JAR com
    a interface antiga.

.PARAMETER PularTestes
    Não roda os testes antes de empacotar. Use só para iterar no próprio
    empacotamento; a entrega de verdade deve rodar com os testes.

.EXAMPLE
    .\empacotar.ps1
#>
[CmdletBinding()]
param(
    [switch]$PularTestes
)

$ErrorActionPreference = "Stop"
$raiz = $PSScriptRoot

function Etapa($texto) {
    Write-Host ""
    Write-Host "==> $texto" -ForegroundColor Cyan
}

# ---------------------------------------------------------------- ferramentas
# O Maven pode não estar no PATH (é o caso da máquina de desenvolvimento, em
# que só existe o binário baixado pela extensão Java do VSCode).
function Achar-Maven {
    $noPath = Get-Command mvn -ErrorAction SilentlyContinue
    if ($noPath) { return $noPath.Source }

    $doWrapper = Get-ChildItem "$env:USERPROFILE\.m2\wrapper\dists\*\*\bin\mvn.cmd" -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending | Select-Object -First 1
    if ($doWrapper) { return $doWrapper.FullName }

    throw "Maven não encontrado. Instale o Maven ou adicione o mvn ao PATH."
}

$mvn = Achar-Maven
Write-Host "Maven: $mvn"

if (-not $env:JAVA_HOME) {
    # O Maven exige JAVA_HOME mesmo quando o java está no PATH.
    $jdk = Get-ChildItem "C:\Program Files\*\jdk-21*" -Directory -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if (-not $jdk) {
        throw "Defina JAVA_HOME apontando para o JDK 21 (o Maven precisa dele)."
    }
    $env:JAVA_HOME = $jdk.FullName
}
Write-Host "JAVA_HOME: $env:JAVA_HOME"

# A versão vem do pom, para o nome do ZIP não divergir do que está no JAR.
$versao = ([xml](Get-Content "$raiz\pom.xml")).project.version
Write-Host "Versão: $versao"

# ------------------------------------------------------------------ 1. front
Etapa "Build do front (Vite)"
Push-Location "$raiz\frontend"
try {
    if (-not (Test-Path "node_modules")) {
        Write-Host "node_modules ausente; rodando npm ci..."
        & npm ci
        if ($LASTEXITCODE -ne 0) { throw "npm ci falhou." }
    }
    if (-not $PularTestes) {
        & npm test
        if ($LASTEXITCODE -ne 0) { throw "Testes do front falharam." }
    }
    # Usa frontend/.env.production, que deixa VITE_API_URL vazia: o front passa
    # a chamar a API por caminho relativo, na mesma origem do JAR.
    & npm run build
    if ($LASTEXITCODE -ne 0) { throw "Build do front falhou." }
}
finally {
    Pop-Location
}

if (-not (Test-Path "$raiz\frontend\dist\index.html")) {
    throw "frontend/dist/index.html não foi gerado; o JAR sairia sem a interface."
}

# ------------------------------------------------------------------- 2. JAR
Etapa "Build do JAR (Maven)"
# [string[]] é necessário: o PowerShell desempacota array de um elemento em
# string, e aí o splat (@metas) passaria letra por letra para o Maven.
[string[]]$metas = if ($PularTestes) { @("package", "-DskipTests") } else { @("package") }
& $mvn -f "$raiz\pom.xml" @metas
if ($LASTEXITCODE -ne 0) {
    # A causa mais provável numa máquina nova: o SubidaDaAplicacaoTest sobe a aplicação de
    # verdade e precisa do banco de teste, que não existe por padrão. O erro do Maven para
    # esse caso ("database ... does not exist") fica enterrado na pilha.
    Write-Host ""
    Write-Host "Se a falha foi em SubidaDaAplicacaoTest com 'database" -ForegroundColor Yellow
    Write-Host "gestao_operadores_teste does not exist', crie o banco de teste (uma vez):" -ForegroundColor Yellow
    Write-Host '  psql -U postgres -c "CREATE DATABASE gestao_operadores_teste OWNER gestao"' -ForegroundColor Yellow
    throw "Build do backend falhou."
}

$jar = "$raiz\target\gestao-operadores.jar"
if (-not (Test-Path $jar)) { throw "JAR não encontrado em $jar." }

# Confere que o site entrou mesmo: um JAR sem /static sobe e serve a API, mas
# devolve 404 na interface — falha que só apareceria no servidor da empresa.
$conteudo = & "$env:JAVA_HOME\bin\jar.exe" tf $jar
if (-not ($conteudo | Select-String -Quiet "BOOT-INF/classes/static/index.html")) {
    throw "O JAR saiu sem o site embutido (BOOT-INF/classes/static/index.html)."
}
Write-Host "Site embutido no JAR: ok"

# -------------------------------------------------------------- 3. montagem
Etapa "Montando o pacote"
$nome = "gestao-operadores-$versao"
$saida = "$raiz\dist-entrega"
$palco = "$saida\$nome"

if (Test-Path $palco) { Remove-Item $palco -Recurse -Force }
New-Item -ItemType Directory -Path $palco -Force | Out-Null

# implantacao/ fica FORA do git de propósito (ver .gitignore e README): num clone novo ela
# não existe. Sem o aviso abaixo, o erro seria um Copy-Item reclamando de caminho — ou, pior,
# um ZIP contendo só o JAR, sem configuração nem instruções, que só se descobre no servidor.
if (-not (Test-Path "$raiz\implantacao\LEIA-ME.md")) {
    Write-Host ""
    Write-Host "[ERRO] A pasta implantacao\ não foi encontrada." -ForegroundColor Red
    Write-Host "       Ela não está no repositório por decisão (o guia descreve a" -ForegroundColor Red
    Write-Host "       infraestrutura interna e este repo é público). Peça os arquivos a" -ForegroundColor Red
    Write-Host "       quem mantém o projeto e coloque-os em:" -ForegroundColor Red
    Write-Host "         $raiz\implantacao\" -ForegroundColor Red
    throw "implantacao/ ausente: o pacote sairia sem configuração nem instruções."
}

Copy-Item $jar "$palco\gestao-operadores.jar"
Copy-Item "$raiz\implantacao\LEIA-ME.md" "$palco\LEIA-ME.md"
foreach ($pasta in @("config", "banco", "windows", "linux", "https")) {
    Copy-Item "$raiz\implantacao\$pasta" "$palco\$pasta" -Recurse
}

# Quebra de linha conforme o sistema que executa cada script: o cmd.exe erra
# em .bat com LF, e o shell não executa .sh com CRLF. O .gitattributes já
# cuida disso no checkout, mas o ZIP não pode depender de como o arquivo
# chegou ao disco de quem empacotou.
$scripts = Get-ChildItem $palco -Recurse -File
foreach ($arquivo in $scripts) {
    $semCrLf = $null
    if ($arquivo.Extension -in ".bat", ".cmd") {
        # Normaliza para LF e depois para CRLF, para não duplicar \r no que já estava certo.
        $semCrLf = ((Get-Content $arquivo.FullName -Raw) -replace "`r`n", "`n") -replace "`n", "`r`n"
    }
    elseif ($arquivo.Extension -eq ".sh") {
        $semCrLf = (Get-Content $arquivo.FullName -Raw) -replace "`r`n", "`n"
    }
    if ($null -ne $semCrLf) {
        # Sem BOM: o cmd.exe imprime os bytes do BOM como lixo na primeira linha.
        [IO.File]::WriteAllText($arquivo.FullName, $semCrLf, [Text.UTF8Encoding]::new($false))
    }
}

# ------------------------------------------------------------------ 4. ZIP
$zip = "$saida\$nome.zip"
if (Test-Path $zip) { Remove-Item $zip -Force }

# As entradas são criadas uma a uma, com o caminho montado à mão, porque as duas formas
# prontas gravam o separador errado no Windows PowerShell 5.1: tanto Compress-Archive quanto
# ZipFile::CreateFromDirectory usam barra INVERTIDA, contrariando a especificação do ZIP.
# No Windows passa despercebido; no Linux o unzip não reconhece aquilo como separador e cria
# arquivos chamados "gestao-operadores-1.0.0\linux\iniciar.sh" soltos numa pasta só —
# justamente para quem fosse usar a unit do systemd e o script .sh daqui.
# Os dois: ZipFile e CreateEntryFromFile estão em .FileSystem; ZipArchive e ZipArchiveMode,
# em System.IO.Compression. Carregar só o primeiro dá "TypeNotFound" no ZipArchiveMode.
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$arquivo = [IO.Compression.ZipFile]::Open($zip, [IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($item in Get-ChildItem $palco -Recurse -File) {
        $relativo = $item.FullName.Substring($saida.Length + 1).Replace('\', '/')
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
            $arquivo, $item.FullName, $relativo, [IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
}
finally {
    $arquivo.Dispose()
}

Remove-Item $palco -Recurse -Force

# Confere o que acabou de ser gravado: errar isto de novo só apareceria no servidor da empresa.
$conferencia = [IO.Compression.ZipFile]::OpenRead($zip)
$invertidas = @($conferencia.Entries | Where-Object { $_.FullName -match '\\' }).Count
$conferencia.Dispose()
if ($invertidas -gt 0) {
    throw "$invertidas entradas do ZIP com barra invertida: não abriria corretamente no Linux."
}

$mb = [math]::Round((Get-Item $zip).Length / 1MB, 1)
Write-Host ""
Write-Host "Pacote pronto: $zip ($mb MB)" -ForegroundColor Green
Write-Host "Entregue este ZIP à TI. As instruções estão no LEIA-ME.md, dentro dele."
Write-Host ""
Write-Host "Confira antes de enviar: o config/application.properties do pacote" -ForegroundColor Yellow
Write-Host "deve estar com os campos em branco — nenhuma senha sua ali." -ForegroundColor Yellow
