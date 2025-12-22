@file:Suppress("JpaQueryApiInspection", "unused")

package nu.westlin.postgreslock.gdl

import nu.westlin.postgreslock.logger
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.getBean
import org.springframework.context.ApplicationContext
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.*

context(ctx: ApplicationContext)
fun korGdl() {
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
}

@Service
class GdlService(
    private val lockRepository: GdlLockRepository,
    private val forandringsarendeRepository: GdlForandringsarendeRepository,
    private val transactionManager: PlatformTransactionManager
) {
    private val logger: Logger = LoggerFactory.getLogger(this.javaClass)

    @Transactional
    fun behandlaEjBehandladeForandringsarenden() {
        logger.info("Försöker få låset")
        val gotLock = lockRepository.lock()
        if (!gotLock) {
            logger.info("Fick inte låset... :(")
            return
        }
        logger.info("Fick låset! :D")
        logger.info("Testar at ta låset igen i samma tx: ${lockRepository.lock()}")

        forandringsarendeRepository.ejBehandlade().forEach { forandringsarende ->
            TransactionTemplate(transactionManager).apply {
                propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
            }.execute {
                logger.info("Behandlar förändringsärende ${forandringsarende.dataleveransidentitet}...")
                // Simulera arbete
                Thread.sleep(Duration.ofSeconds(2))
                forandringsarendeRepository.uppdatera(forandringsarende.dataleveransidentitet, "200", Instant.now())
                logger.info("Förändringsärende ${forandringsarende.dataleveransidentitet} behandlat.")
            }
        }
    }

    fun allaBehandladeForandringsarenden(): List<Forandringsarende> = forandringsarendeRepository.behandlade()
}

data class Forandringsarende(
    val dataleveransidentitet: UUID
)

@Repository
class GdlForandringsarendeRepository(
    private val jdbcClient: JdbcClient
) {

    @Suppress("SqlRedundantOrderingDirection")
    fun behandlade(): List<Forandringsarende> = jdbcClient
        .sql("select * from forandringsarende where behandladtidpunkt is not null order by behandladtidpunkt asc")
        .query { rs, _ ->
            Forandringsarende(
                dataleveransidentitet = UUID.fromString(rs.getString("dataleveransidentitet")),
            )
        }
        .list()

    @Suppress("SqlRedundantOrderingDirection")
    fun ejBehandlade(): List<Forandringsarende> = jdbcClient
        .sql("select * from forandringsarende where behandladtidpunkt is null order by mottagentidpunkt asc")
        .query { rs, _ ->
            Forandringsarende(
                dataleveransidentitet = UUID.fromString(rs.getString("dataleveransidentitet")),
            )
        }
        .list()

    fun uppdatera(dataleveransidentitet: UUID, status: String, behandladtidpunkt: Instant) {
        val antalRader = jdbcClient
            .sql("UPDATE forandringsarende set http_status = :http_status, behandladtidpunkt = :behandladtidpunkt where dataleveransidentitet = :dataleveransidentitet")
            .param("http_status", status)
            .param("behandladtidpunkt", Timestamp.from(behandladtidpunkt))
            .param("dataleveransidentitet", dataleveransidentitet)
            .update()
        check(antalRader == 1) { "1 rad förväntades bli uppdaterad men antal rader blev: $antalRader" }
    }
}

@Repository
class GdlLockRepository(
    private val jdbcClient: JdbcClient
) {
    private val lockKey: Long = "GDL-forandringsarende-exclusive-lock".hashCode().toLong()

    /**
     * **Obs!** Denna funktion kräver att det redan finns en transaktion. Se [Propagation.MANDATORY].
     * @return true om låset kunde tas, annars false
     */
    @Suppress("JpaQueryApiInspection")
    @Transactional(propagation = Propagation.MANDATORY)
    fun lock(): Boolean =
        jdbcClient
            .sql("select pg_try_advisory_xact_lock(:lockKey)")
            .param("lockKey", lockKey)
            .query(Boolean::class.java)
            .single()
}