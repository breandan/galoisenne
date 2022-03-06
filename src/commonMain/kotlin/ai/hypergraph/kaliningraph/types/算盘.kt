@file:Suppress("unused", "ObjectPropertyName", "ClassName", "PropertyName", "NonAsciiCharacters", "FunctionName")

package ai.hypergraph.kaliningraph.types

import kotlin.jvm.JvmName

sealed class 数<丁, 己: 数<丁, 己>>(open val 中: 丁? = null, open val 码: String = "") {
  val 零 get() = 零(this as 己)
  val 一 get() = 一(this as 己)
  val 二 get() = 二(this as 己)
  val 三 get() = 三(this as 己)
  val 四 get() = 四(this as 己)
  val 五 get() = 五(this as 己)
  val 六 get() = 六(this as 己)
  val 七 get() = 七(this as 己)
  val 八 get() = 八(this as 己)
  val 九 get() = 九(this as 己)

  override fun equals(other: Any?) = toString() == other.toString()
  override fun hashCode() = this::class.hashCode() + 中.hashCode()
  override fun toString() = if (this is 未) i.toString().toChinese() else (中 ?: "").toString() + 码
  fun toInt() = toString().toArabic().toInt()
}

open class 零<丁>(override val 中: 丁? = null, override val 码: String = "零") : 数<丁, 零<丁>>(中) { companion object: 零<无>() }
open class 一<丁>(override val 中: 丁? = null, override val 码: String = "一") : 数<丁, 一<丁>>(中) { companion object: 一<无>() }
open class 二<丁>(override val 中: 丁? = null, override val 码: String = "二") : 数<丁, 二<丁>>(中) { companion object: 二<无>() }
open class 三<丁>(override val 中: 丁? = null, override val 码: String = "三") : 数<丁, 三<丁>>(中) { companion object: 三<无>() }
open class 四<丁>(override val 中: 丁? = null, override val 码: String = "四") : 数<丁, 四<丁>>(中) { companion object: 四<无>() }
open class 五<丁>(override val 中: 丁? = null, override val 码: String = "五") : 数<丁, 五<丁>>(中) { companion object: 五<无>() }
open class 六<丁>(override val 中: 丁? = null, override val 码: String = "六") : 数<丁, 六<丁>>(中) { companion object: 六<无>() }
open class 七<丁>(override val 中: 丁? = null, override val 码: String = "七") : 数<丁, 七<丁>>(中) { companion object: 七<无>() }
open class 八<丁>(override val 中: 丁? = null, override val 码: String = "八") : 数<丁, 八<丁>>(中) { companion object: 八<无>() }
open class 九<丁>(override val 中: 丁? = null, override val 码: String = "九") : 数<丁, 九<丁>>(中) { companion object: 九<无>() }

object 无: 数<无, 无>(null)
open class 未(val i: Int): 数<未, 未>(null)

val 十: 十型 = 一.零
val 十一: 十一型 = 一.一
val 十二: 十二型 = 一.二
val 十三: 十三型 = 一.三
val 十四: 十四型 = 一.四
val 十五: 十五型 = 一.五
val 十六: 十六型 = 一.六
val 十七: 十七型 = 一.七
val 十八: 十八型 = 一.八
val 十九: 十九型 = 一.九
val 二十: 二十型 = 二.零
val 二十一: 二十一型 = 二.一
typealias 一型 = 一<无>
typealias 二型 = 二<无>
typealias 三型 = 三<无>
typealias 四型 = 四<无>
typealias 五型 = 五<无>
typealias 六型 = 六<无>
typealias 七型 = 七<无>
typealias 八型 = 八<无>
typealias 九型 = 九<无>
typealias 十型 = 零<一<无>>
typealias 十一型 = 一<一<无>>
typealias 十二型 = 二<一<无>>
typealias 十三型 = 三<一<无>>
typealias 十四型 = 四<一<无>>
typealias 十五型 = 五<一<无>>
typealias 十六型 = 六<一<无>>
typealias 十七型 = 七<一<无>>
typealias 十八型 = 八<一<无>>
typealias 十九型 = 九<一<无>>
typealias 二十型 = 零<二<无>>
typealias 二十一型 = 一<二<无>>

