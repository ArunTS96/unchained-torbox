package com.github.livingwithhippos.unchained.utilities


val noWordDigitRegex = Regex("[^\\w\\d]+")
val ipRegex = Regex(IP_PATTERN)




/**
 * Add the http scheme to the base url if it's not already there Optionally add https No checks are
 * performed on the url validity
 */
fun addHttpScheme(baseUrl: String, setIPHttp: Boolean = true): String {
    if (baseUrl.startsWith("http", ignoreCase = true)) return baseUrl
    if (!setIPHttp) return "http://$baseUrl"
    if (ipRegex.containsMatchIn(baseUrl)) return "http://$baseUrl"
    // we suppose it's a domain
    return "https://$baseUrl"
}
