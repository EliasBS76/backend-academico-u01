# Reto U12: evidencia repetible sin IDE.
# Uso (desde la raíz del proyecto, con "docker compose up -d" ya corriendo):
#   .\evidencia\evidencia-u12.ps1                    casos de la API, seguridad, propietario y Actuator
#   .\evidencia\evidencia-u12.ps1 -Falla             detiene PostgreSQL, observa readiness y lo vuelve a iniciar
#   .\evidencia\evidencia-u12.ps1 -Persistencia      reinicia el contenedor app y comprueba que los datos siguen
#   -Base http://localhost:8081                      si publicaste la app en otro puerto (APP_PORT)
param(
    [string]$Base = "http://localhost:8080",
    [switch]$Falla,
    [switch]$Persistencia
)

$ErrorActionPreference = "Stop"
$tmp = Join-Path ([IO.Path]::GetTempPath()) "evidencia-u12"
New-Item -ItemType Directory -Force $tmp | Out-Null
$script:fallas = 0

# Hace una petición con curl.exe y devuelve status y cuerpo. El cuerpo JSON va por archivo
# porque Windows PowerShell 5.1 altera las comillas al pasarlas a programas nativos.
function Invoke-Peticion {
    param([string]$Metodo, [string]$Ruta, [string]$Usuario, [string]$Clave,
          [string]$Sesion, [string]$Csrf, [string]$Json)
    $salida = Join-Path $tmp "respuesta.txt"
    $argumentos = @("-s", "-o", $salida, "-w", "%{http_code}", "-X", $Metodo, "--max-time", "20")
    if ($Usuario) { $argumentos += @("-u", "${Usuario}:${Clave}") }
    if ($Sesion)  { $argumentos += @("-c", $Sesion, "-b", $Sesion) }
    if ($Csrf)    { $argumentos += @("-H", "X-CSRF-TOKEN: $Csrf") }
    if ($Json) {
        $archivo = Join-Path $tmp "cuerpo.json"
        [IO.File]::WriteAllText($archivo, $Json)
        $argumentos += @("-H", "Content-Type: application/json", "--data-binary", "@$archivo")
    }
    $codigo = & curl.exe @argumentos "$Base$Ruta"
    $cuerpo = if (Test-Path $salida) { Get-Content $salida -Raw -Encoding UTF8 } else { "" }
    [pscustomobject]@{ Status = [int]$codigo; Cuerpo = $cuerpo }
}

# Docker escribe su progreso en stderr; en Windows PowerShell 5.1 eso cuenta como error con
# $ErrorActionPreference = "Stop". Pasar por cmd /c evita que se interprete así.
function Invoke-Compose {
    param([string]$Argumentos)
    & cmd /c "docker compose $Argumentos >nul 2>&1"
    "   (docker compose $Argumentos)"
}

function Test-Caso {
    param([string]$Nombre, [int]$Esperado, $Respuesta)
    $ok = $Respuesta.Status -eq $Esperado
    if (-not $ok) { $script:fallas++ }
    $marca = if ($ok) { "OK " } else { "FALLA" }
    $resumen = ""
    if ($Respuesta.Cuerpo) { $resumen = ($Respuesta.Cuerpo -replace "\s+", " ").Substring(0, [Math]::Min(110, $Respuesta.Cuerpo.Length)) }
    "{0,-5} {1,-52} esperado {2} obtenido {3}  {4}" -f $marca, $Nombre, $Esperado, $Respuesta.Status, $resumen
}

function Get-Token {
    param([string]$Usuario, [string]$Clave, [string]$Sesion)
    if (Test-Path $Sesion) { Remove-Item $Sesion }
    $r = Invoke-Peticion GET "/api/v1/csrf" $Usuario $Clave $Sesion
    ($r.Cuerpo | ConvertFrom-Json).token
}

function Wait-Readiness {
    param([int]$Segundos = 90)
    $limite = (Get-Date).AddSeconds($Segundos)
    while ((Get-Date) -lt $limite) {
        try { if ((Invoke-Peticion GET "/actuator/health/readiness").Status -eq 200) { return $true } } catch { }
        Start-Sleep -Seconds 2
    }
    return $false
}

