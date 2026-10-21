package `in`.roomcontroller.app

import android.app.Activity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class RoomPalette(val background: Color, val panel: Color, val accent: Color, val muted: Color, val line: Color, val text: Color, val light: Boolean = false)
private val forest = RoomPalette(Color(0xFF101310),Color(0xFF1C211C),Color(0xFFB5F36A),Color(0xFFA8B1A5),Color(0xFF30392F),Color(0xFFF0F1E9))
private val midnight = RoomPalette(Color(0xFF0C1020),Color(0xFF181F35),Color(0xFFADBFFF),Color(0xFFA8B1CE),Color(0xFF303C5E),Color(0xFFF0F2FF))
private val ember = RoomPalette(Color(0xFF1B1411),Color(0xFF2A211C),Color(0xFFFFBF82),Color(0xFFC0ADA1),Color(0xFF49372C),Color(0xFFFFF1E5))
private val pearl = RoomPalette(Color(0xFFF4F5EF),Color(0xFFFFFFFF),Color(0xFF376126),Color(0xFF5D6859),Color(0xFFD8DFD0),Color(0xFF1A2517),true)
internal fun roomPalette(selected: String, systemDark: Boolean) = when(selected) {
    "midnight" -> midnight; "ember" -> ember; "pearl" -> pearl
    "system" -> if(systemDark) forest else pearl
    else -> forest
}
val LocalRoomPalette = staticCompositionLocalOf { forest }
data class AppearanceChoice(val id: String, val label: String)
val appearanceChoices = listOf(AppearanceChoice("forest","Forest"),AppearanceChoice("midnight","Midnight"),AppearanceChoice("ember","Ember"),AppearanceChoice("pearl","Pearl"),AppearanceChoice("system","System"))
data class RoomAppearance(val selected: String, val choose: (String)->Unit)
val LocalRoomAppearance = staticCompositionLocalOf { RoomAppearance("forest",{}) }
val Ink: Color @Composable get() = LocalRoomPalette.current.background
val Panel: Color @Composable get() = LocalRoomPalette.current.panel
val Lime: Color @Composable get() = LocalRoomPalette.current.accent
val Muted: Color @Composable get() = LocalRoomPalette.current.muted
val Line: Color @Composable get() = LocalRoomPalette.current.line
val Cream: Color @Composable get() = LocalRoomPalette.current.text

@Composable fun RoomTheme(content: @Composable ()->Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("room-appearance",0) }
    var selected by remember { mutableStateOf(prefs.getString("theme","forest") ?: "forest") }
    val systemDark = isSystemInDarkTheme()
    val palette = roomPalette(selected, systemDark)
    val appearance = RoomAppearance(selected) { id ->
        if (appearanceChoices.any {it.id==id}) { selected=id; prefs.edit().putString("theme",id).apply(); WledWidget.refresh(context) }
    }
    SideEffect {
        (context as? androidx.activity.ComponentActivity)?.enableEdgeToEdge(
            statusBarStyle=if(palette.light) SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT) else SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle=if(palette.light) SystemBarStyle.light(palette.background.toArgb(),palette.background.toArgb()) else SystemBarStyle.dark(palette.background.toArgb()))
    }
    val scheme = if(palette.light) lightColorScheme() else darkColorScheme()
    CompositionLocalProvider(LocalRoomPalette provides palette,LocalRoomAppearance provides appearance) {
        MaterialTheme(colorScheme=scheme.copy(primary=palette.accent,onPrimary=if(palette.light)Color.White else palette.background,
            primaryContainer=palette.accent.copy(alpha=.16f),onPrimaryContainer=palette.accent,
            secondary=palette.accent,onSecondary=palette.background,secondaryContainer=palette.accent.copy(alpha=.14f),onSecondaryContainer=palette.accent,
            background=palette.background,onBackground=palette.text,surface=palette.panel,onSurface=palette.text,
            surfaceVariant=palette.panel,onSurfaceVariant=palette.muted,outline=palette.line)) { content() }
    }
}

@Composable fun ThemeChooser() {
    val appearance = LocalRoomAppearance.current
    RoomCard {
        SectionLabel("Appearance", "THEME")
        Text("Choose a look for the whole app.",color=Muted,fontSize=12.sp)
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            appearanceChoices.forEach {choice ->
                val sample = when(choice.id) {"midnight"->midnight;"ember"->ember;"pearl"->pearl;else->forest}
                val active = appearance.selected==choice.id
                Column(Modifier.width(90.dp).clip(RoundedCornerShape(14.dp)).background(sample.panel)
                    .border(if(active)2.dp else 1.dp,if(active)Lime else Line,RoundedCornerShape(14.dp))
                    .clickable {appearance.choose(choice.id)}.padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Box(Modifier.size(24.dp).clip(CircleShape).background(sample.accent))
                        Spacer(Modifier.weight(1f))
                        if(active)Icon(Icons.Outlined.Check,"Selected ${choice.label}",Modifier.size(16.dp),tint=sample.accent)
                    }
                    Text(choice.label,color=sample.text,fontSize=12.sp)
                }
            }
        }
    }
}
