package model

import snippet.AbstractSnippet

interface SnippetsInterface {
    val snippets: List<AbstractSnippet<Any>>
}