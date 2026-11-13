package com.aniwatch.app

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import android.view.ViewGroup

private val Ink = Color(0xFF090B12)
private val Panel = Color(0xFF151925)
private val Muted = Color(0xFF9AA3B5)
private val Accent = Color(0xFF8B5CF6)

private data class Anime(
    val id: Int,
    val title: String,
    val subtitle: String,
    val genre: String,
    val description: String,
    val colors: List<Color>
)

private val catalog = listOf(
    Anime(1, "There Was a Cute Girl in the Hero’s Party", "A confession in a fantasy world", "TV • Fantasy • Romance", "Youki is reborn in a fantasy world as an overpowered demon guardian. When the Hero’s party attacks the Demon Lord’s castle, an unexpected encounter changes his plans.", listOf(Color(0xFF164E63), Color(0xFF7C3AED))),
    Anime(2, "Hell’s Paradise Season 2", "The search for the elixir continues", "TV • Action • Dark Fantasy", "The second season of Jigokuraku continues the dangerous journey across a mysterious island.", listOf(Color(0xFF365314), Color(0xFF9A3412))),
    Anime(3, "Dead Account", "A streamer caught in a supernatural world", "TV • Action • Supernatural", "Online streamer Aoringo hides a very different life behind his provocative videos, until his world takes a dangerous turn.", listOf(Color(0xFF1E293B), Color(0xFFB91C1C))),
    Anime(4, "Frieren: Beyond Journey’s End Season 2", "The journey after the hero’s quest", "TV • Adventure • Fantasy", "Frieren and her companions continue their journey through a world shaped by memories and the passage of time.", listOf(Color(0xFF155E75), Color(0xFF4F46E5))),
    Anime(5, "Fire Force Season 3 Part 2", "The final battle draws closer", "ONA • Action • Sci-Fi", "The Special Fire Force faces the mysteries behind spontaneous human combustion and the fate of the world.", listOf(Color(0xFF7F1D1D), Color(0xFFEA580C))),
    Anime(6, "Jujutsu Kaisen: The Culling Game Part 1", "A deadly game begins", "TV • Action • Supernatural", "The next chapter brings sorcerers into a deadly conflict with rules that threaten everyone involved.", listOf(Color(0xFF312E81), Color(0xFFBE185D))),
    Anime(7, "Sentenced to Be a Hero", "A punishment with no escape", "TV • Action • Fantasy", "Convicted heroes are forced to fight in an endless war against the Demon Lords, unable to escape even through death.", listOf(Color(0xFF7C2D12), Color(0xFFB91C1C))),
    Anime(8, "Jack-of-All-Trades, Party of None", "A dismissed adventurer starts over", "TV • Adventure • Fantasy", "After being dismissed by his former party, Orn returns to the role he once mastered and begins a new adventure.", listOf(Color(0xFF14532D), Color(0xFF0E7490))),
    Anime(9, "One Punch Man 3", "The hero who ends every fight in one punch", "TV • Action • Comedy", "Saitama’s overwhelming strength continues to reshape the world of heroes and monsters.", listOf(Color(0xFF1D4ED8), Color(0xFF991B1B))),
    Anime(10, "One Piece", "The search for the ultimate treasure", "TV • Adventure • Comedy", "Monkey D. Luffy and his crew sail across the Grand Line in pursuit of the legendary One Piece.", listOf(Color(0xFF075985), Color(0xFF0F766E)))
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(9, 11, 18)
        window.navigationBarColor = android.graphics.Color.rgb(9, 11, 18)
        setContent { AniWatchApp() }
    }
}

