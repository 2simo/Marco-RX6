package com.marcorx6.library.ui

import android.net.Uri

/**
 * Lightweight, offline browser filtering. It deliberately blocks only known ad hosts
 * and clearly ad-related third-party endpoints, while exempting direct file URLs.
 */
internal object LocalBrowserAdBlocker {
    private val blockedAdHosts = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adservice.google.com", "googletagservices.com",
        "taboola.com", "outbrain.com", "popads.net", "popcash.net",
        "propellerads.com", "exoclick.com", "trafficjunky.com", "onclickads.net",
        "juicyads.com", "adsterra.com", "adform.net", "adnxs.com",
        "adsrvr.org", "criteo.com", "casalemedia.com", "rubiconproject.com",
        "openx.net", "pubmatic.com", "sharethrough.com", "lijit.com",
        "media.net", "revcontent.com", "mgid.com", "zemanta.com",
        "smartadserver.com", "yieldmo.com", "bidswitch.net", "advertising.com",
        "contextweb.com", "sovrn.com", "inmobi.com", "applovin.com",
        "vungle.com", "chartboost.com", "ironsrc.com", "adcolony.com",
        "adsafeprotected.com", "adroll.com", "demdex.net", "everesttech.net",
        "flashtalking.com", "mathtag.com", "moatads.com", "quantserve.com",
        "serving-sys.com", "teads.tv", "triplelift.com", "undertone.com",
        "amazon-adsystem.com", "popunder.net", "hilltopads.net", "hilltopads.com",
        "ad-maven.com", "monetag.com", "adcash.com", "yllix.com",
        "revenuehits.com", "adnow.com", "adskeeper.com", "ad-delivery.net",
        "adkernel.com", "bidvertiser.com", "clickadu.com", "clickaine.com",
        "evadav.com", "onclickmega.com", "pushground.com", "rollerads.com",
        "trafficstars.com", "zeropark.com", "plugrush.com", "popmyads.com",
        "mkfshtuxymriq.space", "rapidadexchange.com", "usrpubtrck.com"
    )

    private val adEndpointPatterns = listOf(
        Regex("""(?:^|/)(?:pagead|adsystem|adserver|adservice|adview|adclick|popunder|popup|bannerad|vast|vpaid|adrequest|adserve|adframe|adloader|adscript|adsby|adsense|prebid|gampad)(?:/|[._?=&-]|$)""", RegexOption.IGNORE_CASE),
        Regex("""[?&](?:adunit|adslot|adtag|adzone|adserver|sponsorid|advert|advertising|ad_type|ad_format|ad_id)=([^&]*)""", RegexOption.IGNORE_CASE),
        Regex("""(?:^|[./_-])(?:ads?|adserver|adservice|adnetwork|advert|sponsor|popunder|popup)(?:[./_-]|$)""", RegexOption.IGNORE_CASE)
    )

    fun shouldBlockRequest(
        url: String,
        pageUrl: String,
        enabled: Boolean,
        allowedHosts: Set<String>,
        isDirectFile: (String) -> Boolean
    ): Boolean {
        if (!enabled || isDirectFile(url)) return false
        val host = hostOf(url) ?: return false
        if (isAllowed(host, allowedHosts)) return false
        if (blockedAdHosts.any { host == it || host.endsWith(".$it") }) return true

        val pageHost = hostOf(pageUrl)
        val sameSite = pageHost != null && (host == pageHost || host.endsWith(".$pageHost") || pageHost.endsWith(".$host"))
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val target = (uri.host.orEmpty() + uri.encodedPath.orEmpty() + "?" + uri.encodedQuery.orEmpty())
        return adEndpointPatterns.any { it.containsMatchIn(target) } && !sameSite
    }

    private fun hostOf(url: String): String? =
        runCatching { Uri.parse(url).host?.lowercase() }.getOrNull()

    private fun isAllowed(host: String, allowedHosts: Set<String>): Boolean =
        allowedHosts.any { host == it || host.endsWith(".$it") }
}
