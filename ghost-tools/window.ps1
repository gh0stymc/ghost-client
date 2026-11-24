# Captura la ventana de Minecraft o le manda clics/teclas SIN tocar el ratón ni el foco del usuario.
# Funciona aunque la ventana esté detrás de otras.
# Uso: window.ps1 shot <archivo.png>
#      window.ps1 click <x 0-1> <y 0-1>     (posición relativa dentro de la ventana)
#      window.ps1 key <ESCAPE|ENTER>
param([string]$action, [string]$a, [string]$b)

Add-Type -AssemblyName System.Drawing
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class W {
  [StructLayout(LayoutKind.Sequential)] public struct RECT { public int L, T, R, B; }
  [DllImport("user32.dll")] public static extern bool GetClientRect(IntPtr h, out RECT r);
  [DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr h, IntPtr hdc, uint flags);
  [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr h, uint msg, IntPtr w, IntPtr l);
}
"@

$proc = Get-Process javaw, java -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowHandle -ne 0 -and $_.MainWindowTitle -match 'Minecraft|Ghost' } | Select-Object -First 1
if (-not $proc) { Write-Output "No hay ventana de Minecraft"; exit 1 }
$h = $proc.MainWindowHandle
$r = New-Object W+RECT
[W]::GetClientRect($h, [ref]$r) | Out-Null
$w = $r.R; $hgt = $r.B

switch ($action) {
  'shot' {
    $bmp = New-Object System.Drawing.Bitmap $w, $hgt
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $hdc = $g.GetHdc()
    # 3 = solo el área del juego + contenido acelerado por GPU
    [W]::PrintWindow($h, $hdc, 3) | Out-Null
    $g.ReleaseHdc($hdc)
    $bmp.Save($a, [System.Drawing.Imaging.ImageFormat]::Png)
    Write-Output "Título: $($proc.MainWindowTitle) | $w x $hgt"
  }
  'click' {
    $x = [int]($w * [double]$a); $y = [int]($hgt * [double]$b)
    $lp = [IntPtr](($y -shl 16) -bor $x)
    [W]::PostMessage($h, 0x0200, [IntPtr]0, $lp) | Out-Null   # mover
    Start-Sleep -Milliseconds 120
    [W]::PostMessage($h, 0x0201, [IntPtr]1, $lp) | Out-Null   # botón abajo
    Start-Sleep -Milliseconds 60
    [W]::PostMessage($h, 0x0202, [IntPtr]0, $lp) | Out-Null   # botón arriba
  }
  'type' {
    foreach ($c in $a.ToCharArray()) {
      [W]::PostMessage($h, 0x0102, [IntPtr][int]$c, [IntPtr]1) | Out-Null
      Start-Sleep -Milliseconds 40
    }
  }
  'rclick' {
    $x = [int]($w * [double]$a); $y = [int]($hgt * [double]$b)
    $lp = [IntPtr](($y -shl 16) -bor $x)
    [W]::PostMessage($h, 0x0200, [IntPtr]0, $lp) | Out-Null
    Start-Sleep -Milliseconds 120
    [W]::PostMessage($h, 0x0204, [IntPtr]2, $lp) | Out-Null
    Start-Sleep -Milliseconds 60
    [W]::PostMessage($h, 0x0205, [IntPtr]0, $lp) | Out-Null
  }
  'drag' {
    # window.ps1 drag "x1,y1" "x2,y2"  (posiciones relativas 0-1)
    $p1 = $a.Split(','); $p2 = $b.Split(',')
    $x1 = [int]($w * [double]$p1[0]); $y1 = [int]($hgt * [double]$p1[1])
    $x2 = [int]($w * [double]$p2[0]); $y2 = [int]($hgt * [double]$p2[1])
    [W]::PostMessage($h, 0x0200, [IntPtr]0, [IntPtr](($y1 -shl 16) -bor $x1)) | Out-Null
    Start-Sleep -Milliseconds 120
    [W]::PostMessage($h, 0x0201, [IntPtr]1, [IntPtr](($y1 -shl 16) -bor $x1)) | Out-Null
    for ($i = 1; $i -le 10; $i++) {
      Start-Sleep -Milliseconds 40
      $x = [int]($x1 + ($x2 - $x1) * $i / 10); $y = [int]($y1 + ($y2 - $y1) * $i / 10)
      [W]::PostMessage($h, 0x0200, [IntPtr]1, [IntPtr](($y -shl 16) -bor $x)) | Out-Null
    }
    Start-Sleep -Milliseconds 80
    [W]::PostMessage($h, 0x0202, [IntPtr]0, [IntPtr](($y2 -shl 16) -bor $x2)) | Out-Null
  }
  'key' {
    $codes = @{ ESCAPE = @(0x1B, 0x01); ENTER = @(0x0D, 0x1C); SPACE = @(0x20, 0x39); RIGHT = @(0x27, 0x14D); DOWN = @(0x28, 0x150); BACK = @(0x08, 0x0E); TAB = @(0x09, 0x0F); RSHIFT = @(0xA1, 0x36); D1 = @(0x31, 0x02); D2 = @(0x32, 0x03); D3 = @(0x33, 0x04) }
    $vk = $codes[$a][0]; $scan = $codes[$a][1]
    [W]::PostMessage($h, 0x0100, [IntPtr]$vk, [IntPtr](1 -bor ($scan -shl 16))) | Out-Null
    Start-Sleep -Milliseconds 60
    [W]::PostMessage($h, 0x0101, [IntPtr]$vk, [IntPtr]((1 -bor ($scan -shl 16)) -bor 0xC0000000)) | Out-Null
  }
}
