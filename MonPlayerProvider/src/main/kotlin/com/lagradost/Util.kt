package com.anhdaden

fun String.removerAcentos(): String {
    val mapaAcentos = mapOf(
        "áàäâ" to "a",
        "éèëê" to "e",
        "íìïî" to "i",
        "óòöô" to "o",
        "úùüû" to "u",
        "ñ" to "n"
    )
    
    var resultado = this.lowercase()
    for ((key, value) in mapaAcentos) {
        resultado = resultado.replace(Regex("[$key]"), value)
    }
 
    return resultado
}
