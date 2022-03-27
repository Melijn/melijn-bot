package snippet

abstract class AbstractSnippet<T> {

    abstract val name: String
    abstract val src: String
    open suspend fun render(prop: T): String = src
}