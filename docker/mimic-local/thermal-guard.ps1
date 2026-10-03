# Thermal guard for long Ignifyr runs on a laptop: polls the ACPI thermal zone and stops the running execution(s)
# when the package stays at or above the limit for several consecutive samples, before the firmware hangs the machine
# (observed on a ThinkPad T15g: 99 C sustained -> freeze). Run it before starting a job, in its own window or hidden:
#   powershell -NoProfile -ExecutionPolicy Bypass -File thermal-guard.ps1 [-LimitC 95] [-Samples 4] [-IntervalSec 10]
# It only reads counters and calls the Ignifyr REST API; it never changes power settings.
param(
  [int]$LimitC = 95,
  [int]$Samples = 4,
  [int]$IntervalSec = 10,
  [string]$Api = "http://localhost:6085/ignifyr/projects/mimic/jobs/mimic-hosp-csv-to-fhir-server",
  [string]$Log = "$env:USERPROFILE\mimic-thermal-guard.log"
)
function Log($m) { $line = "{0:yyyy-MM-dd HH:mm:ss} {1}" -f (Get-Date), $m; Add-Content -Path $Log -Value $line; Write-Host $line }
Log "guard start: limit=${LimitC}C samples=$Samples interval=${IntervalSec}s api=$Api"
$hot = 0
while ($true) {
  try {
    $k = (Get-Counter '\Thermal Zone Information(*)\Temperature' -ErrorAction Stop).CounterSamples |
         Measure-Object -Property CookedValue -Maximum | Select-Object -ExpandProperty Maximum
    $c = [math]::Round($k - 273.15)
  } catch { $c = $null }
  if ($c -ne $null -and $c -ge $LimitC) { $hot++ } else { $hot = 0 }
  if ($hot -eq 1 -or ($hot -gt 0 -and $hot % 2 -eq 0)) { Log "temperature ${c}C (hot sample $hot of $Samples)" }
  if ($hot -ge $Samples) {
    Log "LIMIT: ${c}C for $hot samples - stopping running executions"
    try {
      $ex = Invoke-RestMethod -Uri "$Api/executions" -TimeoutSec 20
      foreach ($e in $ex) {
        if ($e.runningStatus) {
          try { Invoke-RestMethod -Method Delete -Uri "$Api/executions/$($e.id)/stop" -TimeoutSec 60 | Out-Null; Log "stopped execution $($e.id)" }
          catch { Log "stop of $($e.id) failed: $($_.Exception.Message)" }
        }
      }
    } catch { Log "could not list executions: $($_.Exception.Message)" }
    $hot = 0
    Start-Sleep -Seconds 120
  }
  Start-Sleep -Seconds $IntervalSec
}
