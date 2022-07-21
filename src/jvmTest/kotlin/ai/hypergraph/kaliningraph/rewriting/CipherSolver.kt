package ai.hypergraph.kaliningraph.rewriting
import org.eclipse.collections.impl.multimap.bag.HashBagMultimap
import org.eclipse.collections.impl.multimap.list.FastListMultimap
import java.io.File

/**
 * The isogram cipher takes an isogram (a word with no repeating letters) as
 * the key, and replaces each letter of the ciphertext with the following
 * letter in the isogram. For example, the isogram "TROUBLEMAKING" maps T->R,
 * R->O, ..., N->G, G->T. ("GATE" becomes "TKRM").
 */

fun main() {
  val lines = File("src/jvmTest/resources/google-10000-english.txt").readLines()

  val patterns = FastListMultimap.newMultimap<String, String>()
  lines.forEach { patterns.put(convertWordToPattern(it), it) }

  val isogram = ('a'..'z').shuffled().joinToString("")
  val message = "meet me at secret location at noon on wednesday"
  val ciphertext = encrypt(isogram, message)
  val cipherwords = ciphertext.split(" ")

  val possibleWords = FastListMultimap.newMultimap<String, String>()
  for (word in cipherwords)
    possibleWords.getIfAbsentPutAll(word, patterns[convertWordToPattern(word)])

  crackCipher(possibleWords)

  for (word in cipherwords)
    println(possibleWords[word].toString() + " -> (" + word + ")")
}

private fun decrypt(isogram: String, ciphertext: String, shift: Int = 1) =
  encrypt(isogram.reversed(), ciphertext, shift)

private fun encrypt(isogram: String, s: String, shift: Int = 1) =
  s.map { getShiftChar(isogram, it, shift) }.joinToString("")

private fun getShiftChar(isogram: String, c: Char, shift: Int = 1) =
  isogram.indexOf(c + "")
    .let { if (0 <= it) isogram[(it + shift) % isogram.length] else c }

/**
 * The following algorithm uses two primary data structures, a dictionary of
 * word mappings, and a dictionary of possible letter mappings (initially
 * full). Considering each ciphertext word, first let's build a map of all
 * the possible English words the cipherword could represent. Ex. "eddm"
 * might map to "loop", "pool", "reek", and therefor 'e' would map to 'l',
 * 'p', 'r'. If we assume our dictionary contains a complete list of possible
 * word mappings (i.e. no plaintext word is unlisted), then in this example,
 * 'e' could *never* map to 'x'. So if we should ever see a word in our
 * dictionary containing the letter 'x' at the same index as the letter 'e'
 * in the cipherword, then we can be certain this word is not contained in
 * the plaintext. Furthermore, if we should ever encounter a plaintext word
 * in the dictionary which has a new letter in the same position as a known
 * cipherletter, we can immediately discard this word from the dictionary.
 * For each word, if the cipherletter is completely new, then we will put
 * every possible corresponding plaintext letter for this cipherword into the
 * letter map. The algorithm then proceeds to filter the word dictionary
 * using the updated letter mapping, and then update the letter map, back and
 * forth, until the dictionary stops shrinking. The resulting dictionary will
 * contain no cipherletter collisions, and if we have enough text, should
 * approximate the plaintext message.
 */

private fun crackCipher(possibleWords: FastListMultimap<String, String>) {
  var lastDictionarySize = 0
  val candidates = HashBagMultimap.newMultimap<Char, Char>()

  for (i in 'a'..'z')
    for (j in 'a'..'z')
      candidates.put(i, j)

  while (possibleWords.size() != lastDictionarySize) {
    lastDictionarySize = possibleWords.size()
    for (entry in possibleWords.keyMultiValuePairsView()) {
      val token = entry.one
      val impossibleWords = HashSet<String>()
      val seen = HashBagMultimap.newMultimap<Char, Char>()

      for (word in entry.two) {
        for (i in word.indices) {
          if (!candidates.containsKeyAndValue(token[i], word[i])) {
            impossibleWords.add(word)
            break
          }

          seen.put(token[i], word[i])
        }
      }

      // Filter letter map against all possible letter mappings
      seen.forEachKeyMultiValues { cipherLetter, newChars ->
        candidates.putAll(cipherLetter,
          candidates.removeAll(cipherLetter)
            .intersect(newChars))
      }

      // Discard all impossible words
      impossibleWords.forEach { word ->
        possibleWords.remove(token, word)

        // Try to solve for proper nouns, but let's indicate with CAPS
        if (possibleWords[token].isEmpty)
          possibleWords.put(token,
            token.map { candidates[it].first().uppercaseChar() }.joinToString(""))
      }
    }
  }
}

private fun convertWordToPattern(word: String) =
  HashMap<Char, Char>().let { charMap ->
    word.map { charMap.computeIfAbsent(it) { 'a' + charMap.size } }
  }.joinToString("").also { println(it) }