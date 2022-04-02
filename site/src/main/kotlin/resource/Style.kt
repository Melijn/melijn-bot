package resource

import io.ktor.http.*
import me.melijn.siteannotationprocessors.page.Page
import model.AbstractPage
import org.intellij.lang.annotations.Language

@Page
class Style : AbstractPage("/style.css", ContentType.Text.CSS) {

    @Language("css")
    override val src: String = """
        body {
            margin: 40px auto;
            max-width: 650px;
            line-height: 1.6;
            font-size: 18px;
            color: #444;
            padding: 0 10px;
        }
        
        h1, h2, h3 {
            line-height:1.2
        }
        
        .navbar {
            display: flex;
            flex-direction: row;
            justify-content: right;
        }
        
        .navbar > div {
            display: flex;
            margin-right: 8px;
        }
        
        .footer {
            display: flex;
            flex-direction: row;
            justify-content: space-between;
        }
        
        .navbar > div {
            display: flex;
        }
        
        .cmd {
            margin-bottom: 10px;
            border-bottom: 1px solid grey;
            border-top: 1px solid grey;
        }
        
        table {
            border-collapse: collapse;
            border-spacing: 0;
        }
        
        td.type {
            text-align: right;
            font-weight: bold;
            padding-bottom: 7px;
        }
        
        td {
            vertical-align: top;
            padding-right: 10px;
        }
    """
}