val z2a: Map<String, String> = mapOf(
  "零" to "0",
  "一" to "1",
  "二" to "2",
  "三" to "3",
  "四" to "4",
  "五" to "5",
  "六" to "6",
  "七" to "7",
  "八" to "8",
  "九" to "9",
  "十" to "",
  "百" to "",
)

val a2z: Map<String, String> = z2a.entries.associate { (k, v) -> v to k }

// TODO: https://cs.github.com/?scopeName=All+repos&scope=&q=%E9%9B%B6+%E4%B8%80+%E4%BA%8C+%E4%B8%89+%E5%9B%9B+%E4%BA%94+%E5%85%AD+%E4%B8%83+%E5%85%AB+%E4%B9%9D+Arabic++language%3AJava
fun String.toArabic() = map { it.toString() }.joinToString("") { if (it in z2a) z2a[it]!! else it }
fun String.toChinese() = map { it.toString() }.joinToString("") { if (it in a2z) a2z[it]!! else it }

@JvmName("口零加一") infix fun <丁> 零<丁>.加(甲: 一<无>): 一<丁> = 一(中)
@JvmName("口一加一") infix fun <丁> 一<丁>.加(甲: 一<无>): 二<丁> = 二(中)
@JvmName("口二加一") infix fun <丁> 二<丁>.加(甲: 一<无>): 三<丁> = 三(中)
@JvmName("口三加一") infix fun <丁> 三<丁>.加(甲: 一<无>): 四<丁> = 四(中)
@JvmName("口四加一") infix fun <丁> 四<丁>.加(甲: 一<无>): 五<丁> = 五(中)
@JvmName("口五加一") infix fun <丁> 五<丁>.加(甲: 一<无>): 六<丁> = 六(中)
@JvmName("口六加一") infix fun <丁> 六<丁>.加(甲: 一<无>): 七<丁> = 七(中)
@JvmName("口七加一") infix fun <丁> 七<丁>.加(甲: 一<无>): 八<丁> = 八(中)
@JvmName("口八加一") infix fun <丁> 八<丁>.加(甲: 一<无>): 九<丁> = 九(中)
@JvmName("九加一") infix fun 九<无>.加(甲: 一<无>): 零<一<无>> = 零(一())
@JvmName("口零十九加一") infix fun <丁> 九<零<丁>>.加(甲: 一<无>): 零<一<丁>> = 零(一(中!!.中))
@JvmName("口一十九加一") infix fun <丁> 九<一<丁>>.加(甲: 一<无>): 零<二<丁>> = 零(二(中!!.中))
@JvmName("口二十九加一") infix fun <丁> 九<二<丁>>.加(甲: 一<无>): 零<三<丁>> = 零(三(中!!.中))
@JvmName("口三十九加一") infix fun <丁> 九<三<丁>>.加(甲: 一<无>): 零<四<丁>> = 零(四(中!!.中))
@JvmName("口四十九加一") infix fun <丁> 九<四<丁>>.加(甲: 一<无>): 零<五<丁>> = 零(五(中!!.中))
@JvmName("口五十九加一") infix fun <丁> 九<五<丁>>.加(甲: 一<无>): 零<六<丁>> = 零(六(中!!.中))
@JvmName("口六十九加一") infix fun <丁> 九<六<丁>>.加(甲: 一<无>): 零<七<丁>> = 零(七(中!!.中))
@JvmName("口七十九加一") infix fun <丁> 九<七<丁>>.加(甲: 一<无>): 零<八<丁>> = 零(八(中!!.中))
@JvmName("口八十九加一") infix fun <丁> 九<八<丁>>.加(甲: 一<无>): 零<九<丁>> = 零(九(中!!.中))
@JvmName("九十九加一") infix fun 九<九<无>>.加(甲: 一<无>): 零<零<一<无>>> = 零(零(一()))
@JvmName("口零百九十九加一") infix fun <丁> 九<九<零<丁>>>.加(甲: 一<无>): 零<零<一<丁>>> = 零(零(一(中!!.中!!.中)))
@JvmName("口一百九十九加一") infix fun <丁> 九<九<一<丁>>>.加(甲: 一<无>): 零<零<二<丁>>> = 零(零(二(中!!.中!!.中)))
@JvmName("口二百九十九加一") infix fun <丁> 九<九<二<丁>>>.加(甲: 一<无>): 零<零<三<丁>>> = 零(零(三(中!!.中!!.中)))
@JvmName("口三百九十九加一") infix fun <丁> 九<九<三<丁>>>.加(甲: 一<无>): 零<零<四<丁>>> = 零(零(四(中!!.中!!.中)))
@JvmName("口四百九十九加一") infix fun <丁> 九<九<四<丁>>>.加(甲: 一<无>): 零<零<五<丁>>> = 零(零(五(中!!.中!!.中)))
@JvmName("口五百九十九加一") infix fun <丁> 九<九<五<丁>>>.加(甲: 一<无>): 零<零<六<丁>>> = 零(零(六(中!!.中!!.中)))
@JvmName("口六百九十九加一") infix fun <丁> 九<九<六<丁>>>.加(甲: 一<无>): 零<零<七<丁>>> = 零(零(七(中!!.中!!.中)))
@JvmName("口七百九十九加一") infix fun <丁> 九<九<七<丁>>>.加(甲: 一<无>): 零<零<八<丁>>> = 零(零(八(中!!.中!!.中)))
@JvmName("口八百九十九加一") infix fun <丁> 九<九<八<丁>>>.加(甲: 一<无>): 零<零<九<丁>>> = 零(零(九(中!!.中!!.中)))
@JvmName("口九百九十九加一") infix fun 九<九<九<无>>>.加(甲: 一<无>): 零<零<零<一<无>>>> = 零(零(零(一())))

