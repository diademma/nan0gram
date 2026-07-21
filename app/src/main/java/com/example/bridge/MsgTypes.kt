package com.example

object MsgTypes {
    const val TEXT = 1
    const val VOICE = 2
    const val PHOTO = 3
    const val VIDEO = 4
    const val FILE = 5
    const val REPLY = 6
    const val REACT = 7
    const val PIN = 8
    const val DELETE = 9
    const val EDIT = 10

    // Краткие имена полей для wire-формата (JS MsgFields)
    const val F_REF = "ref"
    const val F_CID = "cid"
    const val F_EMOJI = "e"
    const val F_PIN = "p"
    const val F_DUR = "dur"
    const val F_CNT = "cnt"
    const val F_NAME = "name"
    const val F_SIZE = "size"
}