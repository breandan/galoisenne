package edu.mcgill.kaliningraph

import kweb.*

fun main() {
    Kweb(port = 16097) {
        doc.body.new {
            h1().text("Hello World!")
            button().text("Click me").on.click {
                println("Button clicked!")
            }
            input(type = InputType.text).on.keyup {
                println("Key Pressed: ${it.key}")
            }
        }
    }
}