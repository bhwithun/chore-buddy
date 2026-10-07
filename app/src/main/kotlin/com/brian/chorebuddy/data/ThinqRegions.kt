package com.brian.chorebuddy.data

/**
 * Country code to ThinQ Connect host. US and the rest of the Americas use
 * `api-aic`. Europe and Africa use `api-eic`. Korea and much of Asia use
 * `api-kic`. The lists match LG's open thinqconnect SDK.
 */
object ThinqRegions {
    fun hostForCountry(country: String): String {
        val region = regionForCountry(country)
            ?: throw ThinqException("Country $country is not in the LG region list. US accounts use US.")
        return "https://api-$region.lgthinq.com"
    }

    fun isSupported(country: String): Boolean = regionForCountry(country) != null

    fun regionForCountry(country: String): String? {
        val code = country.trim().uppercase()
        return when {
            code in AIC -> "aic"
            code in EIC -> "eic"
            code in KIC -> "kic"
            else -> null
        }
    }

    private val KIC = setOf(
        "AU", "BD", "CN", "HK", "ID", "IN", "JP", "KH", "KR", "LA", "LK", "MM",
        "MY", "NP", "NZ", "PH", "SG", "TH", "TW", "VN",
    )

    private val AIC = setOf(
        "AG", "AR", "AW", "BB", "BO", "BR", "BS", "BZ", "CA", "CL", "CO", "CR",
        "CU", "DM", "DO", "EC", "GD", "GT", "GY", "HN", "HT", "JM", "KN", "LC",
        "MX", "NI", "PA", "PE", "PR", "PY", "SR", "SV", "TT", "US", "UY", "VC", "VE",
    )

    private val EIC = setOf(
        "AE", "AF", "AL", "AM", "AO", "AT", "AZ", "BA", "BE", "BF", "BG", "BH",
        "BJ", "BY", "CD", "CF", "CG", "CH", "CI", "CM", "CV", "CY", "CZ", "DE",
        "DJ", "DK", "DZ", "EE", "EG", "ES", "ET", "FI", "FR", "GA", "GB", "GE",
        "GH", "GM", "GN", "GQ", "GR", "HR", "HU", "IE", "IL", "IQ", "IR", "IS",
        "IT", "JO", "KE", "KG", "KW", "KZ", "LB", "LR", "LT", "LU", "LV", "LY",
        "MA", "MD", "ME", "MK", "ML", "MR", "MT", "MU", "MW", "NE", "NG", "NL",
        "NO", "OM", "PK", "PL", "PS", "PT", "QA", "RO", "RS", "RU", "RW", "SA",
        "SD", "SE", "SI", "SK", "SL", "SN", "SO", "ST", "SY", "TD", "TG", "TN",
        "TR", "TZ", "UA", "UG", "UZ", "XK", "YE", "ZA", "ZM",
    )
}
