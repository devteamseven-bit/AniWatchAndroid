package com.aniwatch.app

import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

private val Bg = Color(0xFF080A10)
private val SurfaceDark = Color(0xFF171B27)
private val Muted = Color(0xFFA5ADBC)
private val Red = Color(0xFFE50914)
private val Http = OkHttpClient.Builder().callTimeout(25, TimeUnit.SECONDS).build()

private data class Anime(val id: String, val name: String, val poster: String, val description: String = "", val type: String = "", val rating: String = "", val sub: Int = 0, val dub: Int = 0)
private data class Episode(val id: String, val number: Int, val title: String, val filler: Boolean)
private data class Server(val name: String, val id: Int)
private data class StreamInfo(val url: String, val referer: String, val userAgent: String, val subtitles: List<Pair<String, String>>)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(8, 10, 16)
        window.navigationBarColor = android.graphics.Color.rgb(8, 10, 16)
        setContent { AniWatchApp() }
    }
}

private class AniApi(context: Context) {
    private val prefs = context.getSharedPreferences("aniwatch", Context.MODE_PRIVATE)
    var baseUrl: String
        get() = prefs.getString("api", "https://api-aniwatch.onrender.com") ?: "https://api-aniwatch.onrender.com"
        set(value) { prefs.edit().putString("api", value.trim().trimEnd('/')).apply() }

    private suspend fun get(path: String): JSONObject = withContext(Dispatchers.IO) {
        val base = baseUrl.trim().trimEnd('/')
        require(base.startsWith("https://")) { "Set your HTTPS API address in Settings first." }
        val request = Request.Builder().url("$base/api/v2/hianime/$path").header("Accept", "application/json").build()
        Http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("API returned HTTP ${response.code}")
            JSONObject(response.body?.string() ?: error("Empty API response"))
        }
    }
    private fun JSONObject.data() = optJSONObject("data") ?: JSONObject()
    private fun JSONArray.animes(): List<Anime> = (0 until length()).mapNotNull { i -> optJSONObject(i)?.toAnime() }
    private fun JSONObject.toAnime(): Anime? {
        val id = optString("id")
        val name = optString("name")
        if (id.isBlank() || name.isBlank()) return null
        val eps = optJSONObject("episodes") ?: JSONObject()
        return Anime(id, name, optString("poster"), optString("description"), optString("type"), optString("rating"), eps.optInt("sub"), eps.optInt("dub"))
    }
    suspend fun home(): Pair<Anime?, List<Pair<String, List<Anime>>>> {
        val d = get("home").data()
        val spotlight = d.optJSONArray("spotlightAnimes")?.animes().orEmpty()
        val sections = listOf(
            "Trending" to (d.optJSONArray("trendingAnimes")?.animes().orEmpty()),
            "Top Airing" to (d.optJSONArray("topAiringAnimes")?.animes().orEmpty()),
            "Latest Episodes" to (d.optJSONArray("latestEpisodeAnimes")?.animes().orEmpty()),
            "Most Popular" to (d.optJSONArray("mostPopularAnimes")?.animes().orEmpty()),
            "Most Favorite" to (d.optJSONArray("mostFavoriteAnimes")?.animes().orEmpty()),
            "Recently Completed" to (d.optJSONArray("latestCompletedAnimes")?.animes().orEmpty())
        ).filter { it.second.isNotEmpty() }
        return spotlight.firstOrNull() to sections
    }
    suspend fun search(q: String, page: Int = 1): List<Anime> = get("search?q=${URLEncoder.encode(q, "UTF-8")}&page=$page").data().optJSONArray("animes")?.animes().orEmpty()
    suspend fun detail(id: String): Pair<Anime?, List<Anime>> {
        val d = get("anime/${encPath(id)}").data()
        val arr = d.optJSONArray("anime")
        val first = arr?.optJSONObject(0)
        val info = first?.optJSONObject("info")
        val stats = info?.optJSONObject("stats") ?: JSONObject()
        val eps = stats.optJSONObject("episodes") ?: JSONObject()
        val main = if (info == null) null else Anime(info.optString("id", id), info.optString("name"), info.optString("poster"), info.optString("description"), stats.optString("type"), stats.optString("rating"), eps.optInt("sub"), eps.optInt("dub"))
        val recommended = d.optJSONArray("recommendedAnimes")?.animes().orEmpty()
        return main to recommended
    }
    suspend fun episodes(id: String): List<Episode> {
        val a = get("anime/${encPath(id)}/episodes").data().optJSONArray("episodes") ?: JSONArray()
        return (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { Episode(it.optString("episodeId"), it.optInt("number"), it.optString("title"), it.optBoolean("isFiller")) } }
    }
    suspend fun servers(episodeId: String, category: String): List<Server> {
        val d = get("episode/servers?animeEpisodeId=${encQuery(episodeId)}").data()
        val a = d.optJSONArray(category) ?: JSONArray()
        return (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { Server(it.optString("serverName"), it.optInt("serverId")) } }
    }
    suspend fun source(episodeId: String, server: String, category: String): StreamInfo {
        val d = get("episode/sources?animeEpisodeId=${encQuery(episodeId)}&server=${encQuery(server)}&category=$category").data()
        val source = d.optJSONArray("sources")?.optJSONObject(0) ?: error("This server did not return a playable video source.")
        val url = source.optString("url")
        require(url.startsWith("https://")) { "The selected source did not return a secure playable URL." }
        val headers = d.optJSONObject("headers") ?: JSONObject()
        val subs = d.optJSONArray("subtitles")
        val tracks = (0 until (subs?.length() ?: 0)).mapNotNull { i -> subs?.optJSONObject(i)?.let { it.optString("lang") to it.optString("url") } }.filter { it.second.startsWith("https://") }
        return StreamInfo(url, headers.optString("Referer"), headers.optString("User-Agent"), tracks)
    }
    private fun encPath(s: String) = s.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8") }
    private fun encQuery(s: String) = URLEncoder.encode(s, "UTF-8")
}