@Composable
private fun AniWatchApp() {
    var selectedTab by remember { mutableStateOf("Home") }
    var selectedAnime by remember { mutableStateOf<Anime?>(null) }
    var search by remember { mutableStateOf("") }
    var showStreamDialog by remember { mutableStateOf(false) }
    var streamUrl by remember { mutableStateOf("") }
    var playerUrl by remember { mutableStateOf<String?>(null) }
    val watchlist = remember { mutableStateListOf<Int>() }

    BackHandler(enabled = playerUrl != null || selectedAnime != null || selectedTab != "Home") {
        when {
            playerUrl != null -> playerUrl = null
            selectedAnime != null -> selectedAnime = null
            else -> selectedTab = "Home"
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Accent, background = Ink, surface = Panel,
        onBackground = Color.White, onSurface = Color.White
    )) {
        Surface(Modifier.fillMaxSize(), color = Ink) {
            when {
                playerUrl != null -> NativePlayer(url = playerUrl!!, onBack = { playerUrl = null })
                selectedAnime != null -> DetailScreen(
                    anime = selectedAnime!!,
                    isSaved = watchlist.contains(selectedAnime!!.id),
                    onBack = { selectedAnime = null },
                    onToggleSave = {
                        if (watchlist.contains(selectedAnime!!.id)) watchlist.remove(selectedAnime!!.id)
                        else watchlist.add(selectedAnime!!.id)
                    },
                    onPlay = { showStreamDialog = true },
                    onBrowseSite = { selectedTab = "AniWatch"; selectedAnime = null }
                )
                else -> HomeScreen(
                    selectedTab = selectedTab,
                    onTab = { selectedTab = it },
                    search = search,
                    onSearch = { search = it },
                    watchlist = watchlist,
                    onSelectAnime = { selectedAnime = it }
                )
            }
        }

        if (showStreamDialog) {
            AlertDialog(
                onDismissRequest = { showStreamDialog = false },
                title = { Text("Play with a stream URL") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Paste an HTTPS direct video URL from a source you are authorized to use. HLS (.m3u8), DASH (.mpd) and MP4 are supported.", color = Muted)
                        OutlinedTextField(value = streamUrl, onValueChange = { streamUrl = it }, label = { Text("https://…") }, singleLine = true)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (streamUrl.startsWith("https://")) {
                            playerUrl = streamUrl.trim()
                            showStreamDialog = false
                        }
                    }) { Text("Play") }
                },
                dismissButton = { TextButton(onClick = { showStreamDialog = false }) { Text("Cancel") } },
                containerColor = Panel,
                titleContentColor = Color.White,
                textContentColor = Muted
            )
        }
    }
}

