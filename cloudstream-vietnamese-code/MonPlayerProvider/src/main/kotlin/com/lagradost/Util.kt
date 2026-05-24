package com.anhdaden

fun String.removeVietnameseAccents(): String {
    val vietnameseAccents = mapOf(
        "àáạảãâầấậẩẫăằắặẳẵ" to "a",
        "èéẹẻẽêềếệểễ" to "e",
        "ìíịỉĩ" to "i",
        "òóọỏõôồốộổỗơờớợởỡ" to "o",
        "ùúụủũưừứựửữ" to "u",
        "ỳýỵỷỹ" to "y",
        "đ" to "d"
    )
    var result = this.lowercase()
    for ((key, value) in vietnameseAccents) {
        result = result.replace(Regex("[$key]"), value)
    }
 
    return result
}