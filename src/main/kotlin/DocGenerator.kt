package snkt.org

fun createHtmlDoc(template: String, params: Map<String, Any>): String {
    var _template = template
    params.entries.forEach { e ->
        _template = _template.replace("{{${e.key}}}", e.value.toString())
    }
    return _template
}