@JvmName("口零加二") infix fun <丁> 零<丁>.加(甲: 二<无>): 二<丁> = 加(一()) 加 一()
@JvmName("口一加二") infix fun <丁> 一<丁>.加(甲: 二<无>): 三<丁> = 加(一()) 加 一()
@JvmName("口二加二") infix fun <丁> 二<丁>.加(甲: 二<无>): 四<丁> = 加(一()) 加 一()
@JvmName("口三加二") infix fun <丁> 三<丁>.加(甲: 二<无>): 五<丁> = 加(一()) 加 一()
@JvmName("口四加二") infix fun <丁> 四<丁>.加(甲: 二<无>): 六<丁> = 加(一()) 加 一()
@JvmName("口五加二") infix fun <丁> 五<丁>.加(甲: 二<无>): 七<丁> = 加(一()) 加 一()
@JvmName("口六加二") infix fun <丁> 六<丁>.加(甲: 二<无>): 八<丁> = 加(一()) 加 一()
@JvmName("口七加二") infix fun <丁> 七<丁>.加(甲: 二<无>): 九<丁> = 加(一()) 加 一()
@JvmName("八加二") infix fun 八<无>.加(甲: 二<无>): 零<一<无>> = 加(一()) 加 一()
@JvmName("九加二") infix fun 九<无>.加(甲: 二<无>): 一<一<无>> = 加(一()) 加 一()
@JvmName("口零十八加二") infix fun <丁> 八<零<丁>>.加(甲: 二<无>) = 加(一()) 加 一()
@JvmName("口一十八加二") infix fun <丁> 八<一<丁>>.加(甲: 二<无>) = 加(一()) 加 一()
@JvmName("口二十八加二") infix fun <丁> 八<二<丁>>.加(甲: 二<无>) = 加(一()) 加 一()
@JvmName("口三十八加二") infix fun <丁> 八<三<丁>>.加(甲: 二<无>) = 加(一()) 加 一()
@JvmName("口四十八加二") infix fun <丁> 八<四<丁>>.加(甲: 二<无>) = 加(一()) 加 一()
@JvmName("口五十八加二") infix fun <丁> 八<五<丁>>.加(甲: 二<无>) = 加(一()) 加 一()
@JvmName("口六十八加二") infix fun <丁> 八<六<丁>>.加(甲: 二<无>) = 加(一()) 加 一()
@JvmName("口七十八加二") infix fun <丁> 八<七<丁>>.加(甲: 二<无>) = 加(一()) 加 一()
@JvmName("口八十八加二") infix fun <丁> 八<八<丁>>.加(甲: 二<无>) = 加(一()) 加 一()
@JvmName("九十八加二") infix fun 八<九<无>>.加(甲: 二<无>) = 加(一()) 加 一()
@JvmName("口零十九加二") infix fun <丁> 九<零<丁>>.加(甲: 二<无>) = 加(一()) 加 一()
@JvmName("口一十九加二") infix fun <丁> 九<一<丁>>.加(甲: 二<无>) = 加(一()) 加 一()
@JvmName("口二十九加二") infix fun <丁> 九<二<丁>>.加(甲: 二<无>) = 加(一()) 加 一()
@JvmName("口三十九加二") infix fun <丁> 九<三<丁>>.加(甲: 二<无>) = 加(一()) 加 一()
@JvmName("口四十九加二") infix fun <丁> 九<四<丁>>.加(甲: 二<无>) = 加(一()) 加 一()
@JvmName("口五十九加二") infix fun <丁> 九<五<丁>>.加(甲: 二<无>) = 加(一()) 加 一()
@JvmName("口六十九加二") infix fun <丁> 九<六<丁>>.加(甲: 二<无>) = 加(一()) 加 一()
@JvmName("口七十九加二") infix fun <丁> 九<七<丁>>.加(甲: 二<无>) = 加(一()) 加 一()
@JvmName("口八十九加二") infix fun <丁> 九<八<丁>>.加(甲: 二<无>) = 加(一()) 加 一()
@JvmName("九十九加二") infix fun 九<九<无>>.加(甲: 二<无>) = 加(一()) 加 一()
@JvmName("口零百九十八加二") infix fun <丁> 八<九<零<丁>>>.加(甲: 二<无>) = 加(一()) 加 一()
@JvmName("口一百九十八加二") infix fun <丁> 八<九<一<丁>>>.加(甲: 二<无>) = 加(一()) 加 一()
@JvmName("口二百九十八加二") infix fun <丁> 八<九<二<丁>>>.加(甲: 二<无>) = 加(一()) 加 一()
@JvmName("口三百九十八加二") infix fun <丁> 八<九<三<丁>>>.加(甲: 二<无>) = 加(一()) 加 一()
@JvmName("口四百九十八加二") infix fun <丁> 八<九<四<丁>>>.加(甲: 二<无>) = 加(一()) 加 一()
@JvmName("口五百九十八加二") infix fun <丁> 八<九<五<丁>>>.加(甲: 二<无>) = 加(一()) 加 一()
@JvmName("口六百九十八加二") infix fun <丁> 八<九<六<丁>>>.加(甲: 二<无>) = 加(一()) 加 一()
@JvmName("口七百九十八加二") infix fun <丁> 八<九<七<丁>>>.加(甲: 二<无>) = 加(一()) 加 一()
@JvmName("口八百九十八加二") infix fun <丁> 八<九<八<丁>>>.加(甲: 二<无>) = 加(一()) 加 一()
@JvmName("口九百九十八加二") infix fun 八<九<九<无>>>.加(甲: 二<无>) = 加(一()) 加 一()
@JvmName("口零百九十九加二") infix fun <丁> 九<九<零<丁>>>.加(甲: 二<无>) = 加(一()) 加 一()
@JvmName("口一百九十九加二") infix fun <丁> 九<九<一<丁>>>.加(甲: 二<无>) = 加(一()) 加 一()
@JvmName("口二百九十九加二") infix fun <丁> 九<九<二<丁>>>.加(甲: 二<无>) = 加(一()) 加 一()
@JvmName("口三百九十九加二") infix fun <丁> 九<九<三<丁>>>.加(甲: 二<无>) = 加(一()) 加 一()
@JvmName("口四百九十九加二") infix fun <丁> 九<九<四<丁>>>.加(甲: 二<无>) = 加(一()) 加 一()
@JvmName("口五百九十九加二") infix fun <丁> 九<九<五<丁>>>.加(甲: 二<无>) = 加(一()) 加 一()
@JvmName("口六百九十九加二") infix fun <丁> 九<九<六<丁>>>.加(甲: 二<无>) = 加(一()) 加 一()
@JvmName("口七百九十九加二") infix fun <丁> 九<九<七<丁>>>.加(甲: 二<无>) = 加(一()) 加 一()
@JvmName("口八百九十九加二") infix fun <丁> 九<九<八<丁>>>.加(甲: 二<无>) = 加(一()) 加 一()
@JvmName("口九百九十九加二") infix fun 九<九<九<无>>>.加(甲: 二<无>) = 加(一()) 加 一()

