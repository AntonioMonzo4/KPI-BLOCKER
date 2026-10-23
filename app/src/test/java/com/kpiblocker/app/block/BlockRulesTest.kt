package com.kpiblocker.app.block

import com.kpiblocker.app.data.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class BlockRulesTest {

    private val self = "com.kpiblocker.app"
    private val whatsapp = "com.whatsapp"
    private val instagram = "com.instagram.android"

    private fun cal(day: Int, hour: Int, minute: Int): Calendar =
        Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }

    private fun nowAt(day: Int, hour: Int, minute: Int): Long = cal(day, hour, minute).timeInMillis

    private val manana = Schedule(
        id = "1",
        name = "Mañana",
        days = setOf(1, 2, 3, 4, 5),
        startMin = 8 * 60,
        endMin = 22 * 60
    )

    private fun isBlocked(
        pkg: String,
        blocked: Set<String> = setOf(whatsapp),
        schedules: List<Schedule> = emptyList(),
        pauseUntil: Long = 0L,
        now: Long = nowAt(19, 12, 0)
    ) = BlockRules.isBlocked(
        packageName = pkg,
        selfPackage = self,
        blockedPackages = blocked,
        schedules = schedules,
        pauseUntil = pauseUntil,
        now = now
    )

    @Test
    fun `nunca bloquea la propia app`() {
        assertFalse(isBlocked(self, blocked = setOf(self, whatsapp)))
    }

    @Test
    fun `bloquea app marcada sin horarios a cualquier hora`() {
        assertTrue(isBlocked(whatsapp, now = nowAt(19, 3, 0)))
    }

    @Test
    fun `no bloquea apps no marcadas`() {
        assertFalse(isBlocked(instagram))
    }

    @Test
    fun `la pausa anula el bloqueo`() {
        val now = nowAt(19, 12, 0)
        assertFalse(isBlocked(whatsapp, pauseUntil = now + 60_000, now = now))
    }

    @Test
    fun `la pausa expirada vuelve a bloquear`() {
        val now = nowAt(19, 12, 0)
        assertTrue(isBlocked(whatsapp, pauseUntil = now - 1, now = now))
    }

    @Test
    fun `bloquea dentro del horario`() {
        assertTrue(isBlocked(whatsapp, schedules = listOf(manana), now = nowAt(19, 10, 0)))
    }

    @Test
    fun `no bloquea fuera del horario`() {
        assertFalse(isBlocked(whatsapp, schedules = listOf(manana), now = nowAt(19, 7, 0)))
    }

    @Test
    fun `no bloquea en dia no seleccionado`() {
        assertFalse(isBlocked(whatsapp, schedules = listOf(manana), now = nowAt(24, 12, 0))) // sábado
    }

    @Test
    fun `si todos los horarios estan deshabilitados se bloquea siempre`() {
        val disabled = manana.copy(enabled = false)
        assertTrue(isBlocked(whatsapp, schedules = listOf(disabled), now = nowAt(19, 7, 0)))
    }

    @Test
    fun `bloquea si al menos un horario activo lo cubre`() {
        val otro = manana.copy(id = "2", name = "Tarde", startMin = 13 * 60, endMin = 15 * 60)
        assertTrue(
            isBlocked(whatsapp, schedules = listOf(manana, otro), now = nowAt(19, 14, 0))
        )
    }

    @Test
    fun `nextUnlock devuelve el fin del horario de hoy`() {
        val now = nowAt(19, 10, 0)
        val startOfDay = cal(19, 0, 0).timeInMillis
        val expected = startOfDay + 22 * 60 * 60_000L
        assertEquals(expected, BlockRules.nextUnlock(now, listOf(manana), pauseUntil = 0L))
    }

    @Test
    fun `nextUnlock en horario nocturno pasa al dia siguiente`() {
        val nocturno = manana.copy(startMin = 22 * 60, endMin = 6 * 60, days = setOf(1))
        val now = nowAt(19, 23, 0)
        val expected = cal(19, 0, 0).timeInMillis + 6 * 60 * 60_000L + 24 * 60 * 60_000L
        assertEquals(expected, BlockRules.nextUnlock(now, listOf(nocturno), pauseUntil = 0L))
    }

    @Test
    fun `nextUnlock devuelve la pausa si esta activa`() {
        val now = nowAt(19, 10, 0)
        val pauseUntil = now + 5 * 60_000L
        assertEquals(pauseUntil, BlockRules.nextUnlock(now, listOf(manana), pauseUntil))
    }

    @Test
    fun `nextUnlock es null si nada esta cubriendo ahora`() {
        assertNull(BlockRules.nextUnlock(nowAt(19, 7, 0), listOf(manana), pauseUntil = 0L))
        assertNull(BlockRules.nextUnlock(nowAt(19, 7, 0), emptyList(), pauseUntil = 0L))
    }
}
