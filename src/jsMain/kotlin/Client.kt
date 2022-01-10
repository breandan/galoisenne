import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import kotlin.js.Promise

// ./gradlew jsBrowserRun --continuous
@OptIn(ExperimentalStdlibApi::class)
fun main() {
  window.onload = {
//    prefAttach()
//    rewriter()
    cfgParser()
  }
}

external class Viz {
  fun renderSVGElement(p: String): Promise<HTMLElement>
}