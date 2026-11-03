import com.chandu.gatesystem.ai2027.domain.*
import java.io.File

fun main(args: Array<String>) {
    val plan = Plan.fromJson(File(args[0]).readText())
    val topics = plan.days.flatMap { d -> listOf(TopicRef(d.learnTopicId, d.learnSubjectId, d.learnTopic), TopicRef(d.revisionTopicId, d.revisionSubjectId, d.revisionTopic),
        TopicRef(Topics.id("GA", d.gaTopic), "GA", d.gaTopic)) }.distinctBy { it.id }
    val all = ArrayList<Question>()
    for (s in BankCodec.subjects) {
        val b = BankBuilder.build(s, topics)
        val roles = b.questions.groupingBy { BankCodec.role(it) }.eachCount()
        println("$s: ${b.questions.size} questions $roles marks=${b.questions.groupingBy { it.marks }.eachCount()} types=${b.questions.groupingBy { it.type }.eachCount()} topics=${b.questions.map { it.topicId }.distinct().size}")
        all += b.questions
    }
    File(args[1]).writeText(BankCodec.toJson(all))
    println("TOTAL ${all.size} -> ${args[1]} (${File(args[1]).length() / 1024} KB)")
}