@Composable
private fun AniWatchApp() {
    val context = LocalContext.current
    val api = remember { AniApi(context) }
    val scope = rememberCoroutineScope()
    var apiBase by remember { mutableStateOf(api.baseUrl) }
    var tab by remember { mutableStateOf("Home") }
    var hero by remember { mutableStateOf<Anime?>(null) }
    var sections by remember { mutableStateOf<List<Pair<String, List<Anime>>>>(emptyList()) }
    var searchText by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Anime>>(emptyList()) }
    var selected by remember { mutableStateOf<Anime?>(null) }
    var detailAnime by remember { mutableStateOf<Anime?>(null) }
    var recommendations by remember { mutableStateOf<List<Anime>>(emptyList()) }
    var episodes by remember { mutableStateOf<List<Episode>>(emptyList()) }
    var playerInfo by remember { mutableStateOf<StreamInfo?>(null) }
    var currentEpisode by remember { mutableStateOf<Episode?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var savedItems by remember { mutableStateOf<Map<String, Anime>>(emptyMap()) }
    var queryJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun loadHome() { scope.launch { loading = true; error = ""; runCatching { api.home() }.onSuccess { hero = it.first; sections = it.second }.onFailure { error = it.message ?: "Could not load catalog" }; loading = false } }
    fun openAnime(anime: Anime) { selected = anime; detailAnime = null; episodes = emptyList(); error = ""; scope.launch { loading = true; runCatching { api.detail(anime.id) to api.episodes(anime.id) }.onSuccess { (d, e) -> detailAnime = d.first ?: anime; recommendations = d.second; episodes = e }.onFailure { error = it.message ?: "Could not load anime details" }; loading = false } }
    fun doSearch(q: String) { queryJob?.cancel(); queryJob = scope.launch { delay(350); if (q.isNotBlank()) { loading = true; runCatching { api.search(q) }.onSuccess { results = it }.onFailure { error = it.message ?: "Search failed" }; loading = false } else results = emptyList() } }
    fun playEpisode(ep: Episode) { currentEpisode = ep; error = ""; scope.launch { loading = true; runCatching { val servers = api.servers(ep.id, "sub"); val server = servers.firstOrNull() ?: error("No subtitle servers are available for this episode."); api.source(ep.id, server.name, "sub") }.onSuccess { playerInfo = it }.onFailure { error = it.message ?: "Playback source unavailable" }; loading = false } }

    BackHandler(enabled = playerInfo != null || selected != null || tab != "Home") {
        when { playerInfo != null -> playerInfo = null; selected != null -> selected = null; else -> tab = "Home" }
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = Red, background = Bg, surface = SurfaceDark, onBackground = Color.White, onSurface = Color.White)) {
        Surface(Modifier.fillMaxSize(), color = Bg) {
            when {
                playerInfo != null -> NativePlayer(playerInfo!!, currentEpisode?.title ?: "Now Playing", onBack = { playerInfo = null })
                selected != null -> DetailPage(
                    anime = detailAnime ?: selected!!, episodes = episodes, loading = loading, error = error,
                    isSaved = savedItems.containsKey(selected!!.id), onBack = { selected = null },
                    onSave = { savedItems = if (savedItems.containsKey(selected!!.id)) savedItems - selected!!.id else savedItems + (selected!!.id to (detailAnime ?: selected!!)) },
                    onPlay = { ep -> playEpisode(ep) }, onAnime = { openAnime(it) }
                )
                else -> MainPage(
                    tab = tab, apiBase = apiBase, onApiBase = { apiBase = it }, onSaveApi = { api.baseUrl = apiBase; loadHome() },
                    hero = hero, sections = sections, searchText = searchText, onSearchText = { searchText = it; doSearch(it) },
                    results = results, savedItems = savedItems.values.toList(), loading = loading, error = error,
                    onTab = { tab = it }, onAnime = { openAnime(it) }, onRefresh = { loadHome() }
                )
            }
        }
    }
    LaunchedEffect(Unit) { loadHome() }
}

