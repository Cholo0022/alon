# Valida pedidos sin cuenta. Reinicia EXCLUSIVAMENTE la base desechable alon_validation.
$ErrorActionPreference = 'Stop'
$alonProject = Split-Path -Parent $PSScriptRoot
$alonNames = @('MYSQL_PWD','ALON_TEST_MYSQL_URL','ALON_TEST_MYSQL_USER','ALON_TEST_MYSQL_PASSWORD')
$alonSaved=@{}
foreach($alonName in $alonNames){$alonSaved[$alonName]=[Environment]::GetEnvironmentVariable($alonName,'Process')}
$alonLocation=Get-Location
try {
    $alonSettings=Get-Content -LiteralPath (Join-Path $alonProject '.validation/connection/startup.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    $alonCredential=Import-Clixml -LiteralPath (Join-Path $alonProject '.validation/connection/mysql-credential.xml')
    $alonPrefix=[regex]::Match($alonSettings.databaseUrl,'^jdbc:mysql://[^/]+/').Value
    if(!$alonPrefix){throw 'La URL guardada no es compatible con esta prueba.'}
    $alonUri=[uri]($alonSettings.databaseUrl -replace '^jdbc:','')
    $alonPort=if($alonUri.Port -gt 0){$alonUri.Port}else{3306}
    $alonMySql=@('C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe','C:/Program Files/MySQL/MySQL Server 8.4/bin/mysql.exe') | Where-Object {Test-Path -LiteralPath $_} | Select-Object -First 1
    if(!$alonMySql){throw 'No se encontro mysql.exe.'}
    $env:MYSQL_PWD=$alonCredential.GetNetworkCredential().Password
    & $alonMySql "--host=$($alonUri.Host)" "--port=$alonPort" "--user=$($alonCredential.UserName)" --connect-timeout=4 --batch --skip-column-names --execute='SELECT VERSION()'
    if($LASTEXITCODE -ne 0){throw 'La conexion a MySQL fue rechazada. No se ejecuto la prueba.'}
    $env:ALON_TEST_MYSQL_URL=$alonPrefix+'alon_validation?createDatabaseIfNotExist=true&allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=America/Argentina/Buenos_Aires'
    $env:ALON_TEST_MYSQL_USER=$alonCredential.UserName
    $env:ALON_TEST_MYSQL_PASSWORD=$alonCredential.GetNetworkCredential().Password
    Write-Host 'La prueba recrea solo alon_validation. No toca la base del negocio ni las bases de rendimiento.'
    Set-Location -LiteralPath (Join-Path $alonProject 'backend')
    $alonMavenArgs=@('test','-Dtest=MySqlIntegrationTest#guestOrderReusesExistingCustomerByDniWithoutReplacingTheirData+guestOrdersKeepContactSnapshotsReplayAndBecomeReturningOnlyAfterApproval+rejectedGuestOrdersDoNotCountAsPurchasesAndSharedPhonesDoNotMergeDifferentDni+webApprovalCreatesLinkedSaleAndCashAtOrderPrices,GuestCheckoutHttpTest')
    $alonAgentRoot=Join-Path $env:USERPROFILE '.m2/repository/net/bytebuddy/byte-buddy-agent'
    if(Test-Path -LiteralPath $alonAgentRoot){$alonAgent=Get-ChildItem -LiteralPath $alonAgentRoot -Recurse -File -Filter 'byte-buddy-agent-*.jar' | Sort-Object FullName -Descending | Select-Object -First 1; if($alonAgent){$alonMavenArgs+=('-DargLine=-javaagent:'+$alonAgent.FullName)}}
    & mvn @alonMavenArgs
    if($LASTEXITCODE -ne 0){throw 'Fallo la validacion de pedidos web.'}
    Write-Host 'Pedidos web: validacion completada.'
} finally {
    Set-Location -LiteralPath $alonLocation
    foreach($alonName in $alonNames){[Environment]::SetEnvironmentVariable($alonName,$alonSaved[$alonName],'Process')}
}
