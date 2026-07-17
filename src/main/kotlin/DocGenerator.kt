package snkt.org

fun createHtmlDoc(template: String, params: Map<String, String>): String {
    var _template = template
    params.entries.forEach { e ->
        _template = _template.replace("{{${e.key}}}", e.value)
    }
    return _template
}