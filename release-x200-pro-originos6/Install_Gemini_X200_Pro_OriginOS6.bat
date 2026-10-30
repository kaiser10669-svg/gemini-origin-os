@echo off
setlocal
chcp 65001 >nul
title Gemini by kaiser - X200 Pro / OriginOS 6
cls
echo =====================================================================
echo       GEMINI v3.1 - ТОЛЬКО X200 PRO / ORIGINOS 6
echo =====================================================================
echo.
echo Для X500 Ultra / OriginOS 7 используйте исходный installer\Install_Gemini.bat
echo Подключите только один телефон по USB, разрешите отладку и разблокируйте его
echo Скрипт работает с основным пользователем Android (user 0)
echo.

set "ADB=%~dp0platform-tools\adb.exe"
if not exist "%ADB%" set "ADB=%~dp0..\..\installer\platform-tools\adb.exe"
if not exist "%ADB%" goto missing_files
set "APK=%~dp0Gemini_X200_Pro_OriginOS6_v3.1.apk"
if not exist "%APK%" goto missing_files

echo [1/4] Проверка подключения и конфигурации устройства...
set "CONNECTION_ATTEMPTS=0"
:check_connection
"%ADB%" get-state
if not errorlevel 1 goto connected
set /a CONNECTION_ATTEMPTS+=1 >nul
if %CONNECTION_ATTEMPTS% GEQ 10 goto error
echo Ожидание подключения после запуска ADB...
ping.exe -n 2 -w 1000 127.0.0.1 >nul
goto check_connection

:connected
echo Модель устройства:
"%ADB%" shell getprop ro.product.model
if errorlevel 1 goto error
echo Версия сборки:
"%ADB%" shell getprop ro.build.display.id
if errorlevel 1 goto error
echo.
echo Продолжайте только на X200 Pro с OriginOS 6
echo Профиль выбран вручную, скрипт не определяет версию OriginOS автоматически
echo Будут настроены кнопка питания, ассистент и тихий режим ответов Jovi
choice /c YN /n /m "Установить вариант X200 Pro / OriginOS 6? [Y/N]: "
if errorlevel 2 exit /b 0
if errorlevel 1 goto install
exit /b 1

:install
echo.
echo [2/4] Установка отдельной версии X200 Pro / OriginOS 6...
"%ADB%" install --user 0 -r "%APK%"
if errorlevel 1 goto error
echo.

echo [3/4] Выдача необходимых разрешений...
"%ADB%" shell pm grant --user 0 com.kaiserdev.geminitrigger android.permission.WRITE_SECURE_SETTINGS
if errorlevel 1 goto error
"%ADB%" shell cmd appops set --user 0 com.kaiserdev.geminitrigger WRITE_SETTINGS allow
if errorlevel 1 goto error
"%ADB%" shell cmd appops set --user 0 com.kaiserdev.geminitrigger SYSTEM_ALERT_WINDOW allow
if errorlevel 1 goto error
echo.

echo [4/4] Настройка вызова Gemini на X200 Pro / OriginOS 6...
"%ADB%" shell settings --user 0 put system vivo_jovi_power_wakeup_switch 1
if errorlevel 1 goto error
"%ADB%" shell settings --user 0 put secure long_press_power_key 1
if errorlevel 1 goto error
"%ADB%" shell settings put global power_button_long_press 5
if errorlevel 1 goto error
"%ADB%" shell settings --user 0 put secure setting_voice_mode 2
if errorlevel 1 goto error
"%ADB%" shell am start --user 0 -f 0x14000000 -n com.kaiserdev.geminitrigger/.InfoActivity
if errorlevel 1 goto error
echo.
echo =====================================================================
echo          APK X200 PRO / ORIGINOS 6 УСТАНОВЛЕН
echo =====================================================================
echo.
echo Завершите настройку вручную по README.md в этой папке
echo В Gemini разрешите использование на заблокированном экране
echo В OriginOS для Google и Gemini by kaiser включите Display on lock screen
echo Для Gemini by kaiser также включите Background pop-ups и Autostart
echo Голосовое пробуждение Hi, Jovi включается отдельно
echo Озвучка ответов Jovi отключена, чтобы голосом отвечал только Gemini
echo.
pause
exit /b 0

:missing_files
echo Не найден APK X200 Pro или platform-tools\adb.exe
echo Сохраните структуру репозитория или положите platform-tools рядом со скриптом
goto error

:error
echo.
echo Установка не завершена - проверьте сообщение об ошибке выше
echo При отказе Android разблокируйте телефон и подтвердите установку, затем повторите запуск
echo Уже выполненные команды автоматически не откатываются
echo При несовпадении подписи скрипт остановится и не удалит приложение
echo Порядок переустановки и возврата к v3.0 описан в README.md этой папки
echo.
pause
exit /b 1
