<div align="center">

![Gemini by kaiser Banner](docs/images/banner.png)

# 🌟 Gemini by kaiser (OriginOS China)

**Полноценный Google Gemini и Circle to Search на смартфонах Vivo и iQOO с китайской прошивкой OriginOS — БЕЗ ROOT!**

[![Android](https://img.shields.io/badge/Android-11%20--%2017-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://android.com)
[![OriginOS](https://img.shields.io/badge/OriginOS-4%20%7C%205%20%7C%206%20%7C%207-0052FF?style=for-the-badge)](https://vivo.com)
[![Root](https://img.shields.io/badge/Root-NOT%20REQUIRED-brightgreen?style=for-the-badge)](#)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge)](LICENSE)

[🇷🇺 Инструкция на русском](#-о-проекте) | [🇬🇧 English Documentation](#-about-the-project) | [📥 Скачать APK (Releases)](releases/Gemini_by_kaiser_v3.1.apk) | [📺 YouTube Видео-гайд](https://youtube.com/@KaiserReichDragon)

---

</div>

## 🇷🇺 О проекте

На смартфонах **Vivo** и **iQOO** для китайского рынка (на прошивке **OriginOS**) по умолчанию кнопка питания намертво привязана к китайскому голосовому ассистенту **Jovi** (BlueLM Copilot), а глобальный вызов Google Ассистента или Gemini заблокирован на уровне системы.

**Gemini by kaiser** — это легковесное, безопасное системное решение, которое:
1. Заменяет вызов Jovi на полноценный **голосовой оверлей Google Gemini** поверх любых запущенных приложений и игр.
2. Автоматически активирует **микрофон** и воспроизводит ответы **голосом вслух** (Auto-TTS).
3. Добавляет жест **Circle to Search** (Обвести и найти) по двойному постукиванию по задней крышке смартфона.
4. Сохраняет родное меню выключения / перезагрузки Vivo при повторном зажатии кнопки подряд.
5. Не требует **Root-прав**, разблокировки загрузчика и сохраняет гарантию, банковские приложения (Mir Pay, Т-Банк, Сбер) и обновления по воздуху (OTA).

---

## ⚡ Главные возможности (v3.0)

| Функция | Описание |
|---|---|
| 🎙 **Оверлей Gemini на кнопку питания** | Удержание кнопки питания 0.5 сек открывает компактный плавающий оверлей Gemini. |
| 🗣 **Аппаратный голос «Hi, Jovi»** | Произнесите фразу пробуждения микрофона — откроется Google Gemini и сразу начнет слушать запрос на русском языке. |
| 🔘 **Родное меню выключения** | 2-е зажатие кнопки питания подряд открывает стандартное системное меню выключения/перезагрузки Vivo. |
| 🔍 **Circle to Search (Back Tap)** | Двойной тап по задней спинке телефона мгновенно замораживает экран для выделения любого текста/картинки через Google Поиск. |
| 🔋 **Фоновая оптимизация** | Утилита весит всего ~200 КБ, не расходует батарею и мгновенно просыпается при событии кнопки. |

---

## 📱 Поддерживаемые устройства

Работает на всех смартфонах и планшетах **Vivo** и **iQOO** с китайской версией **OriginOS 4, 5, 6 и 7 (Android 11 – 17)**:
- **Vivo X Series:** X300 Ultra, X300 Pro, X200, X200 Pro, X200 Pro Mini, X100, X100 Pro, X100 Ultra, X90, X80...
- **Vivo X Fold Series:** X Fold 3, X Fold 3 Pro, X Fold 2, X Flip...
- **iQOO Series:** iQOO 13, iQOO 12 / Pro, iQOO 11, iQOO Neo 9 / Pro, Neo 8, Z-серия...
- **Планшеты:** Vivo Pad 3 Pro, Vivo Pad 2, iQOO Pad...

---

## 🚀 Установка

### Способ 1: Автоустановка с ПК за 5 секунд (Рекомендуется)
1. Включите на смартфоне **Отладку по USB** (*Настройки -> Для разработчиков -> Отладка по USB*).
2. Подключите телефон к компьютеру кабелем.
3. Скачайте архив из [Releases](releases/) или папки `installer/`.
4. Запустите файл `Install_Gemini.bat`. Скрипт сам установит APK, выдаст нужные разрешения и переключит кнопку питания в OriginOS.

### Способ 2: Установка прямо на телефоне (Без ПК)
1. Установите [Gemini_by_kaiser_v3.0.apk](releases/Gemini_by_kaiser_v3.0.apk).
2. Если тумблер в *Специальных возможностях* горит серым:
   - Откройте *Настройки -> Приложения -> Gemini by kaiser*.
   - В правом верхнем углу нажмите **3 точки** -> **Разрешить доступ с ограничениями** (Allow restricted settings).
   - Подтвердите паролем блокировки экрана.
3. Включите службу в *Специальных возможностях* -> *Скачанные службы* -> *Gemini by kaiser*.

---

## 🇬🇧 About the Project (English)

On Chinese versions of **Vivo** and **iQOO** smartphones running **OriginOS**, the power button and voice trigger are hard-locked to the Chinese assistant **Jovi** (BlueLM Copilot).

**Gemini by kaiser** resolves this limitation safely without Root:
- Rebinds 0.5s power button hold to floating **Google Gemini overlay**.
- Voice-activated mic listening + speech synthesis response.
- Secondary power button hold retains the native Vivo Power Off / Restart menu.
- **Circle to Search** gesture via Double Back Tap.
- Preserves full OTA updates, Knox/TEE security, and banking apps.

---

## 🛠 Сборка из исходников (Building from Source)

Проект компилируется стандартными средствами Android SDK (AAPT2 + D8):

```bash
# Компиляция ресурсов
aapt2 compile --dir app/res -o build/res.zip
aapt2 link -o build/unaligned.apk -I $ANDROID_HOME/platforms/android-34/android.jar build/res.zip --manifest app/AndroidManifest.xml

# Компиляция Java кода
javac -cp $ANDROID_HOME/platforms/android-34/android.jar -d build/obj app/src/com/kaiserdev/geminitrigger/*.java
d8 --output build/ build/obj/com/kaiserdev/geminitrigger/*.class --lib $ANDROID_HOME/platforms/android-34/android.jar

# Упаковка и подпись
cd build && zip -u unaligned.apk classes.dex
zipalign -v 4 unaligned.apk Gemini_by_kaiser.apk
apksigner sign --ks release.keystore Gemini_by_kaiser.apk
```

---

## 📄 Лицензия

Распространяется под свободной лицензией [MIT](LICENSE). Разрешено свободное использование, модификация и распространение.

Автор: **Kaiser** ([@kaiser10669-svg](https://github.com/kaiser10669-svg) • [@Kaiser](https://youtube.com/@KaiserReichDragon))
