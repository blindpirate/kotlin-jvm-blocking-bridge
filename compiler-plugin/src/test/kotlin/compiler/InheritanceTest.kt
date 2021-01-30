@file:Suppress("RemoveRedundantBackticks", "RedundantSuspendModifier", "MainFunctionReturnUnit")

package compiler

import assertHasFunction
import createInstance
import org.junit.jupiter.api.Test
import runFunction
import kotlin.coroutines.Continuation
import kotlin.test.assertFailsWith

internal sealed class InheritanceTest(
    ir: Boolean,
) : AbstractCompilerTest(ir) {

    internal class Ir : InheritanceTest(true)
    internal class Jvm : InheritanceTest(false)

    @Test
    fun `bridge for abstract`() = testJvmCompile(
        """
    abstract class Abstract {
        @JvmBlockingBridge
        abstract suspend fun test(): String
    }
    object TestData : Abstract() {
        override suspend fun test() = "OK"
        
        fun main(): String = TestData.runFunction("test")
    }
"""
    ) {
        assertFailsWith<NoSuchMethodException> {
            classLoader.loadClass("Abstract").run {
                assertHasFunction<String>("test")
            }
            classLoader.loadClass("TestData").getDeclaredMethod("test")
        }
    }

    @Test
    fun `bridge for overridden`() = testJvmCompile(
        """
    abstract class Abstract {
        abstract suspend fun test(): String
    }
    object TestData : Abstract() {
        @JvmBlockingBridge
        override suspend fun test() = "OK"
        
        fun main(): String = TestData.runFunction("test")
    }
"""
    ) {
        classLoader.loadClass("TestData").run {
            assertHasFunction<String>("test")
        }
    }

    @Test
    fun `bridge for interface overriding`() = testJvmCompile(
        """
    interface Interface3 {
        suspend fun test(): String
    }
    object TestData : Interface3 {
        @JvmBlockingBridge
        override suspend fun test() = "OK"
        
        fun main(): String = TestData.runFunction("test")
    }
"""
    ) {
        classLoader.loadClass("TestData").run {
            assertHasFunction<String>("test")
            createInstance().run {
                runFunction<String>("test")
            }
        }
    }

    @Test
    open fun `bridge for interface inheritance`() = testJvmCompile(
        """
    interface Interface2 {
        @JvmBlockingBridge
        suspend fun test(): String
    }
    object TestData : Interface2 {
        override suspend fun test() = "OK"
        
        fun main(): String = TestData.runFunction("test")
    }
"""
    ) {
        classLoader.loadClass("Interface2").run {
            assertHasFunction<String>("test")
            assertHasFunction<Any>("test", Continuation::class.java)
        }
        classLoader.loadClass("TestData").run {
            assertHasFunction<String>("test")
            assertHasFunction<Any>("test", Continuation::class.java)
        }
        assertFailsWith<NoSuchMethodException> {
            classLoader.loadClass("TestData").getDeclaredMethod("test")
        }
    }

    @Test
    fun `interface codegen`() = testJvmCompile(
        """
    interface Interface {
        @JvmBlockingBridge
        suspend fun test(): String
    }
""", noMain = true
    ) {
        classLoader.loadClass("Interface").run {
            assertHasFunction<String>("test")
        }
    }
}