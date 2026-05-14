package com.chiiii5640.thsrapp.core.model

enum class Station(
    val id: String,
    val code: String,
    val localName: String,
) {
    Nangang("0990", "NAK", "南港"),
    Taipei("1000", "TPE", "台北"),
    Banqiao("1010", "BAN", "板橋"),
    Taoyuan("1020", "TAO", "桃園"),
    Hsinchu("1030", "HSZ", "新竹"),
    Miaoli("1035", "MIL", "苗栗"),
    Taichung("1040", "TXG", "台中"),
    Changhua("1043", "CHA", "彰化"),
    Yunlin("1047", "YUL", "雲林"),
    Chiayi("1050", "CYI", "嘉義"),
    Tainan("1060", "TNN", "台南"),
    Zuoying("1070", "ZUY", "左營");

    val sortIndex: Int
        get() = ordinal

    companion object {
        fun fromId(id: String): Station? = entries.firstOrNull { it.id == id }
        fun fromCode(code: String): Station? = entries.firstOrNull { it.code == code }
    }
}