@Composable
private fun MainPage(tab: String, apiBase: String, onApiBase: (String) -> Unit, onSaveApi: () -> Unit, hero: Anime?, sections: List<Pair<String, List<Anime>>>, searchText: String, onSearchText: (String) -> Unit, results: List<Anime>, savedItems: List<Anime>, loading: Boolean, error: String, onTab: (String) -> Unit, onAnime: (Anime) -> Unit, onRefresh: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 36.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(top = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ANI", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                    Text("WATCH", color = Red, fontSize = 26.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.weight(1f)); Text("ANIME", color = Muted, fontSize = 10.sp, letterSpacing = 3.sp)
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Home", "Search", "My List", "Settings").forEach { name ->
                        val icon = when (name) { "Home" -> Icons.Default.Home; "Search" -> Icons.Default.Search; "My List" -> Icons.Default.Bookmark; else -> Icons.Default.Settings }
                        NavigationChip(name, tab == name, icon) { onTab(name) }
                    }
                }
            }
        }
        when (tab) {
            "Settings" -> {
                item { Column(Modifier.padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Catalog connection", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text("Enter the HTTPS address of your own deployed AniWatch-compatible API. Do not add /api/v2/hianime; the app adds that path automatically.", color = Muted, fontSize = 13.sp)
                    OutlinedTextField(value = apiBase, onValueChange = onApiBase, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("https://your-api-host") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                    Button(onClick = onSaveApi, modifier = Modifier.fillMaxWidth()) { Text("Save and connect") }
                    Text("The API server is separate from the Android app. The project README explains how to deploy it.", color = Muted, fontSize = 12.sp)
                } }
            }
            "Search" -> {
                item { Column(Modifier.padding(horizontal = 22.dp)) { Text("Find anime", fontSize = 24.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(10.dp)); OutlinedTextField(value = searchText, onValueChange = onSearchText, modifier = Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("Search titles") }) } }
                if (results.isNotEmpty()) item { AnimeGrid(results, onAnime) }
            }
            "My List" -> {
                val list = savedItems
                item { Text("My List", Modifier.padding(horizontal = 22.dp), fontSize = 24.sp, fontWeight = FontWeight.Bold) }
                if (list.isNotEmpty()) item { AnimeGrid(list, onAnime) } else item { Text("Save anime from its detail page to see it here.", Modifier.padding(horizontal = 22.dp), color = Muted) }
            }
            else -> {
                if (hero != null) item { HeroCard(hero, onAnime) }
                sections.forEach { (title, list) -> item { AnimeRail(title, list, onAnime) } }
                if (hero == null && sections.isEmpty()) item { Text("Connect an API in Settings to load live anime artwork, search and episodes.", Modifier.padding(horizontal = 22.dp), color = Muted) }
            }
        }
        if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 22.dp), color = Red) }
        if (error.isNotBlank()) item { Text(error, Modifier.padding(horizontal = 22.dp), color = Color(0xFFFF8A80), fontSize = 13.sp) }
        if (tab == "Home" && sections.isNotEmpty()) item { Text("Catalog provided by your configured API", Modifier.padding(horizontal = 22.dp), color = Muted, fontSize = 10.sp) }
    }
}

