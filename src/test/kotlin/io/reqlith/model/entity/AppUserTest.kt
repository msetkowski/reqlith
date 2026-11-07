package io.reqlith.model.entity

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AppUserTest {
    @Test
    fun `initials use first and last name`() {
        assertEquals("OO", AppUser.initialsOf("Olive Owens"))
        assertEquals("AK", AppUser.initialsOf("  anna  maria   kowalska "))
    }

    @Test
    fun `single name uses its first two letters`() {
        assertEquals("OL", AppUser.initialsOf("olive"))
        assertEquals("?", AppUser.initialsOf("   "))
    }
}
