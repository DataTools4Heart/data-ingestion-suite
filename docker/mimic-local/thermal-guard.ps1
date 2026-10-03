# Thermal guard for long Ignifyr runs on a laptop. Polls the ACPI thermal zone every IntervalSec seconds and
# duty-cycles the Spark container with the Docker freezer:
#   - package >= LimitC for Samples consecutive samples  -> "docker pause <container>"  (JVM frozen in place, no CPU)
#   - package <= ResumeC for Samples consecutive samples -> "docker unpause <container>" (mapping continues where it was)
# Nothing is lost by a pause; Spark's heartbeat timeout is raised in ignifyr-server.conf so that long pauses are safe.
# If the pause fails (Docker not reachable) the guard stops the running execution(s) through the REST API instead,
# so that the job dies rather than the machine (observed: 99 C sustained -> hard freeze of a ThinkPad T15g).
# Paused intervals are written to the log: subtract them from the execution durations before reporting them.
#   powershell -NoProfile -ExecutionPolicy Bypass -File thermal-guard.ps1 [-LimitC 95] [-ResumeC 80] [-Samples 4] [-IntervalSec 10]
param(
  [int]$LimitC = 95,
  [int]$ResumeC = 80,
  [int]$Samples = 4,
  [int]$IntervalSec = 10,
  [string]$Container = "mimic-ignifyr-server",
  [string]$Api = "http://localhost:6085/ignifyr/projects/mimic/jobs/mimic-hosp-csv-to-fhir-server",
  [string]$Log = "$env:USERPROFILE\mimic-thermal-guard.log"
)
function Log($m) { $line = "{0:yyyy-MM-dd HH:mm:ss} {1}" -f (Get-Date), $m; Add-Content -Path $Log -Value $line; Write-Host $line }
function Temp() {
  try {
    $k = (Get-Counter '\Thermal Zone Information(*)\Temperature' -ErrorAction Stop).CounterSamples |
         Measure-Object -Property CookedValue -Maximum | Select-Object -ExpandProperty Maximum
    return [math]::Round($k - 273.15)
  } catch { return $null }
}
function StopExecutions() {
  try {
    $ex = Invoke-RestMethod -Uri "$Api/executions" -TimeoutSec 20
    foreach ($e in $ex) {
      if ($e.runningStatus) {
        try { Invoke-RestMethod -Method Delete -Uri "$Api/executions/$($e.id)/stop" -TimeoutSec 60 | Out-Null; Log "stopped execution $($e.id)" }
        catch { Log "stop of $($e.id) failed: $($_.Exception.Message)" }
      }
    }
  } catch { Log "could not list executions: $($_.Exception.Message)" }
}
Log "guard start: pause at >=${LimitC}C, resume at <=${ResumeC}C, $Samples samples of ${IntervalSec}s, container=$Container"
$paused = $false; $hot = 0; $cool = 0; $pausedAt = $null; $n = 0
while ($true) {
  $c = Temp
  $n++
  if ($c -ne $null -and $c -ge $LimitC) { $hot++ } else { $hot = 0 }
  if ($c -ne $null -and $c -le $ResumeC) { $cool++ } else { $cool = 0 }
  if ($n % 30 -eq 0) { Log "temperature ${c}C paused=$paused" }   # heartbeat every 5 minutes at the default interval
  if (-not $paused -and $hot -ge $Samples) {
    $r = (& docker pause $Container 2>&1 | Out-String).Trim()
    if ($LASTEXITCODE -eq 0) { $paused = $true; $pausedAt = Get-Date; Log "PAUSED $Container at ${c}C" }
    else { Log "docker pause failed ($r) - stopping executions instead"; StopExecutions; Start-Sleep -Seconds 120 }
    $hot = 0
  }
  elseif ($paused -and $cool -ge $Samples) {
    $r = (& docker unpause $Container 2>&1 | Out-String).Trim()
    if ($LASTEXITCODE -eq 0) {
      $mins = [math]::Round(((Get-Date) - $pausedAt).TotalMinutes, 1)
      $paused = $false; Log "RESUMED $Container at ${c}C after $mins min paused"
    } else { Log "docker unpause failed: $r" }
    $cool = 0
  }
  Start-Sleep -Seconds $IntervalSec
}
