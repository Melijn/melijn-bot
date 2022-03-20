package me.melijn.annotationprocessors.util

import com.google.devtools.ksp.symbol.KSClassDeclaration

object Reflections {

    fun getCode(clazz: KSClassDeclaration): String {
        val field = clazz.javaClass.getDeclaredField("descriptor\$delegate")
        field.isAccessible = true
        val lazyDescriptor = field.get(clazz)
        val lazyValueMethod = Lazy::class.java.getMethod("getValue")

        val lazyValue = lazyValueMethod.invoke(lazyDescriptor)
        val declarationProvider = lazyValue.javaClass.getDeclaredField("declarationProvider")
        declarationProvider.isAccessible = true
        return declarationProvider.get(lazyValue).toString()
    }

}