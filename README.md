<div align="center">

![Gemini by kaiser Banner](docs/images/banner.png)

# 🌟 Gemini by kaiser (OriginOS China)

**Полноценный Google Gemini и Circle to Search на смартфонах Vivo и iQOO с китайской прошивкой OriginOS — БЕЗ ROOT!**

[![Android](https://img.shields.io/badge/Android-11%20--%2017-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://android.com)
[![OriginOS](https://img.shields.io/badge/OriginOS-4%20%7C%205%20%7C%206%20%7C%207-0052FF?style=for-the-badge)](https://vivo.com)
[![Root](https://img.shields.io/badge/Root-NOT%20REQUIRED-brightgreen?style=for-the-badge)](#)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge)](LICENSE)

[🇷🇺 Инструкция на русском](#-о-проекте) | [🇬🇧 English Documentation](#-about-the-project) | [📥 Скачать APK v3.2 (Releases)](releases/Gemini_by_kaiser_v3.2.apk) | [📺 YouTube Видео-гайд](https://youtube.com/@KaiserReichDragon)

---

</div>

### 🆕 Что нового в v3.2.6:
- **⚡ Авто-настройка в 1 клик через Shizuku**: Автоматическая выдача WRITE_SECURE_SETTINGS, активация службы спец. возможностей, отключение энергосбережения и перенаправление кнопки питания прямо из приложения — **без ПК**!
- **🔑 Полная интеграция API Shizuku**: Отображение статуса сервера и разрешения в реальном времени, встроенный запрос доступа прямо из приложения.
- **🎙️ Физический авто-клик микрофона Gemini**: Мгновенный переход Gemini в режим прослушивания голоса с аппаратным виброщелчком.
- **💡 Подсказка системных жестов OriginOS 7**: Удалены неработающие пункты меню, добавлена подробная подсказка по настройке удержания питания и тапа по спинке (Back Tap) со ссылкой в Настройки.

---

## 🇷🇺 О проекте

На смартфонах **Vivo** и **iQOO** для китайского рынка (на прошивке **OriginOS**) по умолчанию кнопка питания намертво привязана к китайскому голосовому ассистенту **Jovi** (BlueLM Copilot), а глобальный вызов Google Ассистента или Gemini заблокирован на уровне системы.

**Gemini by kaiser** — это легковесное, безопасное системное решение, которое:
1. Заменяет вызов Jovi на полноценный **голосовой оверлей Google Gemini** поверх любых запущенных приложений и игр.
2. Автоматически активирует **микрофон** и воспроизводит ответы **голосом вслух** (Auto-TTS).
3. Добавляет вызов **Circle to Search (MiCTS)** на **Двойное нажатие кнопки питания** (вместо китайского кошелька Vivo Wallet) и на двойной тап по спинке (Back Tap).
4. Оснащено благородным **тактильным виброоткликом (Haptic click)** при срабатывании всех жестов.
5. Интегрирует **«Спросить у Gemini»** в контекстное меню выделения текста и **«Отправить в Gemini»** в меню «Поделиться».
6. Включает **интерактивную панель переключателей и дашборд статуса компонентов** (Служба, MiCTS, Shizuku).
7. Сохраняет родное меню выключения / перезагрузки Vivo при повторном зажатии кнопки подряд.
8. Не требует **Root-прав**, сохраняет гарантию, банковские приложения (Mir Pay, Т-Банк, Сбер) и обновления по воздуху (OTA).

---

## ⚡ Главные возможности (v3.2)

| Функция | Описание |
|---|---|
| 🎙 **Оверлей Gemini на кнопку питания** | Удержание кнопки питания 0.5 сек открывает компактный плавающий оверлей Gemini без задержки. |
| 💳 **Двойной клик питания → Circle to Search** | Мгновенно перехватывает китайский кошелек Vivo Wallet и запускает Circle to Search (MiCTS). |
| 📳 **Тактильный виброотклик (Haptic Feedback)** | Короткий тактильный щелчок линейного вибромотора при срабатывании любого жеста. |
| 📝 **«Спросить у Gemini» при выделении текста** | Выделите текст в любом приложении — в меню появится пункт прямого запроса в Gemini. |
| 📤 **«Отправить в Gemini» через «Поделиться»** | Отправляйте фото или текст из любого приложения прямо в Gemini / Google Lens. |
| 📲 **Добавление плиток в шторку в один клик** | Добавляйте плитки Gemini и Circle to Search прямо из приложения через системное API Android 13+. |
| 📊 **Живой дашборд статуса компонентов** | Контроль активности службы спецвозможностей, Circle to Search (MiCTS) и сервера Shizuku. |
| ⚙️ **Интерактивные тумблеры настроек** | Удобное включение/отключение виброотклика, авто-озвучки TTS, жеста кошелька и меню питания. |
| 🗣 **Аппаратный голос «Hi, Jovi»** | Произнесите фразу пробуждения микрофона — откроется Google Gemini и сразу начнет слушать запрос на русском языке. |
| 🔘 **Родное меню выключения** | 2-е зажатие кнопки питания подряд открывает стандартное системное меню выключения/перезагрузки Vivo. |
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

### Отдельная версия для X200 Pro / OriginOS 6

Для **X200 Pro с OriginOS 6** доступен [отдельный вариант v3.1 со своим APK, исходниками и установщиком](variants/x200-pro-originos6/README.md). В нём доработаны вызов Gemini на заблокированном экране и автоматическая озвучка.

Готовый комплект для Windows: [ZIP для X200 Pro / OriginOS 6](Gemini_X200_Pro_OriginOS6_v3.1.zip) или [отдельная папка релиза с инструкцией](release-x200-pro-originos6/README.md). В комплект входят APK, установщик и ADB; остальные папки проекта для установки не нужны.

Исходная **v3.0**, проверявшаяся на **X500 Ultra / OriginOS 7** по информации владельца, сохранена без изменений: `app/`, `installer/Install_Gemini.bat` и APK v3.0. Инструкция ниже относится к этой исходной версии. Новый вариант не подключается к её сборке и установке автоматически.

### Способ 1: Автоустановка с ПК за 5 секунд (Рекомендуется)
1. Включите на смартфоне **Отладку по USB** (*Настройки -> Для разработчиков -> Отладка по USB*).
2. Подключите телефон к компьютеру кабелем.
3. Скачайте архив из [Releases](releases/) или папки `installer/`.
4. Запустите файл `Install_Gemini.bat`. Скрипт сам установит APK, выдаст нужные разрешения и переключит кнопку питания в OriginOS.

### Способ 2: Установка прямо на телефоне (Без ПК)
1. Установите [Gemini_by_kaiser_v3.2.apk](releases/Gemini_by_kaiser_v3.2.apk).
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
- **Circle to Search** gesture via Double Press Power button (replaces Chinese Vivo Wallet) or Double Back Tap.
- Premium **Haptic click feedback** on linear vibration motor.
- **Process Text** ("Ask Gemini") & **Share Sheet** ("Send to Gemini") system integrations.
- Quick Settings tile request & in-app component health dashboard.
- Preserves full OTA updates, Knox/TEE security, and banking apps.

---

## 📄 Лицензия

Распространяется под свободной лицензией [MIT](LICENSE). Разрешено свободное использование, модификация и распространение.

Автор: **Kaiser** ([@kaiser10669-svg](https://github.com/kaiser10669-svg) • [@Kaiser](https://youtube.com/@KaiserReichDragon))
