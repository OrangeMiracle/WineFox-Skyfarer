param([Parameter(Mandatory=$true)][string]$Ordinary, [Parameter(Mandatory=$true)][string]$Awakened)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$textureRoot = Join-Path $PSScriptRoot '../src/main/resources/assets/winefox_skyfarer/textures/item'
$sources = @{ comet_heart = $Ordinary; everburning_comet_heart = $Awakened }
foreach ($entry in $sources.GetEnumerator()) {
    $sourceImage = [System.Drawing.Image]::FromFile($entry.Value)
    $texture = [System.Drawing.Bitmap]::new(32,32,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($texture)
    try {
        $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
        $graphics.DrawImage($sourceImage,[System.Drawing.Rectangle]::new(0,0,32,32),0,0,$sourceImage.Width,$sourceImage.Height,[System.Drawing.GraphicsUnit]::Pixel)
        $texture.Save((Join-Path $textureRoot ($entry.Key + '.png')),[System.Drawing.Imaging.ImageFormat]::Png)
        Write-Output ($entry.Key + ': 32x32 RGBA; corner alpha=' + $texture.GetPixel(0,0).A)
    } finally { $graphics.Dispose(); $texture.Dispose(); $sourceImage.Dispose() }
}
