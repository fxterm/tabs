# Brings the window of the given process to the front and saves frames of it from the screen, ~10 a second.
param([int]$TabsPid, [string]$Dir, [int]$Frames = 40)
Add-Type @'
using System; using System.Text; using System.Runtime.InteropServices;
public static class Cap {
  public struct RECT { public int L, T, R, B; }
  delegate bool EnumProc(IntPtr h, IntPtr l);
  [DllImport("user32.dll")] static extern bool EnumWindows(EnumProc p, IntPtr l);
  [DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
  [DllImport("user32.dll")] static extern bool IsWindowVisible(IntPtr h);
  [DllImport("user32.dll", CharSet=CharSet.Unicode)] static extern int GetWindowText(IntPtr h, StringBuilder s, int n);
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
  [DllImport("user32.dll")] static extern bool SetProcessDPIAware();
  [DllImport("user32.dll")] static extern bool SetForegroundWindow(IntPtr h);
  [DllImport("user32.dll")] static extern bool ShowWindow(IntPtr h, int c);
  [DllImport("user32.dll")] static extern void keybd_event(byte vk, byte scan, uint flags, UIntPtr extra);
  public static int[] Rect(IntPtr h) { RECT r; GetWindowRect(h, out r); return new int[] { r.L, r.T, r.R, r.B }; }
  public static IntPtr Find(uint pid) { IntPtr f = IntPtr.Zero; SetProcessDPIAware();
    EnumWindows((h, l) => { uint p; GetWindowThreadProcessId(h, out p); if (p == pid && IsWindowVisible(h)) { var sb = new StringBuilder(256); GetWindowText(h, sb, 256); if (sb.ToString().StartsWith("Tabs")) { RECT r; GetWindowRect(h, out r); if ((r.R - r.L) > 200 && (r.B - r.T) > 200) f = h; } } return true; }, IntPtr.Zero); return f; }
  public static void Front(IntPtr h) { keybd_event(0x12,0,0,UIntPtr.Zero); ShowWindow(h,9); SetForegroundWindow(h); keybd_event(0x12,0,2,UIntPtr.Zero); }
}
'@
Add-Type -AssemblyName System.Drawing
$h = [Cap]::Find([uint32]$TabsPid); if ($h -eq [IntPtr]::Zero) { "NOWINDOW"; exit 2 }
[Cap]::Front($h); Start-Sleep -Milliseconds 1500
$q = [Cap]::Rect($h); $r = [pscustomobject]@{ L = $q[0]; T = $q[1]; R = $q[2]; B = $q[3] }
"rect: $($q -join ',')"; $w = $r.R - $r.L; $ht = $r.B - $r.T; New-Item -ItemType Directory -Force $Dir | Out-Null
for ($i = 1; $i -le $Frames; $i++) {
  $bmp = New-Object System.Drawing.Bitmap $w, $ht; $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.CopyFromScreen($r.L, $r.T, 0, 0, $bmp.Size); $bmp.Save("$Dir\f$i.png"); $g.Dispose(); $bmp.Dispose(); Start-Sleep -Milliseconds 40
}
"captured $Frames frames of ${w}x${ht} at $($r.L),$($r.T)"
