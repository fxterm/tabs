# Replaces the running Tabs with the newly built jar, losing nothing: waits until every claude in it is idle,
# hibernates each live tab, closes Tabs through its own close dialog (which saves every tab of every window),
# swaps the jar, starts it again and checks that every window came back.
#   swap.ps1             stops and says why when a claude is waiting for an answer or working
#   swap.ps1 -Force      hibernates a claude that is waiting for an answer too (the question is lost; the session resumes)
# A copy of the saved state is kept as session.before-swap-<time>.json in ~\fxterm\tabs.
param([switch]$Force, [int]$CalmSeconds = 30, [int]$WaitMinutes = 8)
$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$exe = Join-Path $here 'dist\fxterm-tabs.exe'
$state = Join-Path $HOME 'fxterm\tabs'
function Ctl([string[]]$words) { (& $exe ctl @words) -join "`n" | ConvertFrom-Json }
function Live() { (Ctl 'list').windows.tabs | Where-Object { -not $_.separator -and $_.live } }

$before = (Ctl 'list').windows | ForEach-Object { $_.tabs.Count }
"windows before: " + ($before -join ' + ') + " rows"

# 1. every claude idle for a while
$calm = 0; $deadline = (Get-Date).AddMinutes($WaitMinutes)
while ($calm * 5 -lt $CalmSeconds) {
    if ((Get-Date) -gt $deadline) { throw "gave up waiting for the claudes to be idle" }
    $busy = Live | Where-Object { $_.claude -and ($_.claude.status -eq 'working' -or ($_.claude.status -eq 'waiting' -and -not $Force)) }
    if ($busy) { $calm = 0; "waiting for: " + (($busy | ForEach-Object { "$($_.title) [$($_.claude.status)]" }) -join ', ') }
    else { $calm++ }
    Start-Sleep -Seconds 5
}
# 2. hibernate every live tab, then make sure none is left
foreach ($t in Live) { "hibernating $($t.title)"; [void](Ctl 'hibernate', "id=$($t.id)") }
$until = (Get-Date).AddSeconds(90)
while ((Live) -and (Get-Date) -lt $until) { Start-Sleep -Seconds 2 }
if (Live) { throw "tabs are still live after hibernating: " + ((Live | ForEach-Object title) -join ', ') }

# 3. back up the saved state, then close through the dialog
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
Copy-Item (Join-Path $state 'session.json') (Join-Path $state "session.before-swap-$stamp.json") -ErrorAction SilentlyContinue
Copy-Item (Join-Path $state 'windows.json') (Join-Path $state "windows.before-swap-$stamp.json") -ErrorAction SilentlyContinue
Add-Type @'
using System; using System.Text; using System.Runtime.InteropServices;
public static class Swap {
  delegate bool EnumProc(IntPtr h, IntPtr l);
  [DllImport("user32.dll")] static extern bool EnumWindows(EnumProc p, IntPtr l);
  [DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
  [DllImport("user32.dll", CharSet=CharSet.Unicode)] static extern int GetWindowText(IntPtr h, StringBuilder s, int n);
  [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr h, uint m, IntPtr w, IntPtr l);
  public static IntPtr Find(uint pid, string title) { IntPtr f = IntPtr.Zero;
    EnumWindows((h, l) => { uint p; GetWindowThreadProcessId(h, out p); if (p == pid) { var sb = new StringBuilder(256); GetWindowText(h, sb, 256); if (sb.ToString() == title) f = h; } return true; }, IntPtr.Zero); return f; }
}
'@
$proc = Get-Process fxterm-tabs
& taskkill /pid $proc.Id | Out-Null
$until = (Get-Date).AddSeconds(120)
while ((Get-Process -Id $proc.Id -ErrorAction SilentlyContinue) -and (Get-Date) -lt $until) {
    # the "Close all windows (F2)" question is a window titled Confirmation; the key goes to it by message, so no focus is taken
    $h = [Swap]::Find([uint32]$proc.Id, 'Confirmation')
    if ($h -ne [IntPtr]::Zero) { Start-Sleep -Milliseconds 1500; [void][Swap]::PostMessage($h, 0x100, [IntPtr]0x71, [IntPtr]0); Start-Sleep -Milliseconds 80; [void][Swap]::PostMessage($h, 0x101, [IntPtr]0x71, [IntPtr]0); Start-Sleep -Seconds 3 }
    else { Start-Sleep -Milliseconds 500 }
}
if (Get-Process -Id $proc.Id -ErrorAction SilentlyContinue) { throw "Tabs did not close; nothing was swapped" }

# 4. the new jar, and start
Copy-Item (Join-Path $here 'shade\fxterm-tabs.jar') (Join-Path $here 'dist\fxterm-tabs.jar') -Force
Start-Process $exe
$until = (Get-Date).AddSeconds(90); $after = $null
while (-not $after -and (Get-Date) -lt $until) {
    Start-Sleep -Seconds 3
    try { $after = (Ctl 'list').windows | ForEach-Object { $_.tabs.Count } } catch { }
}
"windows after:  " + ($after -join ' + ') + " rows"
if (($after -join ',') -ne ($before -join ',')) { throw "the windows that came back differ from the ones that were open" }
"swapped: every window and tab is back, all hibernated"
