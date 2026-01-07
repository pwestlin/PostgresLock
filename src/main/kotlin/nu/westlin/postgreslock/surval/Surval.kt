package nu.westlin.postgreslock.surval

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.getBean
import org.springframework.context.ApplicationContext
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID

context(ctx: ApplicationContext)
fun korSurval() {
    // "Starta" några Surval (för prestanda och redundans och för att minst en alltid ska vara igång i OpenShift).
    // Observera att alla sura valar får behandla ett jobb men de får endst behandla olika jobb!
    val survalService = ctx.getBean<SurvalService>()
    repeat(5) {
        Thread.startVirtualThread {
            survalService.behandlaEjBehandlatJobb()
        }
    }
}

@Service
class SurvalService(
    private val jobbRepository: SurvalJobbRepository
) {
    private val logger: Logger = LoggerFactory.getLogger(this.javaClass)

    @Transactional
    fun behandlaEjBehandlatJobb() {
        val jobb = jobbRepository.aldstaEjBehandlade()
        logger.info("jobb: $jobb")
        if(jobb == null) {
            return
        }

        // Simulera arbete
        Thread.sleep(Duration.ofSeconds(2))
        // Någon som vet vad defaultvärdena för transaction timeout och read timeout i Spring/Postgres är?
        //Thread.sleep(Duration.ofSeconds(200))
        jobbRepository.klarmarkera(id = jobb.id,klartidpunkt = Instant.now())
        logger.info("Jobb ${jobb.id} klart")
    }
}

@Repository
class SurvalJobbRepository(
    private val jdbcClient: JdbcClient
) {

    @Suppress("SqlRedundantOrderingDirection")
    @Transactional(propagation = Propagation.MANDATORY)
    fun aldstaEjBehandlade(): SurvalJobb? = jdbcClient
        // TODO pevest: Visa med och utan "skip locked"
        .sql("select id,status from surval_jobb where klartidpunkt is null order by mottagentidpunkt asc limit 1 for update skip locked")
        .query {rs, _ ->
            SurvalJobb(
                id = UUID.fromString(rs.getString("id")),
                status = rs.getString("status")
            )
        }
        .optional()
        .orElse(null)

    @Suppress("JpaQueryApiInspection")
    fun klarmarkera(id: UUID, klartidpunkt: Instant) {
        val antalRader = jdbcClient
            .sql("update surval_jobb set klartidpunkt = :klartidpunkt where id = :id")
            .param("klartidpunkt", Timestamp.from(klartidpunkt))
            .param("id", id)
            .update()
        check(antalRader == 1) {"Antal uppdaterade rader förväntades bli 1 men var $antalRader"}
    }
}

data class SurvalJobb(val id: UUID, val status: String)