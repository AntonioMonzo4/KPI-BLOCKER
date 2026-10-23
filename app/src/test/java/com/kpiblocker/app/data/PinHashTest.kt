package com.kpiblocker.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHashTest {

    @Test
    fun `hash es determinista`() {
        assertEquals(Prefs.hashPin("1234"), Prefs.hashPin("1234"))
    }

    @Test
    fun `hash es hexadecimal de 64 caracteres`() {
        val hash = Prefs.hashPin("4321")
        assertEquals(64, hash.length)
        assertTrue(hash.all { it in "0123456789abcdef" })
    }

    @Test
    fun `pines distintos producen hashes distintos`() {
        assertNotEquals(Prefs.hashPin("1234"), Prefs.hashPin("1235"))
    }

    @Test
    fun `pin vacio tambien tiene hash valido`() {
        assertEquals(64, Prefs.hashPin("").length)
    }
}
