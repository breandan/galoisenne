import ai.hypergraph.kaliningraph.graphs.*
import kotlinx.browser.document
import kotlinx.html.InputType
import kotlinx.html.js.onChangeFunction
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.KeyboardEvent
import react.Props
import react.RBuilder
import react.RComponent
import react.State
import react.dom.attrs
import styled.css
import styled.styledDiv
import styled.styledInput

external interface WelcomeProps : Props {
  var name: String
}

data class WelcomeState(val name: String) : State

class Welcome(props: WelcomeProps) : RComponent<WelcomeProps, WelcomeState>(props) {
  init {
    state = WelcomeState(props.name)
  }

  override fun RBuilder.render() {
    document.onkeypress = { event: KeyboardEvent ->
      document.querySelector("svg")!!.remove()
      renderGraph(LabeledGraph { a - b - LGVertex(event.code) - a }.toDot()).then { document.body!!.append(it) }
//          test(name)
//          setState( WelcomeState(name = name) )
      setState(WelcomeState(name = event.code))
    }

    styledDiv {
      css { +WelcomeStyles.textContainer }
      +"Event: ${state.name}"
    }
    styledInput {
      css {
        +WelcomeStyles.textInput
      }
      attrs {
        type = InputType.text
        value = state.name
      }
    }
  }
}

