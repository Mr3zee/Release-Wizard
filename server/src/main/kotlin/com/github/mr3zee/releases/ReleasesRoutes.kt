package com.github.mr3zee.releases

import com.github.mr3zee.NotFoundException
import com.github.mr3zee.api.*
import com.github.mr3zee.auth.userSession
import com.github.mr3zee.model.BlockId
import com.github.mr3zee.model.ReleaseId
import com.github.mr3zee.model.TeamId
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory
import java.util.UUID

private val log = LoggerFactory.getLogger("com.github.mr3zee.releases.ReleasesRoutes")

fun Route.releaseRoutes() {
    val service by inject<ReleasesService>()

    route(ApiRoutes.Releases.BASE) {
        get {
            val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
            val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 20).coerceIn(1, 200)
            val search = call.request.queryParameters["q"]
            val statusStr = call.request.queryParameters["status"]
            val status = statusStr?.let { runCatching { com.github.mr3zee.model.ReleaseStatus.valueOf(it) }.getOrNull() }
            val projectId = call.request.queryParameters["projectId"]?.let { com.github.mr3zee.model.ProjectId(it) }
            val tag = call.request.queryParameters["tag"]
            val teamId = call.request.queryParameters["teamId"]?.let { TeamId(it) }
            val (releases, totalCount) = service.listReleases(
                call.userSession(), teamId, offset, limit, search, status, projectId, tag,
            )
            call.respond(ReleaseListResponse(
                releases = releases,
                pagination = PaginationInfo(totalCount = totalCount, offset = offset, limit = limit),
            ))
        }

        post {
            val request = call.receive<CreateReleaseRequest>()
            val release = service.startRelease(request, call.userSession())
            log.info("Release created: {} for project {}", release.id.value, request.projectTemplateId.value)
            val executions = service.getBlockExecutions(release.id)
            call.respond(HttpStatusCode.Created, ReleaseResponse(release, executions))
        }

        route("/{id}") {
            get {
                val id = call.requireReleaseId()
                service.checkAccess(id, call.userSession())
                val release = service.getRelease(id)
                    ?: throw NotFoundException("Release not found: ${id.value}")
                val executions = service.getBlockExecutions(id)
                call.respond(ReleaseResponse(release, executions))
            }

            post("/await") {
                val id = call.requireReleaseId()
                val session = call.userSession()
                service.checkAccess(id, session)
                service.awaitRelease(id)
                val release = service.getRelease(id)
                    ?: throw NotFoundException("Release not found: ${id.value}")
                val executions = service.getBlockExecutions(id)
                call.respond(ReleaseResponse(release, executions))
            }

            post("/stop") {
                val id = call.requireReleaseId()
                val stopped = service.stopRelease(id, call.userSession())
                if (!stopped) {
                    log.warn("Cannot stop release {}", id.value)
                    throw IllegalArgumentException("Cannot stop release: ${id.value}")
                }
                log.info("Release stopped: {}", id.value)
                val release = service.getRelease(id)
                    ?: throw NotFoundException("Release not found after stop: ${id.value}")
                val executions = service.getBlockExecutions(id)
                call.respond(ReleaseResponse(release, executions))
            }

            post("/resume") {
                val id = call.requireReleaseId()
                val resumed = service.resumeRelease(id, call.userSession())
                if (!resumed) {
                    log.warn("Cannot resume release {}", id.value)
                    throw IllegalArgumentException("Cannot resume release: ${id.value}")
                }
                log.info("Release resumed: {}", id.value)
                val release = service.getRelease(id)
                    ?: throw NotFoundException("Release not found after resume: ${id.value}")
                val executions = service.getBlockExecutions(id)
                call.respond(ReleaseResponse(release, executions))
            }

            post("/cancel") {
                val id = call.requireReleaseId()
                val cancelled = service.cancelRelease(id, call.userSession())
                if (!cancelled) {
                    log.warn("Cannot cancel release {}", id.value)
                    throw IllegalArgumentException("Cannot cancel release: ${id.value}")
                }
                log.info("Release cancelled: {}", id.value)
                val release = service.getRelease(id)
                    ?: throw NotFoundException("Release not found after cancel: ${id.value}")
                val executions = service.getBlockExecutions(id)
                call.respond(ReleaseResponse(release, executions))
            }

            post("/rerun") {
                val id = call.requireReleaseId()
                val newRelease = service.rerunRelease(id, call.userSession())
                log.info("Release rerun: {} -> new release {}", id.value, newRelease.id.value)
                val executions = service.getBlockExecutions(newRelease.id)
                call.respond(HttpStatusCode.Created, ReleaseResponse(newRelease, executions))
            }

            post("/archive") {
                val id = call.requireReleaseId()
                val archived = service.archiveRelease(id, call.userSession())
                if (!archived) {
                    log.warn("Cannot archive release {}", id.value)
                    throw IllegalArgumentException("Cannot archive release: ${id.value}")
                }
                log.info("Release archived: {}", id.value)
                val release = service.getRelease(id)
                    ?: throw NotFoundException("Release not found after archive: ${id.value}")
                val executions = service.getBlockExecutions(id)
                call.respond(ReleaseResponse(release, executions))
            }

            delete {
                val id = call.requireReleaseId()
                val deleted = service.deleteRelease(id, call.userSession())
                if (!deleted) {
                    log.warn("Cannot delete release {}", id.value)
                    throw IllegalArgumentException("Cannot delete release: ${id.value}")
                }
                log.info("Release deleted: {}", id.value)
                call.respond(HttpStatusCode.NoContent)
            }

            route("/blocks/{blockId}") {
                post("/stop") {
                    val releaseId = call.requireReleaseId()
                    val blockId = call.requireBlockId()
                    val stopped = service.stopBlock(releaseId, blockId, call.userSession())
                    if (!stopped) {
                        throw IllegalArgumentException("Cannot stop block: ${blockId.value}")
                    }
                    val release = service.getRelease(releaseId)
                        ?: throw NotFoundException("Release not found after block stop: ${releaseId.value}")
                    val executions = service.getBlockExecutions(releaseId)
                    call.respond(ReleaseResponse(release, executions))
                }

                post("/restart") {
                    val releaseId = call.requireReleaseId()
                    val blockId = call.requireBlockId()
                    val restarted = service.restartBlock(releaseId, blockId, call.userSession())
                    if (!restarted) {
                        log.warn("Cannot restart block {} in release {}", blockId.value, releaseId.value)
                        throw IllegalArgumentException("Cannot restart block: ${blockId.value}")
                    }
                    log.info("Block restarted: {} in release {}", blockId.value, releaseId.value)
                    call.respond(HttpStatusCode.OK, mapOf("status" to "restarted"))
                }

                post("/approve") {
                    val releaseId = call.requireReleaseId()
                    val blockId = call.requireBlockId()
                    val request = call.receive<ApproveBlockRequest>()
                    val approved = service.approveBlock(releaseId, blockId, request, call.userSession())
                    if (!approved) {
                        log.warn("Cannot approve block {} in release {}", blockId.value, releaseId.value)
                        throw IllegalArgumentException("Cannot approve block: ${blockId.value}")
                    }
                    log.info("Block approved: {} in release {}", blockId.value, releaseId.value)
                    call.respond(HttpStatusCode.OK, mapOf("status" to "approved"))
                }
            }
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.requireReleaseId(): ReleaseId {
    val raw = parameters["id"]
        ?: throw IllegalArgumentException("Missing id")
    try {
        UUID.fromString(raw)
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException("Invalid release ID format")
    }
    return ReleaseId(raw)
}

private val BLOCK_ID_PATTERN = Regex("^[a-zA-Z0-9_.-]+$")

private fun io.ktor.server.application.ApplicationCall.requireBlockId(): BlockId {
    val raw = parameters["blockId"]
        ?: throw IllegalArgumentException("Missing blockId")
    if (raw.length > 100) {
        throw IllegalArgumentException("Invalid block ID: too long")
    }
    if (!BLOCK_ID_PATTERN.matches(raw)) {
        throw IllegalArgumentException("Invalid block ID format")
    }
    return BlockId(raw)
}
