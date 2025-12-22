package nu.westlin.postgreslock

import nu.westlin.postgreslock.gdl.GdlService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.getBean
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

@SpringBootApplication
class PostgresLockApplication

private val logger: Logger = LoggerFactory.getLogger("Foo")

fun main(args: Array<String>) {
    val ctx = runApplication<PostgresLockApplication>(*args)

    // "Starta" tre GDL:er (för prestanda och redundans och för att minst en alltid ska vara igång i OpenShift).
    // Observera att endast en GDL får bearbeta ej behandlade förändringsärenden!
    val gdlService = ctx.getBean<GdlService>()
    repeat(3) {
        Thread.startVirtualThread {
            gdlService.behandlaEjBehandladeForandringsarenden()
        }
    }
    // Läs behandlade förändringsärenden parallellt. Detta görs för att visa att vi får läsa (men inte skriva) parallellt.
    repeat(2) {
        Thread.startVirtualThread {
            repeat(10) {
                logger.info("Behandlade: ${gdlService.allaBehandladeForandringsarenden().map { it.dataleveransidentitet }}")
                Thread.sleep(1000)
            }
        }
    }

    /*
        val service = ctx.getBean<ExclusiveWriterService>()
        repeat(100) {
            Thread.startVirtualThread {
                logger.info("${service.tryWriteExclusively()}")
            }
        }
    */

    //ctx.getBean<JobbService>().bearbetaJobb()
}

@Service
class JobbService(
    private val jobbRepository: JobbRepository,
    private val transactionTemplate: TransactionTemplate
) {

    @Transactional
    fun bearbetaJobb() {
        repeat(5) {
            Thread.startVirtualThread {
                transactionTemplate.execute {
                    val deForstaXEjBearbetade = jobbRepository.deForstaXEjBearbetade(2)
                    logger.info("Fick jobb: ${deForstaXEjBearbetade.map { it.id }}")
                    deForstaXEjBearbetade.forEach { jobb ->
                        logger.info("Bearbetar jobb ${jobb.id}")
                        Thread.sleep(1_000)
                        jobbRepository.klarmarkera(jobb.id, Instant.now())
                        logger.info("Jobb ${jobb.id} klart")
                    }
                    deForstaXEjBearbetade.map { it.id }.let { jobb ->
                        if (jobb.isNotEmpty()) {
                            logger.info("Klar med jobb: $jobb")
                        }
                    }
                }
            }
        }
    }
}

@Service
class ExclusiveWriterService(
    private val jdbcTemplate: JdbcTemplate
) {
    // Se https://www.postgresql.org/docs/14/functions-admin.html#FUNCTIONS-ADVISORY-LOCKS

    private val lockKey = "tryWriteExclusively".hashCode().toLong()

    @Transactional
    fun tryWriteExclusively(): Boolean {
        val gotLock: Boolean = jdbcTemplate.queryForObject(
            "select pg_try_advisory_xact_lock(?)",
            Boolean::class.java,
            lockKey
        ) ?: false

        if (!gotLock) {
            return false // någon annan instans skriver
        }

        Thread.sleep(100)
        logger.info("Jag kör!")

        return true
    }
}

data class Jobb(
    val id: Int,
    val data: String,
    val klartidpunkt: Instant?
)

@Suppress("JpaQueryApiInspection")
@Repository
class JobbRepository(private val jdbcClient: JdbcClient) {

    fun klarmarkera(id: Int, klartidpunkt: Instant) {
        jdbcClient
            .sql("update jobb set klartidpunkt = :klartidpunkt where id = :id")
            .param("klartidpunkt", java.sql.Timestamp.from(klartidpunkt))
            .param("id", id)
            .update()
    }

    @Transactional(propagation = Propagation.MANDATORY)
    fun deForstaXEjBearbetade(limit: Int): List<Jobb> {
        @Suppress("SqlRedundantOrderingDirection")
        return jdbcClient
            .sql("select * from jobb where klartidpunkt is null order by klartidpunkt asc limit :limit for update skip locked")
            .param("limit", limit)
            .query { rs, _ ->
                Jobb(
                    id = rs.getInt("id"),
                    data = rs.getString("data"),
                    klartidpunkt = rs.getObject("klartidpunkt") as Instant?
                )
            }
            .list()
            .sortedBy { it.id }
    }
}
