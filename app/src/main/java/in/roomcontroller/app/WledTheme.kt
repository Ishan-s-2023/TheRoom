package `in`.roomcontroller.app

import androidx.compose.ui.graphics.toArgb
import org.json.JSONObject

/** Only presentation changes. Device scripts, forms, colour canvases and endpoints stay upstream. */
internal fun wledThemeCss(palette: RoomPalette): String {
    fun hex(color: androidx.compose.ui.graphics.Color) = "#%06x".format(color.toArgb() and 0xffffff)
    val bg = hex(palette.background); val panel = hex(palette.panel); val accent = hex(palette.accent)
    val text = hex(palette.text); val muted = hex(palette.muted); val line = hex(palette.line)
    return """
        :root,body {
          --c-1:$bg!important;--c-2:$panel!important;--c-3:$line!important;--c-4:$line!important;
          --c-5:$muted!important;--c-6:$muted!important;--c-8:$muted!important;
          --c-b:$text!important;--c-c:$text!important;--c-d:$text!important;--c-e:$text!important;--c-f:$text!important;
          --c-l:$accent!important;--c-o:$panel!important;--c-tb:$panel!important;
          --c-tba:$line!important;--c-tbh:$line!important;color-scheme:${if (palette.light) "light" else "dark"};
        }
        html,body{background-color:$bg!important;color:$text!important;}
        body,input,select,textarea,button{font-family:system-ui,-apple-system,sans-serif;}
        h1,h2{font-family:Georgia,serif;font-weight:400;color:$text;}
        a{color:$accent!important;}
        .sec,.toprow{background-color:$panel!important;border-color:$line!important;}
        button,.btn{color:$text!important;border-color:$line!important;border-radius:14px!important;}
        html[data-room-settings] button,html[data-room-settings] .btn{background-color:$panel!important;}
        button.active,.btn.active{border-color:$accent!important;}
        input:not([type=range]):not([type=color]):not([type=checkbox]):not([type=radio]),select,textarea{
          background-color:$panel!important;color:$text!important;border-color:$line!important;border-radius:10px;
        }
        input[type=range],input[type=checkbox],input[type=radio]{accent-color:$accent;}
        ::selection{background:$accent;color:$bg;}
    """.trimIndent()
}

internal fun wledThemeScript(palette: RoomPalette) = """
    (function(){
      var style=document.getElementById('room-appearance');
      if(!style){style=document.createElement('style');style.id='room-appearance';(document.head||document.documentElement).appendChild(style);}
      style.textContent=${JSONObject.quote(wledThemeCss(palette))};
      document.documentElement.toggleAttribute('data-room-settings',/^\/(settings|update)/.test(location.pathname));
    })();
""".trimIndent()