@JvmName("口零加三") infix fun <丁> 零<丁>.加(甲: 三<无>): 三<丁> = 加(一()) 加 二()
@JvmName("口一加三") infix fun <丁> 一<丁>.加(甲: 三<无>): 四<丁> = 加(一()) 加 二()
@JvmName("口二加三") infix fun <丁> 二<丁>.加(甲: 三<无>): 五<丁> = 加(一()) 加 二()
@JvmName("口三加三") infix fun <丁> 三<丁>.加(甲: 三<无>): 六<丁> = 加(一()) 加 二()
@JvmName("口四加三") infix fun <丁> 四<丁>.加(甲: 三<无>): 七<丁> = 加(一()) 加 二()
@JvmName("口五加三") infix fun <丁> 五<丁>.加(甲: 三<无>): 八<丁> = 加(一()) 加 二()
@JvmName("口六加三") infix fun <丁> 六<丁>.加(甲: 三<无>): 九<丁> = 加(一()) 加 二()
@JvmName("七加三") infix fun 七<无>.加(甲: 三<无>): 零<一<无>> = 加(一()) 加 二()
@JvmName("八加三") infix fun 八<无>.加(甲: 三<无>): 一<一<无>> = 加(一()) 加 二()
@JvmName("九加三") infix fun 九<无>.加(甲: 三<无>): 二<一<无>> = 加(一()) 加 二()
@JvmName("口零十七加三") infix fun <丁> 七<零<丁>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口一十七加三") infix fun <丁> 七<一<丁>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口二十七加三") infix fun <丁> 七<二<丁>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口三十七加三") infix fun <丁> 七<三<丁>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口四十七加三") infix fun <丁> 七<四<丁>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口五十七加三") infix fun <丁> 七<五<丁>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口六十七加三") infix fun <丁> 七<六<丁>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口七十七加三") infix fun <丁> 七<七<丁>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口八十七加三") infix fun <丁> 七<八<丁>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("九十七加三") infix fun 七<九<无>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口零十八加三") infix fun <丁> 八<零<丁>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口一十八加三") infix fun <丁> 八<一<丁>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口二十八加三") infix fun <丁> 八<二<丁>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口三十八加三") infix fun <丁> 八<三<丁>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口四十八加三") infix fun <丁> 八<四<丁>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口五十八加三") infix fun <丁> 八<五<丁>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口六十八加三") infix fun <丁> 八<六<丁>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口七十八加三") infix fun <丁> 八<七<丁>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口八十八加三") infix fun <丁> 八<八<丁>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("九十八加三") infix fun 八<九<无>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口零十九加三") infix fun <丁> 九<零<丁>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口一十九加三") infix fun <丁> 九<一<丁>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口二十九加三") infix fun <丁> 九<二<丁>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口三十九加三") infix fun <丁> 九<三<丁>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口四十九加三") infix fun <丁> 九<四<丁>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口五十九加三") infix fun <丁> 九<五<丁>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口六十九加三") infix fun <丁> 九<六<丁>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口七十九加三") infix fun <丁> 九<七<丁>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口八十九加三") infix fun <丁> 九<八<丁>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("九十九加三") infix fun 九<九<无>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口零百九十七加三") infix fun <丁> 七<九<零<丁>>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口一百九十七加三") infix fun <丁> 七<九<一<丁>>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口二百九十七加三") infix fun <丁> 七<九<二<丁>>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口三百九十七加三") infix fun <丁> 七<九<三<丁>>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口四百九十七加三") infix fun <丁> 七<九<四<丁>>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口五百九十七加三") infix fun <丁> 七<九<五<丁>>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口六百九十七加三") infix fun <丁> 七<九<六<丁>>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口七百九十七加三") infix fun <丁> 七<九<七<丁>>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口八百九十七加三") infix fun <丁> 七<九<八<丁>>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("九百九十七加三") infix fun 七<九<九<无>>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口零百九十八加三") infix fun <丁> 八<九<零<丁>>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口一百九十八加三") infix fun <丁> 八<九<一<丁>>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口二百九十八加三") infix fun <丁> 八<九<二<丁>>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口三百九十八加三") infix fun <丁> 八<九<三<丁>>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口四百九十八加三") infix fun <丁> 八<九<四<丁>>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口五百九十八加三") infix fun <丁> 八<九<五<丁>>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口六百九十八加三") infix fun <丁> 八<九<六<丁>>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口七百九十八加三") infix fun <丁> 八<九<七<丁>>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口八百九十八加三") infix fun <丁> 八<九<八<丁>>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("九百九十八加三") infix fun 八<九<九<无>>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口零百九十九加三") infix fun <丁> 九<九<零<丁>>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口一百九十九加三") infix fun <丁> 九<九<一<丁>>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口二百九十九加三") infix fun <丁> 九<九<二<丁>>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口三百九十九加三") infix fun <丁> 九<九<三<丁>>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口四百九十九加三") infix fun <丁> 九<九<四<丁>>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口五百九十九加三") infix fun <丁> 九<九<五<丁>>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口六百九十九加三") infix fun <丁> 九<九<六<丁>>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口七百九十九加三") infix fun <丁> 九<九<七<丁>>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("口八百九十九加三") infix fun <丁> 九<九<八<丁>>>.加(甲: 三<无>) = 加(一()) 加 二()
@JvmName("九百九十九加三") infix fun 九<九<九<无>>>.加(甲: 三<无>) = 加(一()) 加 二()


