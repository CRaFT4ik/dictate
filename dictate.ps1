# Получаем имя видеокарты, пропуская стандартный драйвер Microsoft
$GPU = Get-CimInstance Win32_VideoController | 
       Where-Object { $_.Name -and $_.Name -notmatch 'Microsoft Basic Display Adapter' } | 
       Select-Object -ExpandProperty Name -First 1

if (-not $GPU) { $GPU = "unknown GPU" }

Write-Host "Starting voice dictation on: $GPU..." -ForegroundColor Cyan

# Запуск питона в той же папке, где лежит этот скрипт
$ScriptPath = Split-Path -Parent $MyInvocation.MyCommand.Definition
python "$ScriptPath\client.py"

# Пауза, если скрипт упадет (чтобы успеть прочитать ошибку)
if ($LASTEXITCODE -ne 0) { Pause }
