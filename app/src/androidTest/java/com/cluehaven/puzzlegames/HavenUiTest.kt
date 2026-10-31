package com.cluehaven.puzzlegames

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cluehaven.puzzlegames.data.*
import com.cluehaven.puzzlegames.game.daily.DailyGame
import com.cluehaven.puzzlegames.ui.LocalReducedMotion
import com.cluehaven.puzzlegames.ui.daily.*
import com.cluehaven.puzzlegames.ui.theme.CluehavenTheme
import java.io.File
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HavenUiTest {
    @get:Rule val compose = createComposeRule()
    private val date = LocalDate.of(2026, 10, 31)
    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "haven-review").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun gardenShowsZeroThreeAndFiveSavedItemsInBothThemesWithReducedMotion() {
        var progress by mutableStateOf(HavenProgress(date))
        var dark by mutableStateOf(true)
        var reduced by mutableStateOf(false)
        var opened: DailyGame? = null
        compose.setContent {
            CluehavenTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalReducedMotion provides reduced) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        HavenScreen(PaddingValues(), progress) { opened = it }
                    }
                }
            }
        }
        compose.onNodeWithContentDescription(progress.sceneDescription).assertIsDisplayed()
        compose.onNodeWithText("0 of 5 grown today").assertIsDisplayed()
        capture("haven-dark-empty")
        compose.runOnIdle { progress = HavenProgress(date, havenGameOrder.take(3).toSet()) }
        compose.onNodeWithContentDescription(progress.sceneDescription).assertIsDisplayed()
        compose.onNodeWithText("3 of 5 grown today").assertIsDisplayed()
        capture("haven-dark-three")
        compose.onNodeWithContentDescription("Tile Merge, pending").performClick()
        compose.runOnIdle { assertEquals(DailyGame.TILE_MERGE, opened) }
        compose.runOnIdle { reduced = true; progress = HavenProgress(date, havenGameOrder.toSet()) }
        compose.onNodeWithText("5 of 5 grown today").assertIsDisplayed()
        havenGameOrder.forEach { compose.onNodeWithContentDescription("${it.title}, done").assertExists() }
        capture("haven-dark-complete")
        compose.runOnIdle { dark = false; progress = HavenProgress(date, havenGameOrder.take(3).toSet()) }
        compose.onNodeWithText("3 of 5 grown today").assertIsDisplayed()
        compose.onNodeWithText("Coming soon").assertExists()
        capture("haven-light-three")
    }

    @Test fun homeCardShowsSavedStatusesAndOpensDailyFlowOrAnIndividualGame() {
        val progress = HavenProgress(date, setOf(DailyGame.SUDOKU))
        var continued = false
        var opened: DailyGame? = null
        compose.setContent {
            CluehavenTheme(darkTheme = true) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.padding(16.dp)) {
                        DailyFiveHomeCard(progress, { continued = true }) { opened = it }
                    }
                }
            }
        }
        compose.onNodeWithText("1 of 5 done").assertIsDisplayed()
        compose.onNodeWithContentDescription("Sudoku, done").assertExists()
        compose.onNodeWithContentDescription("Nonogram, pending").performClick()
        compose.runOnIdle { assertEquals(DailyGame.NONOGRAM, opened) }
        compose.onNodeWithText("Continue Daily Five").performClick()
        compose.runOnIdle { assertTrue(continued) }
        capture("home-daily-card")
    }
}
