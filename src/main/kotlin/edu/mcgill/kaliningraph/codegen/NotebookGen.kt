package edu.mcgill.kaliningraph.codegen

import java.io.File

val `$` = "$"

fun main(args: Array<String>) =
  """{
  "link": "https://github.com/breandan/kaliningraph",
  "description": "Graph library with a DSL for constructing graphs and visualizing the behavior of graph algorithms",
  "dependencies": [
    "com.github.breandan:kaliningraph:${args[1]}"
  ],
  "imports": [
    "edu.mcgill.kaliningraph.*",
    "edu.mcgill.kaliningraph.circuits.*",
    "org.ejml.data.*",
    "org.ejml.kotlin.*"
  ],
  "renderers": {
    "edu.mcgill.kaliningraph.LabeledGraph": "HTML(($`$`it as edu.mcgill.kaliningraph.Graph<*, *, *>).html())",
    "edu.mcgill.kaliningraph.circuits.Gate": "HTML(($`$`it as edu.mcgill.kaliningraph.circuits.Gate).graph.html())",
    "edu.mcgill.kaliningraph.circuits.NFunction": "HTML(($`$`it as edu.mcgill.kaliningraph.circuits.NFunction).graph.html())",
    "edu.mcgill.kaliningraph.circuits.ComputationGraph": "HTML(($`$`it as edu.mcgill.kaliningraph.Graph<*, *, *>).html())",
    "edu.mcgill.kaliningraph.matrix.BMat": "HTML(\"<img src=\\\"$`$`{($`$`it as edu.mcgill.kaliningraph.matrix.BMat).matToImg()}\\\"/>\")",
    "edu.mcgill.kaliningraph.matrix.BSqMat": "HTML(\"<img src=\\\"$`$`{($`$`it as edu.mcgill.kaliningraph.matrix.BSqMat).matToImg()}\\\"/>\")",
    "org.ejml.data.DMatrixSparseCSC": "HTML(\"<img src=\\\"$`$`{($`$`it as org.ejml.data.DMatrixSparseCSC).toBMat().matToImg()}\\\"/>\")"
  }
}
""".let { File("${args[0]}/kaliningraph.json").writeText(it) }