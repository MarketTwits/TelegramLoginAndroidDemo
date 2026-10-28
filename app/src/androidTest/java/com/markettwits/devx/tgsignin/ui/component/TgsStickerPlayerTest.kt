package com.markettwits.devx.tgsignin.ui.component

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import com.markettwits.devx.tgsignin.data.model.ProfileEmoji
import com.markettwits.devx.tgsignin.data.model.ProfileEmojiCatalog
import com.markettwits.devx.tgsignin.data.model.ProfileEmojiSelection
import com.markettwits.devx.tgsignin.data.repository.ProfileEmojiRepository
import com.markettwits.devx.tgsignin.ui.component.tgs.TgsDecoder
import com.markettwits.devx.tgsignin.ui.component.tgs.TgsFrameBaker
import com.markettwits.devx.tgsignin.ui.component.tgs.TgsStickerPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream

class TgsStickerPlayerTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // Real Telegram TGS JSON from animated-icons pack
    private val realTelegramTgsJson = """
    {"v":"5.6.6","ip":0,"op":160,"fr":60,"w":512,"h":512,"layers":[{"ind":3272,"nm":"surface22463","ao":0,"ip":0,"op":264,"st":0,"ty":4,"ks":{"ty":"tr","o":{"k":100},"r":{"k":0},"p":{"k":[0,0]},"a":{"k":[0,0]},"s":{"k":[306.1208968609866,304.75428571428574]},"sk":{"k":0},"sa":{"k":0}},"shapes":[{"ty":"gr","hd":false,"nm":"surface22463","it":[{"ty":"gr","hd":false,"it":[{"ty":"sh","ks":{"k":{"i":[[0,0],[0,0],[0,0],[0,0]],"o":[[0,0],[0,0],[0,0],[0,0]],"v":[[0.11,167.29],[0.13,167.29],[0.13,167.31],[0.11,167.31]],"c":true}}},{"ty":"fl","o":{"a":1,"k":[{"i":{"x":[0.67],"y":[1]},"o":{"x":[0.33],"y":[0]},"t":59,"s":[0]},{"t":109,"s":[1]}],"ix":5},"c":{"k":[0,0,0,0]}},{"ty":"tr","o":{"k":100},"r":{"k":0},"p":{"k":[0,0]},"a":{"k":[0,0]},"s":{"k":[100,100]},"sk":{"k":0},"sa":{"k":0},"hd":false}]},{"ty":"tr","o":{"k":100},"r":{"k":0},"p":{"k":[0,0]},"a":{"k":[0,0]},"s":{"k":[100,100]},"sk":{"k":0},"sa":{"k":0},"hd":false}]},{"ty":"tm","s":{"a":0,"k":0,"ix":1},"e":{"a":1,"k":[{"i":{"x":[0.27],"y":[1]},"o":{"x":[0.5],"y":[0]},"t":0,"s":[0]},{"t":66,"s":[100]}],"ix":2},"o":{"a":0,"k":0,"ix":3},"m":1,"ix":2,"hd":false}]}],"meta":{"g":"LF SVG to Lottie"},"tgs":1}
    """.trimIndent()

    @Before
    @After
    fun cleanUp() {
        TgsFrameBaker.clearCache()
        TgsDecoder.clearCache()
    }

    @Test
    fun frameBakerPreRendersBitmapsAndPlayerRendersInCompose() {
        runBlocking {
            val cacheKey = "test_real_telegram_sticker"
            val baked = TgsFrameBaker.bake(
                tgsJson = realTelegramTgsJson,
                cacheKey = cacheKey,
                widthPx = 260,
                heightPx = 260,
                targetFps = 30
            )

            assertNotNull("Baking should produce BakedTgsAnimation", baked)
            assertTrue("Baked frames should be positive", baked!!.frames.isNotEmpty())
            assertTrue("Duration should be positive", baked.durationMs > 0L)
            assertEquals(260, baked.frames.first().width)
            assertEquals(260, baked.frames.first().height)

            // Verify LRU cache hit
            val cached = TgsFrameBaker.getCachedFrames(cacheKey)
            assertNotNull("Should be retrieved from LRU cache", cached)
            assertEquals(baked.frames.size, cached!!.frames.size)
            assertTrue("Cache size should be positive", TgsFrameBaker.currentCacheBytes() > 0)

            // Disable autoAdvance for continuous withFrameNanos animation
            composeRule.mainClock.autoAdvance = false

            // Render with TgsStickerPlayer in Compose
            composeRule.setContent {
                TgsStickerPlayer(
                    bakedAnimation = baked,
                    contentDescription = "Test Animated Sticker",
                    modifier = Modifier.size(64.dp)
                )
            }

            composeRule.mainClock.advanceTimeBy(100L)
            composeRule.onNodeWithContentDescription("Test Animated Sticker").assertExists()
        }
    }

    @Test
    fun profileEmojiImageUpdatesWhenEmojiChanges() {
        val context = composeRule.activity
        fun gzip(data: ByteArray): ByteArray {
            val bos = ByteArrayOutputStream()
            GZIPOutputStream(bos).use { it.write(data) }
            return bos.toByteArray()
        }

        val jsonBytes = realTelegramTgsJson.toByteArray(Charsets.UTF_8)
        val gzipped = gzip(jsonBytes)

        val file1 = File.createTempFile("emoji1_", ".tgs", context.cacheDir)
        file1.writeBytes(gzipped)
        val file2 = File.createTempFile("emoji2_", ".tgs", context.cacheDir)
        file2.writeBytes(gzipped)

        val emoji1 = ProfileEmoji(
            setId = "set1",
            id = "emoji_1",
            name = "Sticker One",
            keywords = emptyList(),
            assetPath = "emoji1.tgs",
            sha256 = "sha_111111",
            sizeBytes = gzipped.size,
            width = 512,
            height = 512,
            framesPerSecond = 60f,
            durationMs = 2666,
            enabled = true
        )

        val emoji2 = ProfileEmoji(
            setId = "set1",
            id = "emoji_2",
            name = "Sticker Two",
            keywords = emptyList(),
            assetPath = "emoji2.tgs",
            sha256 = "sha_222222",
            sizeBytes = gzipped.size,
            width = 512,
            height = 512,
            framesPerSecond = 60f,
            durationMs = 2666,
            enabled = true
        )

        val fakeRepo = object : ProfileEmojiRepository {
            override val catalog = MutableStateFlow<ProfileEmojiCatalog?>(null)
            override val recentSelections = MutableStateFlow<List<ProfileEmojiSelection>>(emptyList())
            override suspend fun refresh() = Result.success(Unit)
            override suspend fun loadAnimationFile(emoji: ProfileEmoji): File {
                return if (emoji.id == "emoji_1") file1 else file2
            }
            override suspend fun prefetch(emojis: List<ProfileEmoji>) {}
            override fun recordRecent(selection: ProfileEmojiSelection) {}
        }

        val currentEmoji = mutableStateOf<ProfileEmoji?>(emoji1)

        composeRule.mainClock.autoAdvance = false

        composeRule.setContent {
            val emoji by currentEmoji
            ProfileEmojiImage(
                emoji = emoji,
                contentDescription = emoji?.name,
                repository = fakeRepo,
                modifier = Modifier.size(64.dp)
            )
        }

        val expectedKey1 = "emoji:${emoji1.sha256}:${TgsFrameBaker.DEFAULT_FRAME_WIDTH_PX}x${TgsFrameBaker.DEFAULT_FRAME_HEIGHT_PX}:${TgsFrameBaker.DEFAULT_TARGET_FPS}fps"
        var attempts = 0
        while (TgsFrameBaker.getCachedFrames(expectedKey1) == null && attempts < 50) {
            composeRule.mainClock.advanceTimeBy(100L)
            Thread.sleep(50L)
            attempts++
        }
        assertNotNull("Emoji 1 should be cached", TgsFrameBaker.getCachedFrames(expectedKey1))
        composeRule.mainClock.advanceTimeBy(100L)
        composeRule.onNodeWithContentDescription("Sticker One").assertExists()

        // Switch to emoji2
        currentEmoji.value = emoji2
        val expectedKey2 = "emoji:${emoji2.sha256}:${TgsFrameBaker.DEFAULT_FRAME_WIDTH_PX}x${TgsFrameBaker.DEFAULT_FRAME_HEIGHT_PX}:${TgsFrameBaker.DEFAULT_TARGET_FPS}fps"
        attempts = 0
        while (TgsFrameBaker.getCachedFrames(expectedKey2) == null && attempts < 50) {
            composeRule.mainClock.advanceTimeBy(100L)
            Thread.sleep(50L)
            attempts++
        }
        assertNotNull("Emoji 2 should be cached", TgsFrameBaker.getCachedFrames(expectedKey2))
        composeRule.mainClock.advanceTimeBy(100L)
        composeRule.onNodeWithContentDescription("Sticker Two").assertExists()

        file1.delete()
        file2.delete()
    }
}
