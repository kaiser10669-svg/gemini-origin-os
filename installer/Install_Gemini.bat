@echo off
setlocal
chcp 65001 >nul
title Установка Gemini by kaiser v3.1 (Vivo & iQOO OriginOS)
cls
echo =====================================================================
echo       GEMINI BY KAISER v3.1 - УСТАНОВЩИК В 1 КЛИК ДЛЯ ORIGINOS
echo =====================================================================
echo.
echo Подключите ваш Vivo или iQOO по USB к компьютеру.
echo Убедитесь, что включена "Отладка по USB" (USB Debugging).
echo.

set "ADB=%~dp0platform-tools\adb.exe"
if not exist "%ADB%" (
    echo [ОШИБКА] Не найден adb.exe в папке platform-tools!
    pause
    exit /b 1
)

set "APK=%~dp0Gemini_by_kaiser_v3.1.apk"
if not exist "%APK%" set "APK=%~dp0..\releases\Gemini_by_kaiser_v3.1.apk"
if not exist "%APK%" set "APK=%~dp0Gemini_by_kaiser_v3.0.apk"

echo [1/4] Проверка подключения устройства...
set "ATTEMPTS=0"
:check_device
"%ADB%" get-state >nul 2>&1
if not errorlevel 1 goto device_ok
set /a ATTEMPTS+=1
if %ATTEMPTS% GEQ 10 (
    echo [ОШИБКА] Смартфон не обнаружен! Проверьте USB-кабель и включите Отладку по USB.
    pause
    exit /b 1
)
echo Ожидание подключения смартфона (%ATTEMPTS%/10)...
ping.exe -n 2 127.0.0.1 >nul
goto check_device

:device_ok
echo [OK] Устройство подключено!
for /f "tokens=*" %%m in ('"%ADB%" shell getprop ro.product.model 2^>nul') do echo Модель: %%m
for /f "tokens=*" %%b in ('"%ADB%" shell getprop ro.build.display.id 2^>nul') do echo Прошивка: %%b
echo.

echo [2/4] Установка приложения Gemini by kaiser v3.1...
"%ADB%" install -r -d "%APK%"
if errorlevel 1 (
    echo [!] Обычная установка не удалась, пробуем установку для user 0...
    "%ADB%" install --user 0 -r -d "%APK%"
)
echo.

echo [3/4] Выдача необходимых системных разрешений...
"%ADB%" shell pm grant com.kaiserdev.geminitrigger android.permission.WRITE_SECURE_SETTINGS
"%ADB%" shell cmd appops set com.kaiserdev.geminitrigger WRITE_SETTINGS allow
"%ADB%" shell cmd appops set com.kaiserdev.geminitrigger SYSTEM_ALERT_WINDOW allow
"%ADB%" shell appops set com.kaiserdev.geminitrigger ACCESS_RESTRICTED_SETTINGS allow
echo.

echo [4/4] Активация перехвата кнопки питания и голоса в OriginOS...
"%ADB%" shell settings put secure long_press_power_key 1
"%ADB%" shell settings put global power_button_long_press 5
"%ADB%" shell settings put system vivo_jovi_power_wakeup_switch 1
"%ADB%" shell settings put system voicewakeup_switch 1
"%ADB%" shell settings put secure copilot_voicewakeup_feedback 0
"%ADB%" shell settings put secure setting_voice_mode 2
"%ADB%" shell settings put secure enabled_accessibility_services com.kaiserdev.geminitrigger/.GeminiPowerService
"%ADB%" shell settings put secure accessibility_enabled 1
echo.

"%ADB%" shell am start -n com.kaiserdev.geminitrigger/.InfoActivity >nul 2>&1

echo =====================================================================
echo                    УСТАНОВКА УСПЕШНО ЗАВЕРШЕНА!
echo =====================================================================
echo.
echo • Зажатие кнопки питания (0.5 сек) -> открывает Google Gemini!
echo • Повторное зажатие (в течение 5 сек) -> штатное меню Выключения/Перезагрузки.
echo • Фраза «Hi, Jovi» -> запускает Google Gemini с русской озвучкой!
echo • Двойной тап по спинке / плитка в шторке -> Circle to Search.
echo.
echo На экране телефона открылось главное меню приложения:
echo Нажмите первые две кнопки, чтобы разрешить «Показ на экране блокировки»!
echo.
pause
exit /b 0