"== Esperando a que la aplicación esté lista en $Base"
if (-not (Wait-Readiness)) { throw "La aplicación no llegó a readiness UP" }

if ($Falla) {
    "== Falla controlada de PostgreSQL"
    Test-Caso "readiness antes de la falla" 200 (Invoke-Peticion GET "/actuator/health/readiness")
    Invoke-Compose "stop postgres"
    Test-Caso "readiness con PostgreSQL detenido" 503 (Invoke-Peticion GET "/actuator/health/readiness")
    Test-Caso "liveness con PostgreSQL detenido" 200 (Invoke-Peticion GET "/actuator/health/liveness")
    Test-Caso "health general con PostgreSQL detenido" 503 (Invoke-Peticion GET "/actuator/health")
    Test-Caso "health con detalle (admin)" 503 (Invoke-Peticion GET "/actuator/health" "admin" "admin123")
    $inicio = Get-Date
    $api = Invoke-Peticion GET "/api/v1/expediente" "alumno" "alumno123"
    $ms = [int]((Get-Date) - $inicio).TotalMilliseconds
    Test-Caso "API que necesita la base ($ms ms)" 500 $api
    Invoke-Compose "start postgres"
    if (Wait-Readiness 60) { Test-Caso "readiness al volver PostgreSQL" 200 (Invoke-Peticion GET "/actuator/health/readiness") }
    else { "FALLA readiness no volvió a UP"; $script:fallas++ }
}
elseif ($Persistencia) {
    "== Persistencia tras reiniciar el contenedor app"
    $antes = Invoke-Peticion GET "/api/v1/expediente" "alumno" "alumno123"
    Test-Caso "expediente A001 antes de reiniciar" 200 $antes
    Invoke-Compose "restart app"
    if (-not (Wait-Readiness)) { throw "La aplicación no volvió después del reinicio" }
    $despues = Invoke-Peticion GET "/api/v1/expediente" "alumno" "alumno123"
    Test-Caso "expediente A001 después de reiniciar" 200 $despues
    if ($antes.Cuerpo -ne $despues.Cuerpo) { "FALLA el expediente cambió tras el reinicio"; $script:fallas++ }
    else { "OK    mismo cuerpo antes y después: $($despues.Cuerpo)" }
}
else {
    $docente = Join-Path $tmp "docente.cookies"
    $alumno = Join-Path $tmp "alumno.cookies"
    $tokenDocente = Get-Token "docente" "docente123" $docente
    $tokenAlumno = Get-Token "alumno" "alumno123" $alumno

    "== Datos de laboratorio (A001 = cuenta alumno, A002 = cuenta alumno2; 201 o 409 si ya existían)"
    foreach ($m in @(@("A001", "Ada Lovelace", "ada@u.mx"), @("A002", "Emmy Noether", "emmy@u.mx"))) {
        $r = Invoke-Peticion POST "/api/v1/alumnos" "docente" "docente123" $docente $tokenDocente `
            ('{"matricula":"' + $m[0] + '","nombre":"' + $m[1] + '","correo":"' + $m[2] + '"}')
        "      {0} -> {1}" -f $m[0], $r.Status
    }
    $idA001 = ((Invoke-Peticion GET "/api/v1/expediente" "alumno" "alumno123").Cuerpo | ConvertFrom-Json).id
    $idA002 = ((Invoke-Peticion GET "/api/v1/expediente" "alumno2" "alumno2123").Cuerpo | ConvertFrom-Json).id

    "== Casos de la API"
    $matricula = "E" + (Get-Random -Minimum 10000 -Maximum 99999)
    $nuevo = '{"matricula":"' + $matricula + '","nombre":"Grace Hopper","correo":"grace@u.mx"}'
    $creado = Invoke-Peticion POST "/api/v1/alumnos" "docente" "docente123" $docente $tokenDocente $nuevo
    Test-Caso "éxito: crear alumno $matricula" 201 $creado
    $id = ($creado.Cuerpo | ConvertFrom-Json).id
    Test-Caso "éxito: consultar el alumno creado" 200 (Invoke-Peticion GET "/api/v1/alumnos/$id" "docente" "docente123")
    Test-Caso "validación: nombre corto y correo inválido" 400 (Invoke-Peticion POST "/api/v1/alumnos" "docente" "docente123" $docente $tokenDocente '{"matricula":"X1","nombre":"Al","correo":"no-es-correo"}')
    Test-Caso "ausencia: alumno 999999" 404 (Invoke-Peticion GET "/api/v1/alumnos/999999" "docente" "docente123")
    Test-Caso "conflicto: misma matrícula otra vez" 409 (Invoke-Peticion POST "/api/v1/alumnos" "docente" "docente123" $docente $tokenDocente $nuevo)

    "== Rechazos de seguridad (causas distintas)"
    Test-Caso "sin credenciales" 401 (Invoke-Peticion GET "/api/v1/alumnos")
    Test-Caso "credenciales incorrectas" 401 (Invoke-Peticion GET "/api/v1/alumnos" "docente" "equivocada")
    Test-Caso "rol insuficiente: ALUMNO crea (con CSRF válido)" 403 (Invoke-Peticion POST "/api/v1/alumnos" "alumno" "alumno123" $alumno $tokenAlumno '{"matricula":"Z1","nombre":"Intruso","correo":"z@u.mx"}')
    Test-Caso "sin token CSRF: DOCENTE crea" 403 (Invoke-Peticion POST "/api/v1/alumnos" "docente" "docente123" $null $null '{"matricula":"Z2","nombre":"Sin token","correo":"z@u.mx"}')

    "== Mejora por propietario: historial de inscripciones"
    Test-Caso "alumno ve su propio historial (id $idA001)" 200 (Invoke-Peticion GET "/api/v1/inscripciones/alumno/$idA001" "alumno" "alumno123")
    Test-Caso "alumno pide el historial ajeno (id $idA002)" 403 (Invoke-Peticion GET "/api/v1/inscripciones/alumno/$idA002" "alumno" "alumno123")
    Test-Caso "alumno pide un id inexistente" 403 (Invoke-Peticion GET "/api/v1/inscripciones/alumno/999999" "alumno" "alumno123")
    Test-Caso "docente ve cualquier historial" 200 (Invoke-Peticion GET "/api/v1/inscripciones/alumno/$idA002" "docente" "docente123")

    "== Actuator"
    Test-Caso "health público" 200 (Invoke-Peticion GET "/actuator/health")
    Test-Caso "liveness" 200 (Invoke-Peticion GET "/actuator/health/liveness")
    Test-Caso "readiness (incluye db)" 200 (Invoke-Peticion GET "/actuator/health/readiness")
    Test-Caso "métricas sin credenciales" 401 (Invoke-Peticion GET "/actuator/metrics")
    Test-Caso "métricas como ALUMNO" 403 (Invoke-Peticion GET "/actuator/metrics" "alumno" "alumno123")
    Test-Caso "métricas como ADMIN" 200 (Invoke-Peticion GET "/actuator/metrics" "admin" "admin123")
    $resumen = Invoke-Peticion GET "/actuator/metrics/academico.resumen.ejecuciones" "admin" "admin123"
    if ($resumen.Status -eq 200) { Test-Caso "contador de la tarea programada" 200 $resumen }
    else { "INFO  el contador de la tarea aparece después del primer minuto (status $($resumen.Status))" }

    "== Caché (con SQL_LOG=DEBUG revisa: docker compose logs app | Select-String 'from alumnos')"
    1..2 | ForEach-Object { Test-Caso "consulta repetida $_ del alumno $id" 200 (Invoke-Peticion GET "/api/v1/alumnos/$id" "docente" "docente123") }
    Test-Caso "actualizar invalida su entrada" 200 (Invoke-Peticion PUT "/api/v1/alumnos/$id" "docente" "docente123" $docente $tokenDocente '{"nombre":"Grace B. Hopper","correo":"grace.hopper@u.mx"}')
    $tras = Invoke-Peticion GET "/api/v1/alumnos/$id" "docente" "docente123"
    Test-Caso "consulta después de actualizar" 200 $tras
    if ($tras.Cuerpo -notmatch "Grace B. Hopper") { "FALLA la consulta devolvió un valor obsoleto"; $script:fallas++ }
}

""
if ($script:fallas -eq 0) { "Resultado: todos los casos coinciden con lo esperado." }
else { "Resultado: $($script:fallas) caso(s) no coinciden."; exit 1 }
