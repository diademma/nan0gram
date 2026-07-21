package com.example

/**
 * MsgTypes — единственная точка правды для всех типов сообщений на Kotlin-стороне.
 * Зеркало: W.MSG и W.MSG_F в cipher.js (JS-слой).
 * ПРАВИЛО: изменения здесь → синхронно в cipher.js, и наоборот.
 *
 * t:1–5  — контентные блоки
 * t:6    — модификатор reply (только с TEXT или VOICE)
 * t:7–10 — действия
 */
object MsgTypes {
    // ── Контентные блоки ──────────────────────────────────────────────────
    const val TEXT   = 1   // Текст — в envelope.text
    const val VOICE  = 2   // Голосовое — вложением, поле dur: секунды
    const val PHOTO  = 3   // Фото — вложениями, поле cnt: количество
    const val VIDEO  = 4   // Видео — вложением
    const val FILE   = 5   // Файл — вложением, поля name и size

    // ── Модификатор ───────────────────────────────────────────────────────
    const val REPLY  = 6   // Ответ: только с TEXT (1) или VOICE (2)

    // ── Действия ──────────────────────────────────────────────────────────
    const val REACT  = 7   // Реакция: e — эмодзи; e="" = убрать
    const val PIN    = 8   // Пин: p=1 закрепить, p=0 открепить
    const val DELETE = 9   // Удалить сообщение
    const val EDIT   = 10  // Редактировать: новый текст в envelope.text

    // ── Краткие имена полей wire-блоков ───────────────────────────────────
    const val F_REF   = "ref"
    const val F_EMOJI = "e"
    const val F_PIN   = "p"
    const val F_DUR   = "dur"
    const val F_CNT   = "cnt"
    const val F_NAME  = "name"
    const val F_SIZE  = "size"

    // ── Версия wire-схемы ─────────────────────────────────────────────────
    const val SCHEMA_VER = 1

    // ── Направление в БД (НЕ wire-типы) ──────────────────────────────────
    const val DIR_IN  = "in"
    const val DIR_OUT = "out"

    // ── mediaType в БД (строки только внутри DB, числа — только в wire) ──
    const val MEDIA_NONE  = "none"
    const val MEDIA_PHOTO = "photo"
    const val MEDIA_VOICE = "voice"
    const val MEDIA_VIDEO = "video"
    const val MEDIA_FILE  = "file"

    /** wire-тип контента (1–5) → строка mediaType для БД. */
    fun toMediaString(wireType: Int): String = when (wireType) {
        VOICE -> MEDIA_VOICE
        PHOTO -> MEDIA_PHOTO
        VIDEO -> MEDIA_VIDEO
        FILE  -> MEDIA_FILE
        else  -> MEDIA_NONE
    }
}