@JvmName("口零加四") infix fun <丁> 零<丁>.加(甲: 四<无>): 四<丁> = 加(二()) 加 二()
@JvmName("口一加四") infix fun <丁> 一<丁>.加(甲: 四<无>): 五<丁> = 加(二()) 加 二()
@JvmName("口二加四") infix fun <丁> 二<丁>.加(甲: 四<无>): 六<丁> = 加(二()) 加 二()
@JvmName("口三加四") infix fun <丁> 三<丁>.加(甲: 四<无>): 七<丁> = 加(二()) 加 二()
@JvmName("口四加四") infix fun <丁> 四<丁>.加(甲: 四<无>): 八<丁> = 加(二()) 加 二()
@JvmName("口五加四") infix fun <丁> 五<丁>.加(甲: 四<无>): 九<丁> = 加(二()) 加 二()
@JvmName("六加四") infix fun 六<无>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("七加四") infix fun 七<无>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("八加四") infix fun 八<无>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("九加四") infix fun 九<无>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口零十六加四") infix fun <丁> 六<零<丁>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口一十六加四") infix fun <丁> 六<一<丁>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口二十六加四") infix fun <丁> 六<二<丁>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口三十六加四") infix fun <丁> 六<三<丁>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口四十六加四") infix fun <丁> 六<四<丁>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口五十六加四") infix fun <丁> 六<五<丁>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口六十六加四") infix fun <丁> 六<六<丁>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口七十六加四") infix fun <丁> 六<七<丁>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口八十六加四") infix fun <丁> 六<八<丁>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("九十六加四") infix fun 六<九<无>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口零十七加四") infix fun <丁> 七<零<丁>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口一十七加四") infix fun <丁> 七<一<丁>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口二十七加四") infix fun <丁> 七<二<丁>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口三十七加四") infix fun <丁> 七<三<丁>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口四十七加四") infix fun <丁> 七<四<丁>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口五十七加四") infix fun <丁> 七<五<丁>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口六十七加四") infix fun <丁> 七<六<丁>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口七十七加四") infix fun <丁> 七<七<丁>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口八十七加四") infix fun <丁> 七<八<丁>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("九十七加四") infix fun 七<九<无>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口零十八加四") infix fun <丁> 八<零<丁>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口一十八加四") infix fun <丁> 八<一<丁>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口二十八加四") infix fun <丁> 八<二<丁>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口三十八加四") infix fun <丁> 八<三<丁>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口四十八加四") infix fun <丁> 八<四<丁>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口五十八加四") infix fun <丁> 八<五<丁>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口六十八加四") infix fun <丁> 八<六<丁>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口七十八加四") infix fun <丁> 八<七<丁>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口八十八加四") infix fun <丁> 八<八<丁>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("九十八加四") infix fun 八<九<无>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口零十九加四") infix fun <丁> 九<零<丁>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口一十九加四") infix fun <丁> 九<一<丁>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口二十九加四") infix fun <丁> 九<二<丁>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口三十九加四") infix fun <丁> 九<三<丁>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口四十九加四") infix fun <丁> 九<四<丁>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口五十九加四") infix fun <丁> 九<五<丁>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口六十九加四") infix fun <丁> 九<六<丁>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口七十九加四") infix fun <丁> 九<七<丁>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口八十九加四") infix fun <丁> 九<八<丁>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("九十九加四") infix fun 九<九<无>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口零百九十六加四") infix fun <丁> 六<九<零<丁>>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口一百九十六加四") infix fun <丁> 六<九<一<丁>>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口二百九十六加四") infix fun <丁> 六<九<二<丁>>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口三百九十六加四") infix fun <丁> 六<九<三<丁>>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口四百九十六加四") infix fun <丁> 六<九<四<丁>>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口五百九十六加四") infix fun <丁> 六<九<五<丁>>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口六百九十六加四") infix fun <丁> 六<九<六<丁>>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口七百九十六加四") infix fun <丁> 六<九<七<丁>>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口八百九十六加四") infix fun <丁> 六<九<八<丁>>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("九百九十六加四") infix fun 六<九<九<无>>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口零百九十七加四") infix fun <丁> 七<九<零<丁>>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口一百九十七加四") infix fun <丁> 七<九<一<丁>>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口二百九十七加四") infix fun <丁> 七<九<二<丁>>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口三百九十七加四") infix fun <丁> 七<九<三<丁>>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口四百九十七加四") infix fun <丁> 七<九<四<丁>>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口五百九十七加四") infix fun <丁> 七<九<五<丁>>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口六百九十七加四") infix fun <丁> 七<九<六<丁>>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口七百九十七加四") infix fun <丁> 七<九<七<丁>>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口八百九十七加四") infix fun <丁> 七<九<八<丁>>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("九百九十七加四") infix fun 七<九<九<无>>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口零百九十八加四") infix fun <丁> 八<九<零<丁>>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口一百九十八加四") infix fun <丁> 八<九<一<丁>>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口二百九十八加四") infix fun <丁> 八<九<二<丁>>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口三百九十八加四") infix fun <丁> 八<九<三<丁>>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口四百九十八加四") infix fun <丁> 八<九<四<丁>>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口五百九十八加四") infix fun <丁> 八<九<五<丁>>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口六百九十八加四") infix fun <丁> 八<九<六<丁>>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口七百九十八加四") infix fun <丁> 八<九<七<丁>>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口八百九十八加四") infix fun <丁> 八<九<八<丁>>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("九百九十八加四") infix fun 八<九<九<无>>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口零百九十九加四") infix fun <丁> 九<九<零<丁>>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口一百九十九加四") infix fun <丁> 九<九<一<丁>>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口二百九十九加四") infix fun <丁> 九<九<二<丁>>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口三百九十九加四") infix fun <丁> 九<九<三<丁>>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口四百九十九加四") infix fun <丁> 九<九<四<丁>>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口五百九十九加四") infix fun <丁> 九<九<五<丁>>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口六百九十九加四") infix fun <丁> 九<九<六<丁>>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口七百九十九加四") infix fun <丁> 九<九<七<丁>>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("口八百九十九加四") infix fun <丁> 九<九<八<丁>>>.加(甲: 四<无>) = 加(二()) 加 二()
@JvmName("九百九十九加四") infix fun 九<九<九<无>>>.加(甲: 四<无>) = 加(二()) 加 二()


@JvmName("数加数") infix fun <左: 数<*, *>, 右: 数<*, *>> 左.加(甲: 右) = 未(toInt() + 甲.toInt())
@JvmName("数减数") infix fun <左: 数<*, *>, 右: 数<*, *>> 左.减(甲: 右) = 未(toInt() - 甲.toInt())
@JvmName("数乘数") infix fun <左: 数<*, *>, 右: 数<*, *>> 左.乘(甲: 右) = 未(toInt() * 甲.toInt())
@JvmName("数除数") infix fun <左: 数<*, *>, 右: 数<*, *>> 左.除(甲: 右) = 未(toInt() / 甲.toInt())