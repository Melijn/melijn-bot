package snippet

abstract class AbstractSnippet<T> {

    abstract val src: String
    open suspend fun render(prop: T): String = src
}