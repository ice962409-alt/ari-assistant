package com.ari.assistant.agents

import java.time.LocalDate

/** Dónde vive la libreta de personas (la base de datos en la app; una lista en las pruebas). */
interface PeopleStore {
    fun people(): List<Person>
    /** Guarda una persona nueva (id 0) o actualiza una existente. Devuelve su id. */
    fun savePerson(person: Person): Long
    fun deletePerson(id: Long)
}

/**
 * La libreta de personas de Ari: recuerda a quien le mencionas ("mi primo Luis
 * es dentista", "Luis cumple el 5 de mayo") y te lo cuenta cuando preguntas.
 * Gemma también recibe la libreta, para que sepa de quién le hablas. Nunca sale
 * del teléfono ni se le pasa al equipo de la laptop.
 */
class PeopleAgent(
    private val store: PeopleStore,
    private val onPeopleChanged: () -> Unit,
    private val today: () -> LocalDate = { LocalDate.now() }
) : Agent {

    override val id = "personas"
    override val name = "Agente de personas"
    override val description = "Recuerda a las personas que le mencionas: quién es quién, qué hacen y cuándo cumplen años."
    override val examples = listOf(
        "mi primo Luis es dentista",
        "Luis cumple el 5 de mayo",
        "qué sabes de Luis",
        "quién cumple años"
    )

    override fun match(message: String, normalized: String): AgentTask? {
        return when (val intent = PeopleParser.parse(message, normalized)) {
            null -> null
            // Preguntar u olvidar a alguien solo si Ari lo conoce: "quién es Einstein" es para Gemma.
            is PeopleParser.Intent.Ask -> find(intent.name)?.let { AgentTask(message, ACTION_ASK, it.id.toString()) }
            is PeopleParser.Intent.Forget -> find(intent.name)?.let { AgentTask(message, ACTION_FORGET, it.id.toString()) }
            PeopleParser.Intent.ListAll -> AgentTask(message, ACTION_LIST)
            PeopleParser.Intent.Birthdays -> AgentTask(message, ACTION_BIRTHDAYS)
            is PeopleParser.Intent.Remember -> AgentTask(message, ACTION_REMEMBER, message)
        }
    }

    override fun execute(task: AgentTask): AgentResult = when (task.action) {
        ACTION_REMEMBER -> remember(task.argument)
        ACTION_ASK -> byId(task.argument)?.let { AgentResult(describe(it)) }
            ?: AgentResult("Ya no tengo a esa persona en mi libreta.", success = false)
        ACTION_FORGET -> byId(task.argument)?.let {
            store.deletePerson(it.id)
            onPeopleChanged()
            AgentResult("Listo, olvidé a ${label(it)}.")
        } ?: AgentResult("Ya no tengo a esa persona en mi libreta.", success = false)
        ACTION_LIST -> list()
        ACTION_BIRTHDAYS -> birthdays()
        else -> AgentResult("Acción desconocida: ${task.action}", success = false)
    }

    private fun remember(message: String): AgentResult {
        val intent = PeopleParser.parse(message) as? PeopleParser.Intent.Remember
            ?: return AgentResult("No entendí de quién me hablas.", success = false)
        val existing = intent.name?.let { find(it) } ?: intent.relation?.let { relation ->
            val sameRelation = store.people().filter { it.relation == relation }
            // Ficha sin nombre propio ("Primo"), creada con "mi primo es dentista".
            val placeholder = sameRelation.firstOrNull { normalizeForAgents(it.name) == normalizeForAgents(relation) }
            when {
                intent.name != null -> placeholder
                PeopleParser.isSingle(relation) -> sameRelation.firstOrNull()
                else -> placeholder
            }
        }
        val base = existing ?: Person(0, intent.name ?: intent.relation.orEmpty().replaceFirstChar { it.uppercaseChar() })
        val facts = (base.facts + listOfNotNull(intent.fact))
            .distinctBy { normalizeForAgents(it) }
            .takeLast(MAX_FACTS)
            .map { it.take(MAX_FACT_LENGTH) }
        val person = base.copy(
            // "mi mamá se llama Rosa": la ficha de "Mamá" ahora es Rosa.
            name = intent.name ?: base.name,
            relation = intent.relation ?: base.relation,
            facts = facts,
            birthday = intent.birthday ?: base.birthday
        )
        store.savePerson(person)
        onPeopleChanged()
        val parts = listOfNotNull(
            intent.fact,
            intent.birthday?.let { "cumple el ${PeopleParser.describeBirthday(it)}" }
        )
        val who = if (person.relation.isNotEmpty() && normalizeForAgents(person.name) != normalizeForAgents(person.relation)) {
            "${person.name} (tu ${person.relation})"
        } else {
            label(person)
        }
        return AgentResult(
            if (parts.isEmpty()) "Anotado: $who." else "Anotado: $who ${parts.joinToString(" y ")}."
        )
    }

    private fun describe(person: Person): String = buildString {
        append(label(person))
        if (person.relation.isNotEmpty() && normalizeForAgents(person.relation) != normalizeForAgents(person.name)) {
            append(" es tu ").append(person.relation)
        }
        append(".")
        if (person.facts.isNotEmpty()) append(" Sé que ").append(person.facts.joinToString("; ")).append(".")
        person.birthday?.let { append(" Cumple el ").append(PeopleParser.describeBirthday(it)).append(".") }
        if (person.facts.isEmpty() && person.birthday == null) append(" Todavía no me has contado nada más.")
    }

    private fun list(): AgentResult {
        val people = store.people()
        if (people.isEmpty()) {
            return AgentResult("Todavía no me has presentado a nadie. Dime, por ejemplo: \"mi primo Luis es dentista\".")
        }
        return AgentResult("Personas que conozco:" + people.joinToString("") { "\n• " + summary(it) })
    }

    private fun birthdays(): AgentResult {
        val now = today()
        val upcoming = store.people().mapNotNull { person ->
            val birthday = person.birthday ?: return@mapNotNull null
            val (month, day) = birthday.split("-").map(String::toInt)
            val date = runCatching { LocalDate.of(now.year, month, day) }.getOrNull() ?: return@mapNotNull null
            val next = if (date.isBefore(now)) date.plusYears(1) else date
            person to next
        }.sortedBy { it.second }
        if (upcoming.isEmpty()) {
            return AgentResult("No sé el cumpleaños de nadie todavía. Dime, por ejemplo: \"Luis cumple el 5 de mayo\".")
        }
        return AgentResult("Próximos cumpleaños:" + upcoming.joinToString("") { (person, date) ->
            val days = java.time.temporal.ChronoUnit.DAYS.between(now, date)
            val whenText = when (days) {
                0L -> "¡hoy!"
                1L -> "mañana"
                else -> "en $days días"
            }
            "\n• ${label(person)}: ${PeopleParser.describeBirthday(person.birthday!!)} ($whenText)"
        })
    }

    private fun find(name: String): Person? {
        val wanted = normalizeForAgents(name)
        val people = store.people()
        return people.firstOrNull { normalizeForAgents(it.name) == wanted }
            ?: people.firstOrNull { normalizeForAgents(it.relation) == wanted && PeopleParser.isSingle(it.relation) }
            ?: people.firstOrNull { normalizeForAgents(it.name).split(" ").first() == wanted }
    }

    private fun byId(id: String) = id.toLongOrNull()?.let { wanted -> store.people().firstOrNull { it.id == wanted } }

    private fun label(person: Person) = person.name.ifEmpty { person.relation }

    companion object {
        private const val ACTION_REMEMBER = "anotar"
        private const val ACTION_ASK = "contar"
        private const val ACTION_FORGET = "olvidar"
        private const val ACTION_LIST = "listar"
        private const val ACTION_BIRTHDAYS = "cumpleanos"
        private const val MAX_FACTS = 10
        private const val MAX_FACT_LENGTH = 200

        /** Una línea por persona, para la lista y para lo que recibe Gemma. */
        fun summary(person: Person): String = buildString {
            append(person.name.ifEmpty { person.relation })
            if (person.relation.isNotEmpty() && normalizeForAgents(person.relation) != normalizeForAgents(person.name)) {
                append(" (").append(person.relation).append(")")
            }
            val details = person.facts + listOfNotNull(person.birthday?.let { "cumple el ${PeopleParser.describeBirthday(it)}" })
            if (details.isNotEmpty()) append(": ").append(details.joinToString("; "))
        }
    }
}
