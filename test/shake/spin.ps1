# What programs do to a tab's title while they work, one family per -Mode:
#   claude   a star symbol in front of the name, a new one every ~120 ms
#   braille  the dots npm, pip and cargo use
#   wheel    plain ASCII | / - \ in front of the name
#   counter  no symbol; the text itself counts: "name (1s)", "name (10s)", "name (100s)": a width that keeps changing
#   words    no symbol; the text swings between one letter and a very long word
# Written as raw UTF-8 bytes: the console's own encoding turns the symbols into "?".
param([string]$Mode = 'claude')
$out = [Console]::OpenStandardOutput(); $utf8 = New-Object System.Text.UTF8Encoding($false)
$e = [char]27; $i = 0
$sets = @{
  claude  = @(0xB7, 0x2722, 0x2733, 0x2736, 0x273B, 0x273D) | ForEach-Object { [string][char]$_ }
  braille = @(0x280B,0x2819,0x2839,0x2838,0x283C,0x2834,0x2826,0x2827,0x2807,0x280F) | ForEach-Object { [string][char]$_ }
  wheel   = @('|', '/', '-', '\')
}
while ($true) {
  switch ($Mode) {
    'counter' { $t = 'build (' + [string](($i * 7) % 130) + 's)' }
    'words'   { $t = if ($i % 2) { 'x' } else { 'project-01-archive-with-a-very-long-descriptive-name-here' } }
    default   { $f = $sets[$Mode]; $t = $f[$i % $f.Count] + ' project-01-archive' }
  }
  $bytes = $utf8.GetBytes("$e]0;" + $t + [char]7)
  $out.Write($bytes, 0, $bytes.Length); $out.Flush(); $i++; Start-Sleep -Milliseconds 120
}