@Composable
private fun HomeScreen(
    selectedTab: String,
    onTab: (String) -> Unit,
    search: String,
    onSearch: (String) -> Unit,
    watchlist: List<Int>,
    onSelectAnime: (Anime) -> Unit
) {
    val context = LocalContext.current
    val filtered = when (selectedTab) {
        "My List" -> catalog.filter { watchlist.contains(it.id) }
        "Search" -> catalog.filter { it.title.contains(search, ignoreCase = true) }
        else -> catalog
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Ink),
        contentPadding = PaddingValues(bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(top = 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ANI", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                    Text("WATCH", color = Accent, fontSize = 26.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.weight(1f))
                    Text("ANIME STREAMING", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Home", "Search", "My List", "AniWatch").forEach { tab ->
                        val active = selectedTab == tab
                        Box(
                            Modifier.clip(RoundedCornerShape(30.dp))
                                .background(if (active) Accent else Panel)
                                .clickable { onTab(tab) }
                                .focusable()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) { Text(tab, color = Color.White, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, fontSize = 13.sp) }
                    }
                }
                if (selectedTab == "Search") {
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(value = search, onValueChange = onSearch, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search anime") })
                }
            }
        }
        if (selectedTab == "AniWatch") {
            item {
                Column(Modifier.padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("AniWatch catalog", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    Text("The original site is available as the catalog reference, but its changing third-party embeds are not converted into direct video streams by this app. Open the site to browse its current catalog and use a supported, authorized direct stream in the native player.", color = Muted, fontSize = 13.sp)
                    Button(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aniwatch.co.at/"))) }) { Text("Open AniWatch website") }
                }
            }
        } else if (selectedTab == "Home") {
            item {
                HeroBanner(anime = catalog.first(), onClick = { onSelectAnime(catalog.first()) })
            }
            item { SectionTitle("Trending now", "Popular picks") }
            item { AnimeRail(catalog.take(5), onSelectAnime) }
            item { SectionTitle("Discover", "More to explore") }
            item { AnimeRail(catalog.drop(3), onSelectAnime) }
        } else {
            item { SectionTitle(if (selectedTab == "My List") "My List" else "Search results", "${filtered.size} titles") }
        }
        if (selectedTab != "Home" && selectedTab != "AniWatch") {
            if (filtered.isEmpty()) {
                item { Text(if (selectedTab == "My List") "Your list is empty. Save a title to see it here." else "No matching titles.", color = Muted, modifier = Modifier.padding(horizontal = 22.dp)) }
            } else {
                items(filtered.chunked(2)) { pair ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { anime -> Box(Modifier.weight(1f)) { AnimeTile(anime, onClick = { onSelectAnime(anime) }) } }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            Text("AniWatch homepage snapshot • Refreshable catalog integration is not yet connected", color = Color(0xFF687184), fontSize = 10.sp, modifier = Modifier.padding(horizontal = 22.dp))
        }
    }
}

@Composable
private fun HeroBanner(anime: Anime, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp).heightIn(min = 210.dp, max = 300.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(anime.colors + listOf(Ink)))
            .clickable(onClick = onClick).focusable()
            .padding(22.dp), contentAlignment = Alignment.BottomStart
    ) {
        Column(Modifier.fillMaxWidth(0.85f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("FEATURED ANIME", color = Color.White.copy(alpha = .75f), fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
            Text(anime.title, color = Color.White, fontSize = 32.sp, lineHeight = 35.sp, fontWeight = FontWeight.Black)
            Text(anime.subtitle, color = Color.White.copy(alpha = .85f), fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("▶  Explore title", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = .18f)).padding(horizontal = 13.dp, vertical = 9.dp))
                Text(anime.genre, color = Color.White.copy(alpha = .8f), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(Modifier.width(9.dp))
        Text(subtitle, fontSize = 11.sp, color = Muted, modifier = Modifier.padding(bottom = 3.dp))
    }
}

@Composable
private fun AnimeRail(items: List<Anime>, onSelect: (Anime) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(items) { anime -> AnimeTile(anime, onClick = { onSelect(anime) }, modifier = Modifier.width(148.dp)) }
    }
}

@Composable
private fun AnimeTile(anime: Anime, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier = modifier.onFocusChanged { focused = it.isFocused }.focusable().clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().height(174.dp).clip(RoundedCornerShape(13.dp))
            .background(Brush.linearGradient(anime.colors))
            .then(if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(13.dp)) else Modifier),
            contentAlignment = Alignment.BottomStart) {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Text(anime.title, color = Color.White, fontSize = 17.sp, lineHeight = 19.sp, fontWeight = FontWeight.ExtraBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text("ANIME", color = Color.White.copy(alpha = .75f), fontSize = 9.sp, letterSpacing = 1.5.sp)
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(anime.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(anime.genre, color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DetailScreen(
    anime: Anime,
    isSaved: Boolean,
    onBack: () -> Unit,
    onToggleSave: () -> Unit,
    onPlay: () -> Unit,
    onBrowseSite: () -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 30.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(300.dp).background(Brush.linearGradient(anime.colors)), contentAlignment = Alignment.BottomStart) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("ANIWATCH  /  TITLE", color = Color.White.copy(alpha = .7f), fontSize = 10.sp, letterSpacing = 2.sp)
                    Text(anime.title, color = Color.White, fontSize = 36.sp, lineHeight = 38.sp, fontWeight = FontWeight.Black)
                    Text(anime.genre, color = Color.White.copy(alpha = .85f), fontSize = 13.sp)
                }
            }
        }
        item {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(anime.description, color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onPlay) { Text("▶  Play") }
                    OutlinedButton(onClick = onToggleSave) { Text(if (isSaved) "✓  In My List" else "+  My List") }
                }
                OutlinedButton(onClick = onBrowseSite) { Text("Browse AniWatch catalog") }
                Text("Playback note", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("The app uses Android's native Media3 player. It accepts direct HLS, DASH or MP4 stream URLs. Website iframe pages and protected third-party embeds are not direct stream URLs and cannot be imported automatically.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                Text("Episodes", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Episode listings will populate when a permitted catalog API or feed is connected.", color = Muted, fontSize = 12.sp)
            }
        }
    }
    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.TopStart) {
        TextButton(onClick = onBack) { Text("‹  Back", color = Color.White) }
    }
}

@Composable
private fun NativePlayer(url: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹  Back", color = Color.White) }
            Text("ANIWATCH PLAYER", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
        }
        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = { ctx -> PlayerView(ctx).apply {
                this.player = player
                useController = true
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            } },
            update = { it.player = player }
        )
    }
}

