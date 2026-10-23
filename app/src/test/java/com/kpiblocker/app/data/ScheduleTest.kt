package com.kpiblocker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ScheduleTest {

    // 19 de octubre de 2026 es lunes; 20 es martes; 24 es sábado
    private fun cal(day: Int, hour: Int, minute: Int): Calendar =
        Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }

    private val laboral = Schedule(
        id = "1",
        name = "Trabajo",
        days = setOf(1, 2, 3, 4, 5),
        startMin = 8 * 60,
        endMin = 22 * 60
    )

    @Test
    fun `bloquea dentro del rango el mismo dia`() {
        assertTrue(laboral.covers(cal(19, 10, 0)))
    }

    @Test
    fun `incluye el minuto exacto de inicio`() {
        assertTrue(laboral.covers(cal(19, 8, 0)))
    }

    @Test
    fun `excluye el minuto exacto de fin`() {
        assertFalse(laboral.covers(cal(19, 22, 0)))
    }

    @Test
    fun `no bloquea antes del inicio`() {
        assertFalse(laboral.covers(cal(19, 7, 59)))
    }

    @Test
    fun `no bloquea en dia no seleccionado`() {
        assertFalse(laboral.covers(cal(24, 10, 0))) // sábado
    }

    @Test
    fun `horario deshabilitado no bloquea`() {
        assertFalse(laboral.copy(enabled = false).covers(cal(19, 10, 0)))
    }

    @Test
    fun `sin dias no bloquea`() {
        assertFalse(laboral.copy(days = emptySet()).covers(cal(19, 10, 0)))
    }

    @Test
    fun `horario nocturno bloquea la misma noche`() {
        val nocturno = laboral.copy(startMin = 22 * 60, endMin = 6 * 60, days = setOf(1))
        assertTrue(nocturno.covers(cal(19, 23, 30)))
    }

    @Test
    fun `horario nocturno bloquea a la madrugada del dia siguiente`() {
        val nocturno = laboral.copy(startMin = 22 * 60, endMin = 6 * 60, days = setOf(1))
        assertTrue(nocturno.covers(cal(20, 5, 0))) // martes 05:00, empezó el lunes
    }

    @Test
    fun `horario nocturno no bloquea despues de la manana siguiente`() {
        val nocturno = laboral.copy(startMin = 22 * 60, endMin = 6 * 60, days = setOf(1))
        assertFalse(nocturno.covers(cal(20, 7, 0)))
    }

    @Test
    fun `horario nocturno no arrastra desde un dia no seleccionado`() {
        val nocturno = laboral.copy(startMin = 22 * 60, endMin = 6 * 60, days = setOf(1))
        // Martes 23:00: el lunes solo cubre de 22:00 a 06:00, no a las 23:00
        assertFalse(nocturno.covers(cal(20, 23, 0)))
    }

    @Test
    fun `inicio igual a fin bloquea todo el dia`() {
        val todoDia = laboral.copy(startMin = 9 * 60, endMin = 9 * 60)
        assertTrue(todoDia.covers(cal(19, 0, 0)))
        assertTrue(todoDia.covers(cal(19, 23, 59)))
        assertFalse(todoDia.covers(cal(24, 12, 0))) // sábado no seleccionado
    }

    @Test
    fun `formatMinutes produce HH-mm`() {
        assertEquals("08:00", Schedule.formatMinutes(8 * 60))
        assertEquals("09:05", Schedule.formatMinutes(9 * 60 + 5))
        assertEquals("00:00", Schedule.formatMinutes(0))
        assertEquals("23:59", Schedule.formatMinutes(23 * 60 + 59))
    }

    @Test
    fun `daysLabel para todos los dias`() {
        assertEquals("Todos los días", laboral.copy(days = (1..7).toSet()).daysLabel())
    }

    @Test
    fun `daysLabel para lunes a viernes`() {
        assertEquals("Lunes a viernes", laboral.daysLabel())
    }

    @Test
    fun `daysLabel para fin de semana`() {
        assertEquals("Fin de semana", laboral.copy(days = setOf(6, 7)).daysLabel())
    }

    @Test
    fun `daysLabel lista dias sueltos`() {
        assertEquals("Lun, Mié", laboral.copy(days = setOf(1, 3)).daysLabel())
    }

    @Test
    fun `timeLabel muestra rango`() {
        assertEquals("08:00 - 22:00", laboral.timeLabel())
    }

    @Test
    fun `isoDayOfWeek mapea correctamente`() {
        assertEquals(1, Schedule.isoDayOfWeek(cal(19, 12, 0)))
        assertEquals(6, Schedule.isoDayOfWeek(cal(24, 12, 0)))
        assertEquals(7, Schedule.isoDayOfWeek(cal(25, 12, 0)))
    }
}