@Composable
private fun NavigationChip(label: String, active: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(Modifier.clip(RoundedCornerShape(24.dp)).background(if (active) Red else SurfaceDark).onFocusChanged { focused = it.isFocused }.clickable { onClick() }.focusable().padding(horizontal = 13.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(16.dp)); Text(label, color = Color.White, fontSize = 12.sp, fontWeight = if (active || focused) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun HeroCard(anime: Anime, onClick: (Anime) -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(310.dp).clip(RoundedCornerShape(20.dp)).clickable { onClick(anime) }) {
        if (anime.poster.isNotBlank()) AsyncImage(anime.poster, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE080A10)))))
        Column(Modifier.align(Alignment.BottomStart).padding(22.dp)) {
            Text("FEATURED ANIME", color = Red, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text(anime.name, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (anime.description.isNotBlank()) Text(anime.description, color = Color.White.copy(alpha = .82f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp)); Button(onClick = { onClick(anime) }, colors = ButtonDefaults.buttonColors(containerColor = Red)) { Icon(Icons.Default.PlayArrow, null); Text("Explore") }
        }
    }
}

@Composable
private fun AnimeRail(title: String, list: List<Anime>, onAnime: (Anime) -> Unit) {
    if (list.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, Modifier.padding(horizontal = 22.dp), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        LazyRow(contentPadding = PaddingValues(horizontal = 22.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(list, key = { it.id }) { AnimePoster(it, onAnime) } }
    }
}

@Composable
private fun AnimePoster(anime: Anime, onAnime: (Anime) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(132.dp).onFocusChanged { focused = it.isFocused }.clip(RoundedCornerShape(10.dp)).background(if (focused) SurfaceDark else Color.Transparent).clickable { onAnime(anime) }.focusable()) {
        Box(Modifier.fillMaxWidth().height(184.dp).clip(RoundedCornerShape(10.dp)).background(SurfaceDark)) {
            if (anime.poster.isNotBlank()) AsyncImage(anime.poster, anime.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (focused) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = .08f)))
        }
        Text(anime.name, Modifier.padding(top = 6.dp, start = 3.dp, end = 3.dp), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(listOf(anime.type, anime.rating).filter { it.isNotBlank() }.joinToString(" • "), Modifier.padding(start = 3.dp, end = 3.dp, bottom = 5.dp), color = Muted, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun AnimeGrid(list: List<Anime>, onAnime: (Anime) -> Unit) {
    Column(Modifier.padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        list.chunked(3).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { row.forEach { Box(Modifier.weight(1f)) { AnimePoster(it, onAnime) } }; repeat(3 - row.size) { Spacer(Modifier.weight(1f)) } } }
    }
}

@Composable
private fun DetailPage(anime: Anime, episodes: List<Episode>, loading: Boolean, error: String, isSaved: Boolean, onBack: () -> Unit, onSave: () -> Unit, onPlay: (Episode) -> Unit, onAnime: (Anime) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(320.dp).background(SurfaceDark)) {
                if (anime.poster.isNotBlank()) AsyncImage(anime.poster, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x22000000), Bg))))
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(12.dp).background(Color.Black.copy(alpha = .5f), RoundedCornerShape(50))) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
                Column(Modifier.align(Alignment.BottomStart).padding(22.dp)) {
                    Text(anime.name, color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(listOf(anime.type, anime.rating, if (anime.sub > 0) "${anime.sub} SUB" else "").filter { it.isNotBlank() }.joinToString(" • "), color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp)); Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { episodes.firstOrNull()?.let(onPlay) }, colors = ButtonDefaults.buttonColors(containerColor = Red), enabled = episodes.isNotEmpty()) { Icon(Icons.Default.PlayArrow, null); Text("Play") }
                        OutlinedButton(onClick = onSave) { Icon(Icons.Default.Bookmark, null); Text(if (isSaved) "Saved" else "My List") }
                    }
                }
            }
        }
        if (anime.description.isNotBlank()) item { Text(anime.description, Modifier.padding(horizontal = 22.dp, vertical = 16.dp), color = Muted, fontSize = 14.sp, lineHeight = 20.sp) }
        item { Text("Episodes", Modifier.padding(horizontal = 22.dp, vertical = 8.dp), color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold) }
        if (episodes.isEmpty() && loading) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 22.dp), color = Red) }
        items(episodes, key = { it.id }) { ep ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 5.dp).clip(RoundedCornerShape(10.dp)).background(SurfaceDark).clickable { onPlay(ep) }.focusable().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(88.dp, 56.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xFF292F3D)), contentAlignment = Alignment.Center) { Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
                Column(Modifier.weight(1f).padding(start = 12.dp)) { Text("Episode ${ep.number}", color = Color.White, fontWeight = FontWeight.Bold); Text(ep.title.ifBlank { "Episode ${ep.number}" }, color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis); if (ep.filler) Text("Filler", color = Red, fontSize = 10.sp) }
                Icon(Icons.Default.PlayArrow, null, tint = Red)
            }
        }
        if (error.isNotBlank()) item { Text(error, Modifier.padding(22.dp), color = Color(0xFFFF8A80)) }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun NativePlayer(info: StreamInfo, title: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val player = remember(info.url) {
        val props = mutableMapOf<String, String>()
        if (info.referer.isNotBlank()) props["Referer"] = info.referer
        if (info.userAgent.isNotBlank()) props["User-Agent"] = info.userAgent
        val httpFactory = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true).setDefaultRequestProperties(props)
        val mediaItem = MediaItem.Builder().setUri(info.url).setMimeType(MimeTypes.APPLICATION_M3U8).apply {
            val subtitle = info.subtitles.firstOrNull()
            if (subtitle != null) setSubtitleConfigurations(listOf(MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(subtitle.second)).setMimeType(MimeTypes.TEXT_VTT).setLanguage(subtitle.first).setLabel(subtitle.first).build()))
        }.build()
        ExoPlayer.Builder(context).build().apply { setMediaSource(HlsMediaSource.Factory(httpFactory).createMediaSource(mediaItem)); prepare(); playWhenReady = true }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxWidth().background(Color(0xFF111111)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }; Text(title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        AndroidView(factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = true; layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) } }, modifier = Modifier.fillMaxWidth().weight(1f))
    }
}
